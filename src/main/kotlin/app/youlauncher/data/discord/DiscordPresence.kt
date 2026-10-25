package app.youlauncher.data.discord

import app.youlauncher.core.BuildInfo
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.core.repo.LaunchService
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.settings.DiscordSettings
import app.youlauncher.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Где пользователь в лаунчере: это видно в Discord, пока не идёт игра. */
sealed interface PresenceScreen {
    data object Home : PresenceScreen
    data object Library : PresenceScreen
    data class Instance(val id: String) : PresenceScreen
    data class Mods(val instanceId: String?) : PresenceScreen
    data object Settings : PresenceScreen
    data object Other : PresenceScreen
}

/**
 * Активность в профиле Discord: что открыто в лаунчере и во что идёт игра (название сборки, загрузчик,
 * время в игре). Discord не запущен — лаунчер тихо ждёт и подключается, когда он появится.
 */
class DiscordPresence(
    private val scope: CoroutineScope,
    private val appId: String,
    private val settings: StateFlow<DiscordSettings>,
    private val launcher: LaunchService,
    private val instances: InstanceRepository,
) {
    val screen = MutableStateFlow<PresenceScreen>(PresenceScreen.Home)
    private val _connected = MutableStateFlow(false)
    /** Есть связь с Discord: для подписи в настройках. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val desired = MutableStateFlow<JsonObject?>(null)
    private val poke = Channel<Unit>(Channel.CONFLATED)
    private val startedAt = System.currentTimeMillis()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            combine(settings, screen, launcher.states, instances.instances) { s, sc, st, list -> PresenceBuilder.Input(s, sc, st, list, startedAt) }
                .distinctUntilChanged()
                .collectLatest { input ->
                    desired.value = if (input.settings.enabled) PresenceBuilder.build(input) { r, args -> getString(r, *args) } else null
                    poke.trySend(Unit)
                }
        }
        scope.launch { settings.map { it.enabled }.distinctUntilChanged().collect { poke.trySend(Unit) } }
        // Весь обмен с Discord в одном потоке: чтение ответа блокирует, а кадры нельзя перемешивать.
        scope.launch(Dispatchers.IO.limitedParallelism(1)) { sendLoop() }
    }

    private suspend fun sendLoop() {
        var ipc: DiscordIpc? = null
        var sent: JsonObject? = null
        var synced = false
        var lastSend = 0L
        while (scope.isActive) {
            if (!settings.value.enabled) {
                ipc?.close(); ipc = null; synced = false; _connected.value = false
                poke.receive()
                continue
            }
            if (ipc == null) {
                ipc = runCatching { DiscordIpc.connect(appId) }.getOrNull()
                _connected.value = ipc != null
                synced = false
                if (ipc == null) { withTimeoutOrNull(RECONNECT_MS) { poke.receive() }; continue }
            }
            val want = desired.value
            if (!synced || want != sent) {
                // Discord принимает не больше пяти обновлений за 20 секунд: частые смены экранов склеиваются.
                val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastSend)
                if (wait > 0) delay(wait)
                val now = desired.value
                val result = runCatching { ipc.setActivity(now) }
                lastSend = System.currentTimeMillis()
                if (result.isSuccess) { sent = now; synced = true }
                else { ipc.close(); ipc = null; _connected.value = false }
                continue
            }
            // Ничего не изменилось: ждём перемен; раз в минуту сверка, что Discord ещё на связи.
            if (withTimeoutOrNull(KEEPALIVE_MS) { poke.receive() } == null) synced = false
        }
        ipc?.close()
    }

    companion object {
        const val MIN_INTERVAL_MS = 4_000L
        const val RECONNECT_MS = 20_000L
        const val KEEPALIVE_MS = 60_000L
    }
}

/** Текст и картинки активности; без Discord и ресурсов, чтобы проверять тестами. */
object PresenceBuilder {
    const val PAGE = "https://github.com/nurudinsaidov98-commits/YouLauncher"

    data class Input(
        val settings: DiscordSettings,
        val screen: PresenceScreen,
        val states: Map<String, LaunchState>,
        val instances: List<Instance>,
        val launcherStartedAt: Long,
    )

    /** Ключи картинок, загруженных в приложение Discord (Rich Presence → Art Assets). */
    object Assets {
        const val LOGO = "logo"
        const val PLAY = "play"
        const val MODS = "mods"
        const val LIBRARY = "library"
        const val DOWNLOAD = "download"
    }

    suspend fun build(input: Input, text: suspend (StringResource, Array<Any>) -> String): JsonObject {
        val names = input.settings.showNames
        fun inst(id: String?) = id?.let { i -> input.instances.firstOrNull { it.id == i } }
        suspend fun t(r: StringResource, vararg args: Any) = text(r, arrayOf(*args))
        suspend fun versionLine(i: Instance) =
            if (i.loader == LoaderType.Vanilla) t(Res.string.discord_state_vanilla, i.gameVersion)
            else t(Res.string.discord_state_loader, i.loader.title, i.gameVersion)

        // Игра важнее экрана: пока Minecraft запущен, в профиле он.
        val running = input.states.entries.firstOrNull { it.value is LaunchState.Running }
        val preparing = input.states.entries.firstOrNull { it.value is LaunchState.Preparing }
        var details: String
        var state: String? = null
        var start = input.launcherStartedAt
        var small = Assets.LIBRARY
        var smallText: String? = null
        when {
            running != null -> {
                val i = inst(running.key)
                start = (running.value as LaunchState.Running).startedAt
                small = Assets.PLAY
                details = when {
                    i == null -> t(Res.string.discord_playing_vanilla, "")
                    names && i.isModded -> t(Res.string.discord_playing, i.name)
                    else -> t(Res.string.discord_playing_vanilla, i.gameVersion)
                }
                state = i?.let { versionLine(it) }
                smallText = i?.loader?.title
            }
            preparing != null -> {
                val i = inst(preparing.key)
                small = Assets.DOWNLOAD
                details = if (names && i != null) t(Res.string.discord_preparing, i.name) else t(Res.string.discord_preparing_hidden)
                state = i?.let { versionLine(it) }
            }
            else -> when (val s = input.screen) {
                PresenceScreen.Home -> details = t(Res.string.discord_home)
                PresenceScreen.Library -> {
                    details = t(Res.string.discord_library)
                    state = t(Res.string.discord_library_count, input.instances.size)
                }
                is PresenceScreen.Instance -> {
                    val i = inst(s.id)
                    details = if (names && i != null) t(Res.string.discord_instance, i.name) else t(Res.string.discord_instance_hidden)
                    state = i?.let { versionLine(it) }
                }
                is PresenceScreen.Mods -> {
                    small = Assets.MODS
                    details = t(Res.string.discord_mods)
                    state = inst(s.instanceId)?.takeIf { names }?.let { t(Res.string.discord_mods_for, it.name) }
                }
                PresenceScreen.Settings -> details = t(Res.string.discord_settings)
                PresenceScreen.Other -> details = t(Res.string.discord_launcher)
            }
        }
        val large = t(Res.string.discord_large_text, BuildInfo.version)
        val button = t(Res.string.discord_button)
        return buildJsonObject {
            put("type", 0)
            put("details", fit(details, 128))
            state?.let { put("state", fit(it, 128)) }
            putJsonObject("timestamps") { put("start", start / 1000) }
            putJsonObject("assets") {
                put("large_image", Assets.LOGO)
                put("large_text", fit(large, 128))
                put("small_image", small)
                put("small_text", fit(smallText ?: details, 128))
            }
            put("buttons", buildJsonArray { add(buildJsonObject { put("label", fit(button, 32)); put("url", PAGE) }) })
        }
    }

    /** Discord отклоняет слишком длинные строки и строки короче двух символов. */
    fun fit(s: String, max: Int): String {
        val t = s.trim()
        val cut = if (t.length > max) t.take(max - 1).trimEnd() + "…" else t
        return cut.padEnd(2, ' ')
    }
}
