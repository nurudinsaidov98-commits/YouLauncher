package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.ContentFile
import app.youlauncher.core.model.Instance
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.action_remove
import app.youlauncher.resources.content_add_files
import app.youlauncher.resources.content_browse
import app.youlauncher.resources.content_empty_mods
import app.youlauncher.resources.content_empty_packs
import app.youlauncher.resources.content_empty_title
import app.youlauncher.resources.content_enabled
import app.youlauncher.resources.content_search
import app.youlauncher.resources.content_update_all
import app.youlauncher.resources.content_update_available
import app.youlauncher.resources.content_vanilla_mods
import app.youlauncher.resources.modset_delete
import app.youlauncher.resources.modset_name
import app.youlauncher.resources.modset_save
import app.youlauncher.resources.modset_save_confirm
import app.youlauncher.resources.modsets_empty
import app.youlauncher.resources.modsets_title
import app.youlauncher.resources.tab_settings
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.TextInputDialog
import app.youlauncher.data.mods.ModSetLogic
import app.youlauncher.ui.components.fileSize
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.InstanceTab
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource

/** Моды, ресурспаки, шейдеры: переключатели, обновления, удаление, добавление файлов. */
@Composable
fun ContentTab(inst: Instance, vm: InstanceViewModel, kind: ContentKind?) {
    val navigator = LocalNavigator.current
    if (kind == null) {
        EmptyState(Illustration.Mods, stringResource(Res.string.content_empty_title), stringResource(Res.string.content_vanilla_mods)) {
            Button(onClick = { navigator.replace(Screen.Instance(inst.id, InstanceTab.Settings)) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Tune, stringResource(Res.string.tab_settings))
            }
        }
        return
    }
    val files by vm.content(kind).collectAsState()
    var query by remember { mutableStateOf("") }
    val shown = files.filter { query.isBlank() || it.title.contains(query, true) || it.fileName.contains(query, true) }
    val addLabel = stringResource(Res.string.content_add_files)
    val extensions = if (kind == ContentKind.Mods) listOf("jar") else listOf("zip")

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.content_search)) },
                leadingIcon = { Icon(Symbols.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { query = "" }) }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f),
            )
            if (kind == ContentKind.Mods) ModSetsButton(vm, files)
            if (files.any { it.updateAvailable != null }) {
                FilledTonalButton(onClick = { vm.updateAll(kind) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Update, stringResource(Res.string.content_update_all)) }
            }
            OutlinedButton(onClick = { vm.addFiles(kind, Platform.chooseFiles(addLabel, extensions, multiple = true)) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.FileOpen, addLabel)
            }
            Button(onClick = { navigator.navigate(Screen.Mods(inst.id)) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.TravelExplore, stringResource(Res.string.content_browse))
            }
        }
        if (files.isEmpty()) {
            EmptyState(
                Illustration.Mods,
                stringResource(Res.string.content_empty_title),
                stringResource(if (kind == ContentKind.Mods) Res.string.content_empty_mods else Res.string.content_empty_packs),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(shown, key = { _, f -> f.fileName }) { index, file ->
                    ContentRow(file, onToggle = { vm.setEnabled(kind, file, it) }, onRemove = { vm.remove(kind, file) }, modifier = Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ).staggered(index, file.fileName))
                }
            }
        }
    }
}

@Composable
private fun ContentRow(file: ContentFile, onToggle: (Boolean) -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val enabledLabel = stringResource(Res.string.content_enabled)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { ProjectIcon(file.title, null, size = 44.dp) },
            headlineContent = {
                Text(
                    file.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (file.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    listOfNotNull(file.version, file.fileName, fileSize(file.sizeBytes).takeIf { file.sizeBytes > 0 }).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    file.updateAvailable?.let {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(stringResource(Res.string.content_update_available, it), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Switch(checked = file.enabled, onCheckedChange = onToggle, modifier = Modifier.semantics { contentDescription = enabledLabel })
                    TIconButton(Symbols.Delete, stringResource(Res.string.action_remove), onRemove)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** Наборы модов: сохранить, какие моды включены, и переключаться между наборами одним нажатием. */
@Composable
private fun ModSetsButton(vm: InstanceViewModel, files: List<ContentFile>) {
    val sets by vm.modSets.collectAsState()
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Layers, stringResource(Res.string.modsets_title)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            if (sets.isEmpty()) {
                Text(
                    stringResource(Res.string.modsets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 280.dp),
                )
            }
            sets.forEach { set ->
                val active = ModSetLogic.matches(files, set)
                DropdownMenuItem(
                    text = { Text(set.name) },
                    leadingIcon = { Icon(if (active) Symbols.CheckCircleFilled else Symbols.Layers, null) },
                    trailingIcon = { TIconButton(Symbols.Delete, stringResource(Res.string.modset_delete), { vm.deleteModSet(set) }) },
                    onClick = { open = false; vm.applyModSet(set) },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.modset_save)) },
                leadingIcon = { Icon(Symbols.BookmarkAdd, null) },
                onClick = { open = false; naming = true },
            )
        }
    }
    if (naming) {
        TextInputDialog(
            title = stringResource(Res.string.modset_save),
            label = stringResource(Res.string.modset_name),
            initial = "",
            confirmLabel = stringResource(Res.string.modset_save_confirm),
            onConfirm = { vm.saveModSet(it.trim()); naming = false },
            onDismiss = { naming = false },
            validate = { if (it.isBlank()) "" else null },
        )
    }
}
