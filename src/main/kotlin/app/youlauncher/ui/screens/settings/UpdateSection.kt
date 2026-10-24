package app.youlauncher.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.core.BuildInfo
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.UpdateChannel
import app.youlauncher.data.system.UpdateInfo
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.ViewModel
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.MarkdownText
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
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

class UpdateViewModel(private val graph: AppGraph, dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate) : ViewModel(dispatcher) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check() = scope.launch {
        _state.value = UpdateState.Checking
        _state.value = runCatching { graph.updater.check(graph.settings.settings.value.system.updateChannel, BuildInfo.version) }
            .fold({ it?.let { i -> UpdateState.Available(i) } ?: UpdateState.UpToDate }, { UpdateState.Failed(it.message ?: "") })
    }

    fun download(info: UpdateInfo) = scope.launch {
        _state.value = UpdateState.Downloading(info, 0f)
        val watcher = launch { graph.updaterProgress.collectLatest { p -> _state.value = UpdateState.Downloading(info, p) } }
        _state.value = runCatching { graph.updater.download(info) }.fold({ UpdateState.Ready(info, it) }, { UpdateState.Failed(it.message ?: "") })
        watcher.cancel()
    }

    /** Портативная сборка обновляется на месте; запуск из среды разработки открывает страницу релиза. */
    fun install(ready: UpdateState.Ready) = scope.launch {
        val appDir = AppDirs.appDirectory()?.takeIf { Files.exists(it.resolve("YouLauncher.exe")) }
        if (appDir == null) {
            Platform.openUrl(ready.info.pageUrl)
            return@launch
        }
        runCatching { graph.updater.install(ready.zip, appDir) }
            .onSuccess { graph.shutdown(); kotlin.system.exitProcess(0) }
            .onFailure { _state.value = UpdateState.Failed(it.message ?: "") }
    }
}

/** Раздел обновлений: канал, проверка, загрузка с проверкой хеша и перезапуск. */
@Composable
fun UpdateSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { UpdateViewModel(graph) }
    val state by vm.state.collectAsState()
    SettingLabel(stringResource(Res.string.update_title))
    if (!graph.updater.configured) {
        Text(stringResource(Res.string.update_not_configured), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    ConnectedChoice(
        options = listOf(
            ChoiceOption(UpdateChannel.Stable, stringResource(Res.string.update_stable)),
            ChoiceOption(UpdateChannel.Beta, stringResource(Res.string.update_beta)),
            ChoiceOption(UpdateChannel.Off, stringResource(Res.string.update_off)),
        ),
        selected = s.system.updateChannel,
        onSelect = { c -> update { it.copy(system = it.system.copy(updateChannel = c)) } },
        showIcons = false,
    )
    val motion = MaterialTheme.motionScheme
    AnimatedContent(state, transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) }, contentKey = { it::class }) { st ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (st) {
                UpdateState.Idle, is UpdateState.Failed, UpdateState.UpToDate -> {
                    if (st is UpdateState.Failed) Text(stringResource(Res.string.update_failed, st.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (st == UpdateState.UpToDate) Text(stringResource(Res.string.update_latest, BuildInfo.version), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { vm.check() }, enabled = s.system.updateChannel != UpdateChannel.Off, shapes = ButtonDefaults.shapes()) {
                        IconLabel(Symbols.Update, stringResource(Res.string.update_check))
                    }
                }
                UpdateState.Checking -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(Res.string.update_checking))
                }
                is UpdateState.Available -> {
                    Text(stringResource(Res.string.update_available, st.info.version), style = MaterialTheme.typography.titleMedium)
                    if (st.info.notes.isNotBlank()) MarkdownText(st.info.notes, Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()))
                    Button(onClick = { vm.download(st.info) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Download, stringResource(Res.string.update_download)) }
                }
                is UpdateState.Downloading -> {
                    Text(stringResource(Res.string.update_downloading, st.info.version))
                    LinearWavyProgressIndicator(progress = { st.progress })
                }
                is UpdateState.Ready -> {
                    Text(stringResource(Res.string.update_ready, st.info.version), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.install(st) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.RestartAlt, stringResource(Res.string.update_install)) }
                }
            }
        }
    }
}
