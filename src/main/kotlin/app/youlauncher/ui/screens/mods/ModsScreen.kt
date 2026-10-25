package app.youlauncher.ui.screens.mods

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.core.repo.ModSort
import app.youlauncher.resources.Res
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.action_install
import app.youlauncher.resources.action_installed
import app.youlauncher.resources.action_retry
import app.youlauncher.resources.count_results
import app.youlauncher.resources.curseforge_body
import app.youlauncher.resources.curseforge_open_console
import app.youlauncher.resources.curseforge_title
import app.youlauncher.resources.curseforge_enter_key
import app.youlauncher.resources.curseforge_use_modrinth
import app.youlauncher.resources.mods_by
import app.youlauncher.resources.mods_categories
import app.youlauncher.resources.mods_compatible_only
import app.youlauncher.resources.mods_error_body
import app.youlauncher.resources.mods_error_title
import app.youlauncher.resources.mods_filters
import app.youlauncher.resources.mods_load_more
import app.youlauncher.resources.mods_no_results_body
import app.youlauncher.resources.mods_no_results_title
import app.youlauncher.resources.mods_search
import app.youlauncher.resources.mods_sort_downloads
import app.youlauncher.resources.mods_sort_follows
import app.youlauncher.resources.mods_sort_newest
import app.youlauncher.resources.mods_sort_relevance
import app.youlauncher.resources.mods_sort_updated
import app.youlauncher.resources.mods_subtitle
import app.youlauncher.resources.mods_target
import app.youlauncher.resources.mods_target_none
import app.youlauncher.resources.mods_type_datapacks
import app.youlauncher.resources.mods_type_modpacks
import app.youlauncher.resources.mods_type_mods
import app.youlauncher.resources.mods_type_resourcepacks
import app.youlauncher.resources.mods_type_shaders
import app.youlauncher.resources.nav_mods
import app.youlauncher.resources.select_project
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.AdaptiveWidth
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.ScreenHeader
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.components.WidthClass
import app.youlauncher.ui.components.compactNumber
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.rememberViewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

fun ProjectType.label(): StringResource = when (this) {
    ProjectType.Mod -> Res.string.mods_type_mods
    ProjectType.ResourcePack -> Res.string.mods_type_resourcepacks
    ProjectType.Shader -> Res.string.mods_type_shaders
    ProjectType.DataPack -> Res.string.mods_type_datapacks
    ProjectType.Modpack -> Res.string.mods_type_modpacks
}

fun ModSort.label(): StringResource = when (this) {
    ModSort.Relevance -> Res.string.mods_sort_relevance
    ModSort.Downloads -> Res.string.mods_sort_downloads
    ModSort.Follows -> Res.string.mods_sort_follows
    ModSort.Newest -> Res.string.mods_sort_newest
    ModSort.Updated -> Res.string.mods_sort_updated
}

@Composable
fun ModsScreen(screen: Screen.Mods) {
    val graph = LocalAppGraph.current
    // Состояние каталога сохраняется, когда открывают страницу мода, и возвращается при выходе с неё.
    val ui = LocalAppUi.current
    val vm = rememberViewModel(screen.instanceId) { ModsViewModel(graph, screen.instanceId, ui.modsMemory) }
    DisposableEffect(vm) { onDispose { ui.modsMemory = vm.memory() } }
    val state by vm.state.collectAsState()
    val progress by vm.progress.collectAsState()
    val navigator = LocalNavigator.current

    AdaptiveWidth { width ->
        Row(Modifier.fillMaxSize().padding(start = Gaps.screen, end = Gaps.screen, top = Gaps.screen)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenHeader(stringResource(Res.string.nav_mods), subtitle = stringResource(Res.string.mods_subtitle)) {
                    TargetPicker(state, vm)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConnectedChoice(
                        options = CatalogSource.entries.map { ChoiceOption(it, it.name) },
                        selected = state.source,
                        onSelect = vm::setSource,
                        showIcons = false,
                    )
                    ConnectedChoice(
                        options = ProjectType.entries.map { ChoiceOption(it, stringResource(it.label())) },
                        selected = state.query.type,
                        onSelect = vm::setType,
                        showIcons = false,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query.text,
                        onValueChange = vm::setText,
                        placeholder = { Text(stringResource(Res.string.mods_search, state.source.name)) },
                        leadingIcon = { Icon(Symbols.Search, null) },
                        trailingIcon = if (state.query.text.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { vm.setText("") }) }) else null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.weight(1f),
                    )
                    SortPicker(state.query.sort, vm::setSort)
                }
                if (width < WidthClass.Expanded && state.categories.isNotEmpty()) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.categories.forEach { c -> FilterChip(selected = c in state.query.categories, onClick = { vm.toggleCategory(c) }, label = { Text(categoryLabel(c)) }) }
                    }
                }
                Results(state, progress, vm, onOpen = { navigator.navigate(Screen.ModProject(it.id, state.source.name, state.target?.id)) })
            }
            if (width >= WidthClass.Expanded) {
                FilterPanel(state, vm, Modifier.width(280.dp).padding(start = 20.dp, bottom = 16.dp))
            }
        }
    }
    InstallSheetHost(vm)
}

@Composable
private fun TargetPicker(state: ModsState, vm: ModsViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) {
            val t = state.target
            if (t != null) InstanceBadge(t.icon, t.gameVersion, size = 22.dp, accent = t.accent) else Icon(Symbols.GridView, null, Modifier.size(18.dp))
            Text(
                if (t != null) "${stringResource(Res.string.mods_target)}: ${t.name}" else stringResource(Res.string.mods_target_none),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Symbols.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            vm.instances().forEach { inst ->
                DropdownMenuItem(
                    text = { Text(inst.name) },
                    leadingIcon = { InstanceBadge(inst.icon, inst.gameVersion, size = 28.dp, accent = inst.accent) },
                    trailingIcon = { Text("${inst.gameVersion} · ${inst.loader.title}", style = MaterialTheme.typography.labelSmall) },
                    onClick = { vm.selectTarget(inst); open = false },
                )
            }
        }
    }
}

@Composable
private fun SortPicker(sort: ModSort, onSort: (ModSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Sort, stringResource(sort.label())) }
        DropdownMenu(open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            ModSort.entries.forEach { s ->
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
private fun FilterPanel(state: ModsState, vm: ModsViewModel, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.mods_filters), style = MaterialTheme.typography.titleMediumEmphasized)
            if (state.target != null) {
                SwitchRow(stringResource(Res.string.mods_compatible_only), state.compatibleOnly, vm::setCompatibleOnly)
            }
            if (state.categories.isNotEmpty()) {
                Text(stringResource(Res.string.mods_categories), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.categories.forEach { c -> FilterChip(selected = c in state.query.categories, onClick = { vm.toggleCategory(c) }, label = { Text(categoryLabel(c)) }) }
                }
            }
        }
    }
}

@Composable
private fun Results(state: ModsState, progress: Map<String, InstallProgress>, vm: ModsViewModel, onOpen: (ModProject) -> Unit) {
    when {
        !vm.catalog.available -> EmptyState(Illustration.Offline, stringResource(Res.string.curseforge_title), stringResource(Res.string.curseforge_body)) {
            FilledTonalButton(onClick = { Platform.openUrl("https://console.curseforge.com/") }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.OpenInNew, stringResource(Res.string.curseforge_open_console))
            }
            val ui = LocalAppUi.current
            val navigator = LocalNavigator.current
            Button(onClick = { ui.pendingAction = "settings:Network"; navigator.selectTop(TopLevel.Settings) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Key, stringResource(Res.string.curseforge_enter_key))
            }
            OutlinedButton(onClick = { vm.setSource(CatalogSource.Modrinth) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.curseforge_use_modrinth)) }
        }
        state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
        state.error != null -> EmptyState(Illustration.Offline, stringResource(Res.string.mods_error_title), stringResource(Res.string.mods_error_body)) {
            Button(onClick = { vm.search() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Refresh, stringResource(Res.string.action_retry)) }
        }
        state.results.isEmpty() -> EmptyState(Illustration.Search, stringResource(Res.string.mods_no_results_title), stringResource(Res.string.mods_no_results_body))
        else -> {
            val listState = rememberLazyListState(vm.scrollIndex, vm.scrollOffset)
            LaunchedEffect(listState) {
                snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) -> vm.scrollIndex = i; vm.scrollOffset = o }
            }
            val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.results.size - 3 } == true } }
            LaunchedEffect(nearEnd, state.results.size) { if (nearEnd) vm.loadMore() }
            val selected by vm.selected.collectAsState()
            val checking by vm.checking.collectAsState()
            Box {
            Column {
                Text(pluralStringResource(Res.plurals.count_results, state.total, state.total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                    itemsIndexed(state.results, key = { _, p -> p.id }) { index, project ->
                        ProjectCard(
                            project,
                            installed = project.id in state.installed,
                            progress = progress[project.id],
                            checking = project.id in checking,
                            selected = project.id in selected,
                            onToggleSelect = { vm.toggleSelected(project) },
                            onInstall = { vm.install(project) },
                            onOpen = { onOpen(project) },
                            modifier = Modifier.staggered(index % 20, project.id),
                        )
                    }
                    if (state.canLoadMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.loadingMore) LoadingIndicator() else TextButton(onClick = vm::loadMore, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.mods_load_more)) }
                            }
                        }
                    }
                }
            }
            SelectionBar(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
fun InstallButton(installed: Boolean, progress: InstallProgress?, checking: Boolean = false, onInstall: () -> Unit) {
    val installing = progress != null && !progress.done && progress.error == null
    when {
        checking -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(40.dp)) }
        installing -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularWavyProgressIndicator(progress = { progress?.progress ?: 0f }, modifier = Modifier.size(40.dp))
        }
        installed -> FilledTonalButton(onClick = onInstall, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.CheckCircleFilled, stringResource(Res.string.action_installed)) }
        else -> Button(onClick = onInstall, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Download, stringResource(Res.string.action_install)) }
    }
}

@Composable
private fun ProjectCard(
    project: ModProject,
    installed: Boolean,
    progress: InstallProgress?,
    checking: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!installed) {
                val label = stringResource(Res.string.select_project)
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() }, modifier = Modifier.semantics { contentDescription = label })
            }
            ProjectIcon(project.title, project.iconUrl, size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.mods_by, project.author), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(project.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Symbols.Download, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(compactNumber(project.downloads), style = MaterialTheme.typography.labelMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Symbols.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Text(compactNumber(project.follows), style = MaterialTheme.typography.labelMedium)
                    }
                    project.categories.take(3).forEach { c ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(categoryLabel(c), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            InstallButton(installed, progress, checking, onInstall)
        }
    }
}

/** Слаг категории каталога в читаемый вид: game-mechanics -> Game mechanics. */
internal fun categoryLabel(slug: String): String = slug.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
