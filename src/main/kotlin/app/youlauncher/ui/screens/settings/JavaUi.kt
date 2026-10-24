package app.youlauncher.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.AppGraph
import app.youlauncher.data.java.JavaCheck
import app.youlauncher.data.java.JavaInstall
import app.youlauncher.data.java.JavaSource
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.ViewModel
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

/** Список Java, проверка запуском и загрузка Temurin. */
class JavaViewModel(
    private val graph: AppGraph,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel(dispatcher) {
    private val _installs = MutableStateFlow<List<JavaInstall>?>(null)
    val installs: StateFlow<List<JavaInstall>?> = _installs.asStateFlow()
    private val _checks = MutableStateFlow<Map<Path, JavaCheck?>>(emptyMap())
    /** Результат проверки по пути; null в карте: проверка идёт. */
    val checks: StateFlow<Map<Path, JavaCheck?>> = _checks.asStateFlow()
    private val _downloads = MutableStateFlow<Map<Int, Float?>>(emptyMap())
    val downloads: StateFlow<Map<Int, Float?>> = _downloads.asStateFlow()

    init { refresh() }

    fun refresh() = scope.launch {
        _installs.value = runCatching { graph.java.detect(graph.settings.settings.value.java.extraSearchPaths) }.getOrDefault(emptyList())
    }

    fun verify(install: JavaInstall) {
        if (_checks.value.containsKey(install.executable) && _checks.value[install.executable] == null) return
        _checks.update { it + (install.executable to null) }
        scope.launch {
            val result = graph.java.verify(install)
            _checks.update { it + (install.executable to result) }
        }
    }

    fun download(major: Int) {
        if (major in _downloads.value) return
        _downloads.update { it + (major to null) }
        scope.launch {
            runCatching { graph.adoptium.install(major) { p -> _downloads.update { it + (major to p) } } }
                .onSuccess { graph.messenger.show(Res.string.java_downloaded, it.version) }
                .onFailure { graph.messenger.show(Res.string.java_download_failed, major, it.message ?: "", error = true) }
            _downloads.update { it - major }
            refresh()
        }
    }
}

/** Названия сборок Java (имена продуктов не переводятся), системная и своя из ресурсов. */
@Composable
private fun JavaSource.label(): String = when (this) {
    JavaSource.Mojang -> "Mojang"
    JavaSource.Temurin -> "Temurin"
    JavaSource.Zulu -> "Zulu"
    JavaSource.Microsoft -> "Microsoft"
    JavaSource.Corretto -> "Corretto"
    JavaSource.Liberica -> "Liberica"
    JavaSource.Oracle -> "Oracle"
    JavaSource.System -> stringResource(Res.string.java_src_system)
    JavaSource.Custom -> stringResource(Res.string.java_src_custom)
}

/**
 * Выбор Java: «Автоматически» (подбор под версию игры), найденные установки или свой файл.
 * Рядом кнопка проверки запуском. [path] null означает автоподбор.
 */
@Composable
fun JavaPicker(path: String?, onPick: (String?) -> Unit, autoLabel: String) {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { JavaViewModel(graph) }
    val installs by vm.installs.collectAsState()
    val checks by vm.checks.collectAsState()
    var open by remember { mutableStateOf(false) }
    val chooseTitle = stringResource(Res.string.settings_java_choose)
    val selected = installs?.firstOrNull { it.executable.toString() == path } ?: path?.let { graph.java.inspect(Path.of(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
                    Icon(Symbols.Coffee, null, Modifier.size(18.dp))
                    Text(
                        when {
                            path == null -> autoLabel
                            selected != null -> stringResource(Res.string.java_item, selected.version, selected.source.label())
                            else -> path
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Icon(Symbols.ExpandMore, null, Modifier.size(18.dp))
                }
                DropdownMenu(open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
                    DropdownMenuItem(
                        text = { Text(autoLabel) },
                        leadingIcon = { Icon(Symbols.AutoAwesome, null) },
                        trailingIcon = if (path == null) ({ Icon(Symbols.Check, null) }) else null,
                        onClick = { onPick(null); open = false },
                    )
                    HorizontalDivider()
                    installs.orEmpty().forEach { j ->
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.java_item, j.version, j.source.label())) },
                            leadingIcon = { Icon(Symbols.Coffee, null) },
                            trailingIcon = if (j.executable.toString() == path) ({ Icon(Symbols.Check, null) }) else null,
                            onClick = { onPick(j.executable.toString()); open = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(chooseTitle) },
                        leadingIcon = { Icon(Symbols.FolderOpen, null) },
                        onClick = {
                            open = false
                            Platform.chooseFiles(chooseTitle, listOf("exe")).firstOrNull()?.let { onPick(it.toString()) }
                        },
                    )
                }
            }
            if (selected != null) CheckButton(selected, checks, vm::verify)
        }
        selected?.let { CheckResult(checks, it) }
    }
}

@Composable
private fun CheckButton(install: JavaInstall, checks: Map<Path, JavaCheck?>, onCheck: (JavaInstall) -> Unit) {
    val running = checks.containsKey(install.executable) && checks[install.executable] == null
    if (running) LoadingIndicator(Modifier.size(36.dp))
    else TextButton(onClick = { onCheck(install) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Verified, stringResource(Res.string.java_check)) }
}

@Composable
private fun CheckResult(checks: Map<Path, JavaCheck?>, install: JavaInstall) {
    val result = checks[install.executable] ?: return
    val motion = MaterialTheme.motionScheme
    AnimatedContent(result, transitionSpec = { (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec())) togetherWith fadeOut(motion.fastEffectsSpec()) }) { r ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.ok) Symbols.CheckCircleFilled else Symbols.ErrorFilled, null, Modifier.size(18.dp), tint = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(
                if (r.ok) stringResource(Res.string.java_check_ok, r.output) else stringResource(Res.string.java_check_fail, r.output),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Общий раздел Java: выбор по умолчанию, список найденных, загрузка Temurin одной кнопкой. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JavaManagerSection(globalPath: String?, onGlobal: (String?) -> Unit) {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { JavaViewModel(graph) }
    val installs by vm.installs.collectAsState()
    val checks by vm.checks.collectAsState()
    val downloads by vm.downloads.collectAsState()
    SettingLabel(stringResource(Res.string.settings_java_global))
    JavaPicker(globalPath, onGlobal, stringResource(Res.string.java_auto_global))

    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(stringResource(Res.string.java_found), Modifier.weight(1f))
        TIconButton(Symbols.Refresh, stringResource(Res.string.java_refresh), { vm.refresh() })
    }
    val list = installs
    when {
        list == null -> LoadingIndicator(Modifier.size(40.dp))
        list.isEmpty() -> Text(stringResource(Res.string.java_none_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { j ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("${j.major}", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(Res.string.java_item, j.version, j.source.label()), style = MaterialTheme.typography.titleSmallEmphasized)
                                Text(j.home.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            CheckButton(j, checks, vm::verify)
                            TIconButton(Symbols.FolderOpen, stringResource(Res.string.settings_open_data), { Platform.openFolder(j.home) })
                        }
                        CheckResult(checks, j)
                    }
                }
            }
        }
    }

    SettingLabel(stringResource(Res.string.java_download_title))
    Text(stringResource(Res.string.java_download_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        app.youlauncher.data.java.AdoptiumInstaller.GAME_MAJORS.forEach { major ->
            val progress = downloads[major]
            val busy = major in downloads
            FilledTonalButton(onClick = { vm.download(major) }, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                if (busy) {
                    if (progress == null) LoadingIndicator(Modifier.size(20.dp))
                    else CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(20.dp))
                } else Icon(Symbols.Download, null, Modifier.size(18.dp))
                Text(stringResource(Res.string.java_download_major, major), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
