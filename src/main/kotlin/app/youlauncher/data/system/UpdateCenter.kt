package app.youlauncher.data.system

import app.youlauncher.core.AppDirs
import app.youlauncher.core.settings.UpdateChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val zip: Path) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Обновления на всё приложение: результат проверки живёт здесь, а не на экране настроек. Поэтому найденное
 * при запуске обновление сразу ждёт в «О программе», а уведомление может вести прямо к нему.
 */
class UpdateCenter(
    private val updater: Updater,
    private val downloadProgress: StateFlow<Float>,
    private val scope: CoroutineScope,
    private val currentVersion: String,
    /** Закрыть лаунчер после запуска сценария замены файлов. */
    private val exit: () -> Unit,
    /** Открыть страницу релиза: запуск из среды разработки, где заменять нечего. */
    private val openPage: (String) -> Unit,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    val configured: Boolean get() = updater.configured

    /** Проверка: возвращает найденное обновление, чтобы при запуске показать уведомление. */
    suspend fun check(channel: UpdateChannel): UpdateInfo? {
        _state.value = UpdateState.Checking
        return runCatching { updater.check(channel, currentVersion) }
            .fold(
                { info -> _state.value = info?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate; info },
                { e -> _state.value = UpdateState.Failed(e.message ?: ""); null },
            )
    }

    fun download(info: UpdateInfo) = scope.launch {
        _state.value = UpdateState.Downloading(info, 0f)
        val watcher = launch { downloadProgress.collectLatest { p -> _state.value = UpdateState.Downloading(info, p) } }
        _state.value = runCatching { updater.download(info) }.fold({ UpdateState.Ready(info, it) }, { UpdateState.Failed(it.message ?: "") })
        watcher.cancel()
    }

    /** Портативная или установленная сборка обновляется на месте; запуск из среды разработки открывает страницу релиза. */
    fun install(ready: UpdateState.Ready) = scope.launch {
        val appDir = AppDirs.appDirectory()?.takeIf { Files.exists(it.resolve("YouLauncher.exe")) }
        if (appDir == null) {
            openPage(ready.info.pageUrl)
            return@launch
        }
        runCatching { updater.install(ready.zip, appDir) }
            .onSuccess { exit() }
            .onFailure { _state.value = UpdateState.Failed(it.message ?: "") }
    }
}
