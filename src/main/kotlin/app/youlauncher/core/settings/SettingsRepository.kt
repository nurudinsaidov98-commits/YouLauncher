package app.youlauncher.core.settings

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.writeJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Настройки как StateFlow: изменения применяются сразу и сохраняются с небольшой задержкой. */
class SettingsRepository(
    private val file: Path,
    scope: CoroutineScope,
) {
    private val state = MutableStateFlow(load())
    val settings: StateFlow<LauncherSettings> = state.asStateFlow()

    @Volatile
    private var lastSaved: LauncherSettings = state.value

    init {
        @OptIn(FlowPreview::class)
        state.debounce(300).onEach { if (it != lastSaved) save(it) }.launchIn(scope)
    }

    fun update(transform: (LauncherSettings) -> LauncherSettings) = state.update(transform)

    fun replace(settings: LauncherSettings) = state.update { migrate(settings) }

    fun saveNow() = save(state.value)

    @Synchronized
    private fun save(value: LauncherSettings) {
        runCatching { file.writeJson(LauncherSettings.serializer(), value) }.onSuccess { lastSaved = value }
    }

    private fun load(): LauncherSettings {
        if (!file.exists()) return LauncherSettings()
        return decode(file.readText()) ?: file.readJsonOrNull(LauncherSettings.serializer()) ?: LauncherSettings()
    }

    companion object {
        fun migrate(settings: LauncherSettings) = settings.copy(schema = LauncherSettings.CURRENT_SCHEMA)

        /** Чтение настроек из текста с миграциями схемы (файл настроек и импорт). null, если это не настройки YouLauncher. */
        fun decode(text: String): LauncherSettings? {
            val element = runCatching { YouLauncherJson.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return null
            val schema = runCatching { element["schema"]?.jsonPrimitive?.int }.getOrNull() ?: 0
            return runCatching { YouLauncherJson.decodeFromJsonElement<LauncherSettings>(SettingsMigrations.migrate(element, schema)) }.getOrNull()
        }

        /** Настройки для экспорта: без машинно-зависимых путей (свои обои, Java, папки поиска). */
        fun exportText(settings: LauncherSettings): String {
            val portable = settings.copy(
                java = settings.java.copy(globalJavaPath = null, extraSearchPaths = emptyList()),
                background = settings.background.copy(
                    wallpaper = settings.background.wallpaper?.takeUnless { it.startsWith("file:") },
                    userWallpaperFolder = null,
                ),
                lastInstanceId = null,
                activeAccountId = null,
            )
            return YouLauncherJson.encodeToString(LauncherSettings.serializer(), portable)
        }
    }
}

/** Миграции формата settings.json между версиями схемы. */
object SettingsMigrations {
    fun migrate(json: JsonObject, fromSchema: Int): JsonObject {
        var current = json
        if (fromSchema < 1) {
            // Схема 0 не существовала в релизах: просто проставляем номер.
            current = JsonObject(current + ("schema" to kotlinx.serialization.json.JsonPrimitive(1)))
        }
        return current
    }
}
