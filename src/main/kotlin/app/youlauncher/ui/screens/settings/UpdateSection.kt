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
import app.youlauncher.core.BuildInfo
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.UpdateChannel
import app.youlauncher.data.system.UpdateState
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.MarkdownText
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.icons.Symbols
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Раздел обновлений: канал, проверка, загрузка с проверкой хеша и перезапуск. */
@Composable
fun UpdateSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    // Состояние общее на приложение (UpdateCenter): найденное при запуске обновление уже здесь.
    val updates = graph.updates
    val state by updates.state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
                    OutlinedButton(onClick = { scope.launch { updates.check(s.system.updateChannel) } }, enabled = s.system.updateChannel != UpdateChannel.Off, shapes = ButtonDefaults.shapes()) {
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
                    Button(onClick = { updates.download(st.info) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Download, stringResource(Res.string.update_download)) }
                }
                is UpdateState.Downloading -> {
                    Text(stringResource(Res.string.update_downloading, st.info.version))
                    LinearWavyProgressIndicator(progress = { st.progress })
                }
                is UpdateState.Ready -> {
                    Text(stringResource(Res.string.update_ready, st.info.version), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { updates.install(st) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.RestartAlt, stringResource(Res.string.update_install)) }
                }
            }
        }
    }
}
