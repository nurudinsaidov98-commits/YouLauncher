package app.youlauncher.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceSort
import app.youlauncher.core.model.LoaderType
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_cancel
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.action_create
import app.youlauncher.resources.action_delete
import app.youlauncher.resources.action_save
import app.youlauncher.resources.copy_suffix
import app.youlauncher.resources.dialog_delete_instance_body
import app.youlauncher.resources.dialog_delete_instance_title
import app.youlauncher.resources.dialog_duplicate_title
import app.youlauncher.resources.dialog_rename_title
import app.youlauncher.resources.error_name_taken
import app.youlauncher.resources.fab_import
import app.youlauncher.resources.fab_menu
import app.youlauncher.resources.fab_new_instance
import app.youlauncher.resources.field_name
import app.youlauncher.resources.import_body
import app.youlauncher.resources.import_choose
import app.youlauncher.resources.import_title
import app.youlauncher.resources.library_all
import app.youlauncher.resources.library_favorites
import app.youlauncher.resources.library_no_results_body
import app.youlauncher.resources.library_no_results_title
import app.youlauncher.resources.library_reset_filters
import app.youlauncher.resources.library_search
import app.youlauncher.resources.library_sort
import app.youlauncher.resources.msg_shortcut_created
import app.youlauncher.resources.msg_shortcut_failed
import app.youlauncher.resources.nav_library
import app.youlauncher.resources.sort_created
import app.youlauncher.resources.sort_last_played
import app.youlauncher.resources.sort_name
import app.youlauncher.resources.sort_play_time
import app.youlauncher.resources.sort_version
import app.youlauncher.resources.home_empty_body
import app.youlauncher.resources.home_empty_title
import app.youlauncher.resources.count_instances
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.ConfirmDialog
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.ScreenHeader
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.TextInputDialog
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.symbol
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.rememberViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

fun InstanceSort.label(): StringResource = when (this) {
    InstanceSort.LastPlayed -> Res.string.sort_last_played
    InstanceSort.Name -> Res.string.sort_name
    InstanceSort.PlayTime -> Res.string.sort_play_time
    InstanceSort.Created -> Res.string.sort_created
    InstanceSort.Version -> Res.string.sort_version
}

private sealed interface LibraryDialog {
    data object Create : LibraryDialog
    data object Import : LibraryDialog
    data class Rename(val instance: Instance) : LibraryDialog
    data class Duplicate(val instance: Instance) : LibraryDialog
    data class Delete(val instance: Instance) : LibraryDialog
}

@Composable
fun LibraryScreen() {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { LibraryViewModel(graph) }
    val state by vm.state.collectAsState()
    val navigator = LocalNavigator.current
    val ui = LocalAppUi.current
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    var fabOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.pendingAction) { if (ui.consume("newInstance")) dialog = LibraryDialog.Create }

    fun onAction(instance: Instance, action: InstanceAction) {
        when (action) {
            InstanceAction.Play -> if (state.launches[instance.id]?.let { it is app.youlauncher.core.repo.LaunchState.Preparing || it is app.youlauncher.core.repo.LaunchState.Running } == true) vm.stop(instance.id) else vm.play(instance.id)
            InstanceAction.Open -> navigator.navigate(Screen.Instance(instance.id))
            InstanceAction.Favorite -> vm.toggleFavorite(instance.id)
            InstanceAction.Rename -> dialog = LibraryDialog.Rename(instance)
            InstanceAction.Duplicate -> dialog = LibraryDialog.Duplicate(instance)
            InstanceAction.Folder -> Platform.openFolder(graph.instances.gameDir(instance.id))
            InstanceAction.Shortcut -> scope.launch {
                val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Platform.createDesktopShortcut(instance.name, "--launch ${instance.id}") }
                graph.messenger.show(if (ok) Res.string.msg_shortcut_created else Res.string.msg_shortcut_failed, error = !ok)
            }
            InstanceAction.Delete -> dialog = LibraryDialog.Delete(instance)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = Gaps.screen, end = Gaps.screen, top = Gaps.screen), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScreenHeader(
                stringResource(Res.string.nav_library),
                subtitle = pluralStringResource(Res.plurals.count_instances, state.all.size, state.all.size),
            ) {
                SortMenu(state.filter.sort, vm::setSort)
            }
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                trailingIcon = if (state.filter.query.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { vm.setQuery("") }) }) else null,
                placeholder = { Text(stringResource(Res.string.library_search)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = !state.filter.favoritesOnly && state.filter.group == null,
                        onClick = { vm.setGroup(null); vm.setFavoritesOnly(false) },
                        label = { Text(stringResource(Res.string.library_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = state.filter.favoritesOnly,
                        onClick = { vm.setFavoritesOnly(!state.filter.favoritesOnly) },
                        label = { Text(stringResource(Res.string.library_favorites)) },
                        leadingIcon = { Icon(if (state.filter.favoritesOnly) Symbols.StarFilled else Symbols.Star, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
                items(state.groups) { g ->
                    FilterChip(
                        selected = state.filter.group == g,
                        onClick = { vm.setGroup(if (state.filter.group == g) null else g) },
                        label = { Text(g) },
                        leadingIcon = { Icon(Symbols.Workspaces, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
                item { Spacer(Modifier.width(16.dp)) }
                items(LoaderType.entries) { l ->
                    FilterChip(
                        selected = state.filter.loader == l,
                        onClick = { vm.setLoader(if (state.filter.loader == l) null else l) },
                        label = { Text(l.title) },
                        leadingIcon = { Icon(l.symbol(), null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            when {
                state.all.isEmpty() -> EmptyState(Illustration.Library, stringResource(Res.string.home_empty_title), stringResource(Res.string.home_empty_body)) {
                    Button(onClick = { dialog = LibraryDialog.Create }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Add, stringResource(Res.string.fab_new_instance)) }
                }
                state.shown.isEmpty() -> EmptyState(Illustration.Search, stringResource(Res.string.library_no_results_title), stringResource(Res.string.library_no_results_body)) {
                    OutlinedButton(onClick = vm::reset, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.FilterAlt, stringResource(Res.string.library_reset_filters)) }
                }
                else -> {
                    val gridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(280.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.shown, key = { it.id }) { inst ->
                            val index = state.shown.indexOf(inst)
                            InstanceCard(
                                instance = inst,
                                launch = state.launches[inst.id] ?: app.youlauncher.core.repo.LaunchState.Idle,
                                onClick = { navigator.navigate(Screen.Instance(inst.id)) },
                                onAction = { onAction(inst, it) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                ).staggered(index, inst.id),
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButtonMenu(
            expanded = fabOpen,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            button = {
                val label = stringResource(Res.string.fab_menu)
                ToggleFloatingActionButton(checked = fabOpen, onCheckedChange = { fabOpen = it }) {
                    val icon: ImageVector = if (checkedProgress > 0.5f) Symbols.Close else Symbols.Add
                    Icon(icon, contentDescription = label, modifier = with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) })
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = { fabOpen = false; dialog = LibraryDialog.Import },
                text = { Text(stringResource(Res.string.fab_import)) },
                icon = { Icon(Symbols.FileOpen, null) },
            )
            FloatingActionButtonMenuItem(
                onClick = { fabOpen = false; dialog = LibraryDialog.Create },
                text = { Text(stringResource(Res.string.fab_new_instance)) },
                icon = { Icon(Symbols.AddCircle, null) },
            )
        }
    }

    when (val d = dialog) {
        LibraryDialog.Create -> CreateInstanceWizard(
            onDismiss = { dialog = null },
            onCreate = { spec -> vm.create(spec) { navigator.navigate(Screen.Instance(it.id)) } },
            existingGroups = state.groups,
        )
        LibraryDialog.Import -> ImportDialog(onDismiss = { dialog = null }) { path ->
            vm.import(path) { navigator.navigate(Screen.Instance(it.id)) }
        }
        is LibraryDialog.Rename -> TextInputDialog(
            title = stringResource(Res.string.dialog_rename_title),
            label = stringResource(Res.string.field_name),
            initial = d.instance.name,
            confirmLabel = stringResource(Res.string.action_save),
            onConfirm = { vm.rename(d.instance.id, it) },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.Duplicate -> {
            val taken = stringResource(Res.string.error_name_taken)
            val names = remember { vm.existingNames() }
            TextInputDialog(
                title = stringResource(Res.string.dialog_duplicate_title),
                label = stringResource(Res.string.field_name),
                initial = LibraryLogic.uniqueName(stringResource(Res.string.copy_suffix, d.instance.name), names),
                confirmLabel = stringResource(Res.string.action_create),
                onConfirm = { vm.duplicate(d.instance.id, it) },
                onDismiss = { dialog = null },
                validate = { if (it.trim() in names) taken else null },
            )
        }
        is LibraryDialog.Delete -> ConfirmDialog(
            title = stringResource(Res.string.dialog_delete_instance_title, d.instance.name),
            text = stringResource(Res.string.dialog_delete_instance_body),
            confirmLabel = stringResource(Res.string.action_delete),
            onConfirm = { vm.delete(d.instance) },
            onDismiss = { dialog = null },
            icon = Symbols.Delete,
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun SortMenu(sort: InstanceSort, onSort: (InstanceSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
            IconLabel(Symbols.Sort, stringResource(sort.label()))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Text(stringResource(Res.string.library_sort), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            InstanceSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.label())) },
                    trailingIcon = if (s == sort) ({ Icon(Symbols.Check, null) }) else null,
                    onClick = { onSort(s); open = false },
                )
            }
        }
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onFile: (java.nio.file.Path) -> Unit) {
    val title = stringResource(Res.string.import_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.FileOpen, null) },
        title = { Text(title) },
        text = { Text(stringResource(Res.string.import_body)) },
        confirmButton = {
            Button(
                onClick = {
                    val file = Platform.chooseFiles(title, listOf("mrpack", "zip")).firstOrNull()
                    if (file != null) { onFile(file); onDismiss() }
                },
                shapes = ButtonDefaults.shapes(),
            ) { IconLabel(Symbols.FolderOpen, stringResource(Res.string.import_choose)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

