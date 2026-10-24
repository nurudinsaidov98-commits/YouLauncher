package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.youlauncher.ui.components.WithTooltip
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.BackupSchedule
import app.youlauncher.core.model.Instance
import app.youlauncher.resources.*
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.UserWallpapers
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.InstanceCover
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.fileSize
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.SeedColors
import app.youlauncher.ui.theme.toSeedLong
import org.jetbrains.compose.resources.stringResource

/** Цвет и обложка карточки экземпляра: цвет как у версии или один из seed-цветов, своя картинка обложки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardLookSection(inst: Instance, vm: InstanceViewModel) {
    val coverTitle = stringResource(Res.string.instance_cover_choose)
    Box(Modifier.fillMaxWidth().height(120.dp).clip(MaterialTheme.shapes.large)) {
        InstanceCover(inst, Modifier.fillMaxWidth().height(120.dp))
    }
    SettingLabel(stringResource(Res.string.instance_color))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDot(selected = inst.accent == null, label = stringResource(Res.string.instance_color_version), onClick = { vm.update { it.copy(accent = null) } }) {
            Icon(Symbols.AutoAwesome, null, Modifier.size(18.dp))
        }
        SeedColors.forEach { seed ->
            val argb = seed.color.toSeedLong()
            val scheme = rememberSceneScheme(argb.toInt())
            ColorDot(selected = inst.accent == argb, label = stringResource(seed.name), onClick = { vm.update { it.copy(accent = argb) } }, fill = scheme.primaryContainer) {}
        }
    }
    SettingLabel(stringResource(Res.string.instance_cover))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = { Platform.chooseFiles(coverTitle, UserWallpapers.extensions.toList()).firstOrNull()?.let(vm::setCover) },
            shapes = ButtonDefaults.shapes(),
        ) { IconLabel(Symbols.AddPhotoAlternate, coverTitle) }
        if (inst.coverPath != null) {
            TextButton(onClick = { vm.setCover(null) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.HideImage, stringResource(Res.string.instance_cover_reset)) }
        }
    }
}

@Composable
private fun ColorDot(selected: Boolean, label: String, onClick: () -> Unit, fill: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerHighest, content: @Composable () -> Unit) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    WithTooltip(label) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(fill)
                .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
                .selectable(selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Резервные копии экземпляра: вручную, по расписанию, список с восстановлением в новый экземпляр. */
@Composable
fun BackupSection(inst: Instance, vm: InstanceViewModel, onOpenInstance: (String) -> Unit) {
    val backups by vm.backups.collectAsState()
    val busy by vm.backingUp.collectAsState()
    LaunchedEffect(inst.id) { vm.loadBackups() }
    Text(stringResource(Res.string.backup_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = vm::backupNow, enabled = !busy, shapes = ButtonDefaults.shapes()) {
            if (busy) LoadingIndicator(Modifier.size(20.dp)) else Icon(Symbols.Backup, null, Modifier.size(18.dp))
            Text(stringResource(Res.string.backup_now), modifier = Modifier.padding(start = 8.dp))
        }
        inst.lastBackupAt?.let { Text(stringResource(Res.string.backup_last, timeAgo(it)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    SettingLabel(stringResource(Res.string.backup_schedule))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(BackupSchedule.Off, stringResource(Res.string.backup_off)),
            ChoiceOption(BackupSchedule.Daily, stringResource(Res.string.backup_daily)),
            ChoiceOption(BackupSchedule.Weekly, stringResource(Res.string.backup_weekly)),
        ),
        selected = inst.backupSchedule,
        onSelect = { s -> vm.update { it.copy(backupSchedule = s) } },
        showIcons = false,
    )
    val list = backups.orEmpty()
    if (list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { b ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (b.scheduled) Symbols.Schedule else Symbols.Backup, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(Formats.dateTime(b.createdAt), style = MaterialTheme.typography.bodyLarge)
                            Text(fileSize(b.sizeBytes) + if (b.scheduled) " · " + stringResource(Res.string.backup_auto) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val restoredName = stringResource(Res.string.backup_restored_name, inst.name, Formats.date(b.createdAt))
                        OutlinedButton(onClick = { vm.restoreBackup(b, restoredName, onOpenInstance) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.backup_restore))
                        }
                        TIconButton(Symbols.FolderOpen, stringResource(Res.string.menu_open_folder), { Platform.openFolder(b.path.parent) })
                        TIconButton(Symbols.Delete, stringResource(Res.string.backup_delete), { vm.deleteBackup(b) })
                    }
                }
            }
        }
    }
}
