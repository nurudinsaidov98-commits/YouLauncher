package app.youlauncher.ui.screens.instance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.CompatStatus
import app.youlauncher.core.repo.LoaderVersion
import app.youlauncher.core.repo.ModCompat
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import org.jetbrains.compose.resources.stringResource

/** Раздел «Версия и загрузчик»: текущая версия, смена и обновление загрузчика кнопкой. */
@Composable
fun VersionSection(inst: Instance, vm: InstanceViewModel) {
    var dialog by remember { mutableStateOf(false) }
    val loaderUpdate by vm.loaderUpdate.collectAsState()
    Text(
        stringResource(Res.string.version_current, listOfNotNull(inst.gameVersion, inst.loader.title, inst.loaderVersion).joinToString(" · ")),
        style = MaterialTheme.typography.titleMedium,
    )
    val repairing by vm.repairing.collectAsState()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { dialog = true }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.SwapVert, stringResource(Res.string.version_change)) }
        OutlinedButton(onClick = vm::repair, enabled = !repairing, shapes = ButtonDefaults.shapes()) {
            if (repairing) LoadingIndicator(Modifier.size(20.dp)) else Icon(Symbols.Healing, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.repair_instance), modifier = Modifier.padding(start = 8.dp))
        }
    }
    Text(stringResource(Res.string.repair_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    AnimatedVisibility(
        visible = loaderUpdate != null,
        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.Upgrade, null)
                Text(stringResource(Res.string.loader_update_available, loaderUpdate.orEmpty()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Button(onClick = vm::updateLoader, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.loader_update)) }
            }
        }
    }
    if (dialog) VersionChangeDialog(inst, onDismiss = { dialog = false })
}

@Composable
private fun VersionChangeDialog(inst: Instance, onDismiss: () -> Unit) {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel(inst.id) { VersionChangeViewModel(graph, inst.id) }
    val s by vm.state.collectAsState()
    val motion = MaterialTheme.motionScheme
    AlertDialog(
        onDismissRequest = { if (!s.applying) onDismiss() },
        icon = { Icon(Symbols.SwapVert, null) },
        title = { Text(stringResource(Res.string.version_change_title)) },
        text = {
            Column(Modifier.widthIn(min = 480.dp, max = 580.dp).animateContentSize(motion.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.version_change_body), style = MaterialTheme.typography.bodyMedium)
                SettingLabel(stringResource(Res.string.version_game))
                Picker(
                    label = s.game,
                    loading = s.versions == null,
                    items = s.versions.orEmpty().map { it.id },
                    selected = s.game,
                    itemLabel = { it },
                    onPick = vm::selectGame,
                )
                SettingLabel(stringResource(Res.string.version_loader))
                ConnectedChoice(
                    options = LoaderType.entries.map { ChoiceOption(it, it.title) },
                    selected = s.loader,
                    onSelect = vm::selectLoader,
                    showIcons = false,
                )
                if (s.loader != LoaderType.Vanilla) {
                    SettingLabel(stringResource(Res.string.version_loader_version))
                    val lv = s.loaderVersions
                    if (lv != null && lv.isEmpty()) {
                        Text(stringResource(Res.string.version_loader_none, s.loader.title, s.game), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                        Picker(
                            label = s.loaderVersion ?: "",
                            loading = lv == null,
                            items = lv.orEmpty(),
                            selected = lv.orEmpty().firstOrNull { it.version == s.loaderVersion },
                            itemLabel = { loaderLabel(it) },
                            onPick = { vm.selectLoaderVersion(it.version) },
                        )
                    }
                }
                if (s.downgrade) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                        Column(Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Symbols.WarningFilled, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Text(stringResource(Res.string.version_downgrade), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            SwitchRow(stringResource(Res.string.version_backup), s.backupWorlds, vm::setBackup)
                        }
                    }
                }
                CompatList(s.compat, s.checking)
            }
        },
        confirmButton = {
            Button(onClick = { vm.apply(onDismiss) }, enabled = s.canApply, shapes = ButtonDefaults.shapes()) {
                if (s.applying) LoadingIndicator(Modifier.size(20.dp)) else IconLabel(Symbols.Check, stringResource(Res.string.version_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !s.applying, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun loaderLabel(v: LoaderVersion): String = buildString {
    append(v.version)
    val tags = listOfNotNull(
        if (v.recommended) stringResource(Res.string.version_loader_recommended) else null,
        if (v.latest) stringResource(Res.string.version_loader_latest) else null,
        if (!v.stable) stringResource(Res.string.version_loader_beta) else null,
    )
    if (tags.isNotEmpty()) append(" · ").append(tags.joinToString(", "))
}

@Composable
private fun <T> Picker(label: String, loading: Boolean, items: List<T>, selected: T?, itemLabel: @Composable (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = !loading, shapes = ButtonDefaults.shapes()) {
            if (loading) LoadingIndicator(Modifier.size(20.dp))
            else Text(if (selected != null) itemLabel(selected) else label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Symbols.ExpandMore, null, Modifier.size(18.dp).padding(start = 4.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large, modifier = Modifier.heightIn(max = 360.dp)) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    trailingIcon = if (item == selected) ({ Icon(Symbols.Check, null) }) else null,
                    onClick = { onPick(item); open = false },
                )
            }
        }
    }
}

/** Итог проверки модов: сводка и список с пометкой, что будет с каждым файлом. */
@Composable
private fun CompatList(compat: List<ModCompat>?, checking: Boolean) {
    when {
        checking || compat == null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(Modifier.size(28.dp))
            Text(stringResource(Res.string.version_checking), style = MaterialTheme.typography.bodyMedium)
        }
        compat.isEmpty() -> Text(stringResource(Res.string.version_no_mods), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Text(
                stringResource(
                    Res.string.compat_summary,
                    compat.count { it.status == CompatStatus.Update }, compat.count { it.status == CompatStatus.Same },
                    compat.count { it.status == CompatStatus.Incompatible }, compat.count { it.status == CompatStatus.Unknown },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(compat, key = { it.kind.name + it.fileName }) { c ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        val (text, container) = when (c.status) {
                            CompatStatus.Update -> stringResource(Res.string.compat_update, c.newVersion?.versionNumber.orEmpty()) to MaterialTheme.colorScheme.primaryContainer
                            CompatStatus.Same -> stringResource(Res.string.compat_same) to MaterialTheme.colorScheme.surfaceContainerHighest
                            CompatStatus.Incompatible -> stringResource(Res.string.compat_incompatible) to MaterialTheme.colorScheme.errorContainer
                            CompatStatus.Unknown -> stringResource(Res.string.compat_unknown) to MaterialTheme.colorScheme.tertiaryContainer
                        }
                        StatusChip(text, container)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, container: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
