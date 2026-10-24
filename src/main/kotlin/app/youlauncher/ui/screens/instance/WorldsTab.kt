package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.World
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_delete
import app.youlauncher.resources.dialog_delete_world_body
import app.youlauncher.resources.dialog_delete_world_title
import app.youlauncher.resources.menu_open_folder
import app.youlauncher.resources.world_backup
import app.youlauncher.resources.world_mode_adventure
import app.youlauncher.resources.world_mode_creative
import app.youlauncher.resources.world_mode_hardcore
import app.youlauncher.resources.world_mode_spectator
import app.youlauncher.resources.world_mode_survival
import app.youlauncher.resources.worlds_empty_body
import app.youlauncher.resources.worlds_empty_title
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.ConfirmDialog
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.fileSize
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource

@Composable
private fun modeLabel(mode: String): String = stringResource(
    when (mode) {
        "creative" -> Res.string.world_mode_creative
        "hardcore" -> Res.string.world_mode_hardcore
        "adventure" -> Res.string.world_mode_adventure
        "spectator" -> Res.string.world_mode_spectator
        else -> Res.string.world_mode_survival
    },
)

@Composable
fun WorldsTab(vm: InstanceViewModel) {
    val worlds by vm.worlds.collectAsState()
    var deleting by remember { mutableStateOf<World?>(null) }
    if (worlds.isEmpty()) {
        EmptyState(Illustration.Worlds, stringResource(Res.string.worlds_empty_title), stringResource(Res.string.worlds_empty_body))
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(worlds, key = { _, w -> w.folder }) { index, world ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().staggered(index)) {
                ListItem(
                    leadingContent = {
                        val icon = when (world.gameMode) { "creative" -> InstanceIcon.Diamond; "hardcore" -> InstanceIcon.Swords; else -> InstanceIcon.Grass }
                        InstanceBadge(icon, world.folder, size = 48.dp)
                    },
                    headlineContent = { Text(world.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("${modeLabel(world.gameMode)} · ${timeAgo(world.lastPlayed)} · ${fileSize(world.sizeBytes)}") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = { vm.backupWorld(world) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Backup, stringResource(Res.string.world_backup)) }
                            TIconButton(Symbols.FolderOpen, stringResource(Res.string.menu_open_folder), { Platform.openFolder(vm.gameDir.resolve("saves").resolve(world.folder)) })
                            TIconButton(Symbols.Delete, stringResource(Res.string.action_delete), { deleting = world })
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    deleting?.let { w ->
        ConfirmDialog(
            title = stringResource(Res.string.dialog_delete_world_title, w.name),
            text = stringResource(Res.string.dialog_delete_world_body),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = { vm.deleteWorld(w) },
            onDismiss = { deleting = null },
            icon = Symbols.Delete,
            destructive = true,
        )
    }
}
