package app.youlauncher.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.DefaultHotkeys
import app.youlauncher.core.settings.HomeWidget
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.SettingsRepository
import app.youlauncher.core.settings.WindowMode
import app.youlauncher.resources.*
import app.youlauncher.ui.Hotkey
import app.youlauncher.ui.UiSound
import app.youlauncher.ui.UiSounds
import app.youlauncher.ui.components.SwitchRow
import kotlinx.coroutines.launch
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.window.LocalWindowController
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Files

/** Логика редакторов интерфейса без Compose (проверяется тестами). */
object InterfaceLogic {
    /** Назначение сочетания: если оно уже занято другим действием, у того оно снимается. */
    fun assignHotkey(map: Map<String, String>, action: String, combo: String): Map<String, String> =
        map.filterValues { !it.equals(combo, ignoreCase = true) } + (action to combo)

    fun conflict(map: Map<String, String>, action: String, combo: String): String? =
        map.entries.firstOrNull { it.key != action && it.value.equals(combo, ignoreCase = true) }?.key

    fun toggleWidget(list: List<HomeWidget>, widget: HomeWidget, on: Boolean): List<HomeWidget> =
        if (on) (list - widget) + widget else list - widget

    fun moveWidget(list: List<HomeWidget>, widget: HomeWidget, delta: Int): List<HomeWidget> {
        val i = list.indexOf(widget)
        val j = i + delta
        if (i < 0 || j !in list.indices) return list
        return list.toMutableList().apply { removeAt(i); add(j, widget) }
    }
}

/** Режим окна: обычное, компактное и мини-окно; размер меняется сразу. */
@Composable
fun WindowModeChoice(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val controller = LocalWindowController.current
    SettingLabel(stringResource(Res.string.settings_window_mode))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(WindowMode.Normal, stringResource(Res.string.window_normal), Symbols.DesktopWindows),
            ChoiceOption(WindowMode.Compact, stringResource(Res.string.window_compact), Symbols.PictureInPictureAlt),
            ChoiceOption(WindowMode.Mini, stringResource(Res.string.window_mini), Symbols.Minimize),
        ),
        selected = s.ui.windowMode,
        onSelect = { m ->
            update { it.copy(ui = it.ui.copy(windowMode = m)) }
            controller.applyMode(m)
        },
    )
}

private fun HomeWidget.label(): StringResource = when (this) {
    HomeWidget.ContinueCard -> Res.string.widget_continue
    HomeWidget.RecentInstances -> Res.string.widget_recent
    HomeWidget.LastSession -> Res.string.widget_last_session
    HomeWidget.Stats -> Res.string.widget_stats
    HomeWidget.Shortcuts -> Res.string.widget_shortcuts
}

/** Виджеты главной: включение и порядок кнопками вверх и вниз. */
@Composable
fun HomeWidgetsEditor(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val list = s.ui.homeWidgets
    val ordered = list + HomeWidget.entries.filterNot { it in list }
    SettingLabel(stringResource(Res.string.settings_home_widgets))
    Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ordered.forEach { w ->
            val on = w in list
            val container by animateColorAsState(
                if (on) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
            )
            Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(w.label()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (on) {
                        TIconButton(Symbols.ExpandLess, stringResource(Res.string.widget_up), { update { it.copy(ui = it.ui.copy(homeWidgets = InterfaceLogic.moveWidget(list, w, -1))) } })
                        TIconButton(Symbols.ExpandMore, stringResource(Res.string.widget_down), { update { it.copy(ui = it.ui.copy(homeWidgets = InterfaceLogic.moveWidget(list, w, 1))) } })
                    }
                    Switch(on, { v -> update { it.copy(ui = it.ui.copy(homeWidgets = InterfaceLogic.toggleWidget(list, w, v))) } }, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

private val HotkeyActions = listOf(
    "palette" to Res.string.hotkey_palette,
    "home" to Res.string.nav_home,
    "library" to Res.string.nav_library,
    "mods" to Res.string.nav_mods,
    "accounts" to Res.string.nav_accounts,
    "console" to Res.string.nav_console,
    "settings" to Res.string.nav_settings,
    "play" to Res.string.action_play,
    "newInstance" to Res.string.cmd_new_instance,
)

/** Горячие клавиши: запись нового сочетания нажатием, снятие конфликтов, сброс к стандартным. */
@Composable
fun HotkeysEditor(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    var recording by remember { mutableStateOf<Pair<String, StringResource>?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingLabel(stringResource(Res.string.settings_hotkeys), Modifier.weight(1f))
        TextButton(onClick = { update { it.copy(ui = it.ui.copy(hotkeys = DefaultHotkeys)) } }, shapes = ButtonDefaults.shapes()) {
            IconLabel(Symbols.RestartAlt, stringResource(Res.string.hotkeys_reset))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HotkeyActions.forEach { (action, label) ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val combo = s.ui.hotkeys[action]
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(combo ?: stringResource(Res.string.hotkey_none), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    TextButton(onClick = { recording = action to label }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.hotkey_change)) }
                }
            }
        }
    }
    recording?.let { (action, label) ->
        HotkeyRecorder(
            title = stringResource(label),
            current = s.ui.hotkeys,
            action = action,
            onSave = { combo -> update { it.copy(ui = it.ui.copy(hotkeys = InterfaceLogic.assignHotkey(it.ui.hotkeys, action, combo))) }; recording = null },
            onClear = { update { it.copy(ui = it.ui.copy(hotkeys = it.ui.hotkeys - action)) }; recording = null },
            onDismiss = { recording = null },
        )
    }
}

@Composable
private fun HotkeyRecorder(title: String, current: Map<String, String>, action: String, onSave: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val ui = LocalAppUi.current
    var combo by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    DisposableEffect(Unit) {
        ui.recordingHotkey = true
        onDispose { ui.recordingHotkey = false }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val conflict = combo?.let { InterfaceLogic.conflict(current, action, it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Keyboard, null) },
        title = { Text(stringResource(Res.string.hotkey_record_title, title)) },
        text = {
            Column(Modifier.widthIn(min = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.hotkey_record_body), style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                        .focusRequester(focus)
                        .focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val name = Hotkey.keyName(e.key.nativeKeyCode) ?: return@onPreviewKeyEvent true
                            // Без модификатора сочетание мешало бы вводу текста, кроме F1–F12.
                            if (!e.isCtrlPressed && !e.isAltPressed && !name.matches(Regex("F\\d{1,2}"))) return@onPreviewKeyEvent true
                            combo = Hotkey(e.isCtrlPressed, e.isShiftPressed, e.isAltPressed, e.key.nativeKeyCode, name).toString()
                            true
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(combo ?: stringResource(Res.string.hotkey_press), style = MaterialTheme.typography.headlineSmallEmphasized)
                    }
                }
                conflict?.let {
                    val other = HotkeyActions.firstOrNull { a -> a.first == it }?.second
                    if (other != null) Text(stringResource(Res.string.hotkey_conflict, stringResource(other)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { combo?.let(onSave) }, enabled = combo != null && Hotkey.parse(combo!!) != null, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Check, stringResource(Res.string.colors_apply))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.hotkey_clear)) }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

/** Системные уведомления и звуки интерфейса. */
@Composable
fun NotificationsAndSounds(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    SwitchRow(
        stringResource(Res.string.settings_notifications), s.ui.notifications,
        { on -> update { it.copy(ui = it.ui.copy(notifications = on)) } },
        subtitle = stringResource(Res.string.settings_notifications_hint), icon = Symbols.Notifications,
    )
    SwitchRow(
        stringResource(Res.string.settings_sounds), s.ui.sounds,
        { on ->
            update { it.copy(ui = it.ui.copy(sounds = on)) }
            if (on) UiSounds.play(UiSound.Success)
        },
        subtitle = stringResource(Res.string.settings_sounds_hint), icon = Symbols.VolumeUp,
    )
}

/** Кэш: размер, очистка сейчас и автоочистка старых файлов при запуске. */
@Composable
fun CacheSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var size by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { size = graph.cacheCleaner.size() }
    SettingLabel(stringResource(Res.string.settings_cache))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(size?.let { stringResource(Res.string.cache_size, app.youlauncher.ui.components.fileSize(it)) } ?: "…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = {
                scope.launch {
                    val freed = graph.cacheCleaner.clean(0)
                    size = graph.cacheCleaner.size()
                    graph.messenger.show(Res.string.cache_cleaned, app.youlauncher.ui.components.fileSizeText(freed))
                }
            },
            shapes = ButtonDefaults.shapes(),
        ) { IconLabel(Symbols.Delete, stringResource(Res.string.cache_clean)) }
    }
    SwitchRow(
        stringResource(Res.string.cache_auto, s.system.cacheMaxAgeDays), s.system.autoCleanCache,
        { on -> update { it.copy(system = it.system.copy(autoCleanCache = on)) } },
    )
}

/** Отчёт для диагностики: zip без секретов и имени пользователя, чтобы отправить автору. */
@Composable
fun DiagnosticsRow() {
    val graph = LocalAppGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val title = stringResource(Res.string.diag_save)
    SettingLabel(stringResource(Res.string.diag_title))
    Text(stringResource(Res.string.diag_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(
        onClick = {
            val target = Platform.chooseSaveFile(title, "youlauncher-diagnostics.zip") ?: return@OutlinedButton
            scope.launch {
                runCatching {
                    app.youlauncher.data.system.Diagnostics(graph.dirs, AppInfo.version)
                        .write(target, graph.settings.settings.value, graph.instances.instances.value, graph.log.lines.value)
                }.onSuccess { graph.messenger.show(Res.string.diag_saved, it.fileName.toString()) }
                    .onFailure { graph.messenger.show(Res.string.settings_transfer_failed, it.message ?: "", error = true) }
            }
        },
        shapes = ButtonDefaults.shapes(),
    ) { IconLabel(Symbols.BugReport, title) }
}

/** Экспорт и импорт настроек лаунчера в JSON (без путей этого компьютера и без секретов). */
@Composable
fun SettingsTransfer(update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    val exportTitle = stringResource(Res.string.settings_export)
    val importTitle = stringResource(Res.string.settings_import)
    SettingLabel(stringResource(Res.string.settings_transfer))
    Text(stringResource(Res.string.settings_transfer_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = {
                val target = Platform.chooseSaveFile(exportTitle, "youlauncher-settings.json") ?: return@OutlinedButton
                runCatching { Files.writeString(target, SettingsRepository.exportText(graph.settings.settings.value)) }
                    .onSuccess { graph.messenger.show(Res.string.settings_exported, target.fileName.toString()) }
                    .onFailure { graph.messenger.show(Res.string.settings_transfer_failed, it.message ?: "", error = true) }
            },
            shapes = ButtonDefaults.shapes(),
        ) { IconLabel(Symbols.FileExport, exportTitle) }
        OutlinedButton(
            onClick = {
                val file = Platform.chooseFiles(importTitle, listOf("json")).firstOrNull() ?: return@OutlinedButton
                val imported = runCatching { SettingsRepository.decode(Files.readString(file)) }.getOrNull()
                if (imported == null) {
                    graph.messenger.show(Res.string.settings_import_invalid, error = true)
                } else {
                    // Пути этого компьютера сохраняются, остальное берётся из файла.
                    update { cur ->
                        SettingsRepository.migrate(imported).copy(
                            java = imported.java.copy(globalJavaPath = cur.java.globalJavaPath, extraSearchPaths = cur.java.extraSearchPaths),
                            lastInstanceId = cur.lastInstanceId,
                            activeAccountId = cur.activeAccountId,
                        )
                    }
                    graph.messenger.show(Res.string.settings_imported)
                }
            },
            shapes = ButtonDefaults.shapes(),
        ) { IconLabel(Symbols.Upload, importTitle) }
    }
}
