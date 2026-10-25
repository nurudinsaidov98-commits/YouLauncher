package app.youlauncher.ui.screens.instance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.youlauncher.core.repo.ModSort
import app.youlauncher.resources.content_sort
import app.youlauncher.resources.content_sort_enabled
import app.youlauncher.resources.content_sort_name
import app.youlauncher.resources.content_sort_newest
import app.youlauncher.resources.content_sort_size
import app.youlauncher.resources.content_sort_updates
import app.youlauncher.ui.screens.mods.label
import org.jetbrains.compose.resources.StringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.toShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ProjectType
import app.youlauncher.core.repo.InstallProgress
import app.youlauncher.resources.action_retry
import app.youlauncher.resources.content_catalog
import app.youlauncher.resources.content_catalog_for
import app.youlauncher.resources.content_empty_shaders
import app.youlauncher.resources.content_installed
import app.youlauncher.resources.content_resize
import app.youlauncher.resources.curseforge_body
import app.youlauncher.resources.curseforge_title
import app.youlauncher.resources.curseforge_use_modrinth
import app.youlauncher.resources.mods_error_body
import app.youlauncher.resources.mods_error_title
import app.youlauncher.resources.mods_no_results_body
import app.youlauncher.resources.mods_no_results_title
import app.youlauncher.resources.mods_search
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.rememberViewModel
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.ExpressiveScrollbar
import app.youlauncher.ui.components.compactNumber
import app.youlauncher.ui.screens.mods.CatalogSource
import app.youlauncher.ui.screens.mods.InstallButton
import app.youlauncher.ui.screens.mods.InstallSheetHost
import app.youlauncher.ui.screens.mods.ModsViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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

/**
 * Моды, ресурспаки, шейдеры. В широком окне две панели: слева установленное (переключатели, обновления,
 * удаление, свои файлы), справа каталог этого же типа под версию экземпляра; между ними разделитель,
 * который можно тянуть. В узком окне панели переключаются кнопками.
 */
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
    val ui = LocalAppUi.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        if (maxWidth >= SPLIT_MIN_WIDTH) {
            val total = constraints.maxWidth.toFloat()
            // Мод можно перетащить мышью: из установленного в каталог — удалить, из каталога в установленное — поставить.
            val drag = remember { ContentDrag() }
            CompositionLocalProvider(LocalContentDrag provides drag) {
                Box(Modifier.fillMaxSize().onGloballyPositioned { drag.origin = it.positionInRoot() }) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(ui.contentSplit).fillMaxHeight().onGloballyPositioned { drag.installedBounds = it.boundsInRoot() }) {
                            InstalledPane(inst, vm, kind)
                            DropZone(visible = drag.payload is DragPayload.Catalog, over = drag.overTarget, remove = false)
                        }
                        PaneDivider(onDrag = { px -> ui.contentSplit = (ui.contentSplit + px / total).coerceIn(0.3f, 0.7f) })
                        Box(Modifier.weight(1f - ui.contentSplit).fillMaxHeight().onGloballyPositioned { drag.catalogBounds = it.boundsInRoot() }) {
                            CatalogPane(inst, vm, kind)
                            DropZone(visible = drag.payload is DragPayload.Installed, over = drag.overTarget, remove = true)
                        }
                    }
                    DragGhost(drag)
                }
            }
        } else {
            var catalog by remember(kind) { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedChoice(
                    options = listOf(ChoiceOption(false, stringResource(Res.string.content_installed), Symbols.Inventory2), ChoiceOption(true, stringResource(Res.string.content_catalog), Symbols.TravelExplore)),
                    selected = catalog,
                    onSelect = { catalog = it },
                )
                if (catalog) CatalogPane(inst, vm, kind) else InstalledPane(inst, vm, kind)
            }
        }
    }
}

/** Ширина, начиная с которой установленное и каталог помещаются рядом. */
private val SPLIT_MIN_WIDTH = 900.dp

/** Заголовок панели: значок в форме M3, название и счётчик или подпись. */
@Composable
private fun PaneHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, badge: String?, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Surface(shape = androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
                if (badge != null) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(badge, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
                    }
                }
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/**
 * Разделитель панелей: тонкая линия во всю высоту и ручка-таблетка посередине. При наведении и
 * перетаскивании ручка на пружине вытягивается и окрашивается в основной цвет; курсор показывает,
 * что ширину можно менять.
 */
@Composable
private fun PaneDivider(onDrag: (Float) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val handleHeight by animateDpAsState(if (dragging) 88.dp else if (hovered) 72.dp else 56.dp, spatial, label = "handleH")
    val handleWidth by animateDpAsState(if (dragging) 12.dp else if (hovered) 10.dp else 6.dp, spatial, label = "handleW")
    val handleColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, MaterialTheme.motionScheme.fastEffectsSpec(), label = "handleC")
    val lineColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.motionScheme.fastEffectsSpec(), label = "lineC")
    val description = stringResource(Res.string.content_resize)
    Box(
        Modifier
            .width(28.dp)
            .fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, delta -> change.consume(); onDrag(delta) }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Линия с разрывом вокруг ручки: ручка читается чётко на любом фоне.
        Column(Modifier.fillMaxHeight().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).width(2.dp).background(lineColor, RoundedCornerShape(50)))
            Spacer(Modifier.height(handleHeight + 20.dp))
            Box(Modifier.weight(1f).width(2.dp).background(lineColor, RoundedCornerShape(50)))
        }
        Box(Modifier.size(handleWidth, handleHeight).background(handleColor, RoundedCornerShape(50)))
    }
}

/** Левая панель: что уже стоит в экземпляре. */
@Composable
private fun InstalledPane(inst: Instance, vm: InstanceViewModel, kind: ContentKind) {
    val files by vm.content(kind).collectAsState()
    val ui = LocalAppUi.current
    var query by remember { mutableStateOf("") }
    val shown = remember(files, query, ui.installedSort) {
        ui.installedSort.sort(files.filter { query.isBlank() || it.title.contains(query, true) || it.fileName.contains(query, true) })
    }
    val addLabel = stringResource(Res.string.content_add_files)
    val extensions = if (kind == ContentKind.Mods) listOf("jar") else listOf("zip")

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneHeader(Symbols.Inventory2, stringResource(Res.string.content_installed), files.size.toString()) {
            if (files.any { it.updateAvailable != null }) {
                TIconButton(Symbols.Update, stringResource(Res.string.content_update_all), { vm.updateAll(kind) })
            }
            TIconButton(Symbols.FileOpen, addLabel, { vm.addFiles(kind, Platform.chooseFiles(addLabel, extensions, multiple = true)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.content_search)) },
                leadingIcon = { Icon(Symbols.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { query = "" }) }) else null,
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
            if (kind == ContentKind.Mods) ModSetsButton(vm, files)
            // Сортировщик во внутреннем углу у разделителя.
            SortButton(InstalledSort.entries, ui.installedSort, { stringResource(it.label) }, { ui.installedSort = it })
        }
        if (files.isEmpty()) {
            EmptyState(
                Illustration.Mods,
                stringResource(Res.string.content_empty_title),
                stringResource(when (kind) {
                    ContentKind.Mods -> Res.string.content_empty_mods
                    ContentKind.ShaderPacks -> Res.string.content_empty_shaders
                    else -> Res.string.content_empty_packs
                }),
                artSize = 140.dp,
            )
        } else {
            val installedList = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
            LazyColumn(state = installedList, modifier = Modifier.padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(shown, key = { _, f -> f.fileName }) { index, file ->
                    ContentRow(file, onToggle = { vm.setEnabled(kind, file, it) }, onRemove = { vm.remove(kind, file) }, modifier = Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    ).staggered(index, file.fileName).dragSource({ DragPayload.Installed(file.fileName, file.title) { vm.remove(kind, file) } }, file.fileName))
                }
            }
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                ExpressiveScrollbar(installedList, label = { shown.getOrNull(it)?.title })
            }
            }
        }
    }
}

private fun ContentKind.projectType(): ProjectType = when (this) {
    ContentKind.Mods -> ProjectType.Mod
    ContentKind.ResourcePacks -> ProjectType.ResourcePack
    ContentKind.ShaderPacks -> ProjectType.Shader
    ContentKind.DataPacks -> ProjectType.DataPack
}

/**
 * Правая панель: каталог Modrinth или CurseForge только этого типа и только подходящее экземпляру.
 * Поиск, источник и прокрутка запоминаются на экземпляр и тип: после страницы мода всё на месте.
 */
@Composable
private fun CatalogPane(inst: Instance, instanceVm: InstanceViewModel, kind: ContentKind) {
    val graph = LocalAppGraph.current
    val ui = LocalAppUi.current
    val navigator = LocalNavigator.current
    val key = inst.id to kind
    val vm = rememberViewModel(inst.id, kind) { ModsViewModel(graph, inst.id, ui.contentCatalogs[key], type = kind.projectType()) }
    DisposableEffect(vm) { onDispose { ui.contentCatalogs[key] = vm.memory() } }
    val state by vm.state.collectAsState()
    val progress by vm.progress.collectAsState()
    val checking by vm.checking.collectAsState()
    // Слева что-то удалили или добавили: отметки «установлено» справа обновляются.
    val files by instanceVm.content(kind).collectAsState()
    LaunchedEffect(files.map { it.fileName }) { vm.refreshInstalled() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PaneHeader(
            Symbols.TravelExplore,
            stringResource(Res.string.content_catalog),
            badge = null,
            subtitle = stringResource(Res.string.content_catalog_for, listOfNotNull(inst.gameVersion, inst.loader.title.takeIf { kind == ContentKind.Mods }).joinToString(" · ")),
        ) {
            ConnectedChoice(
                options = CatalogSource.entries.map { ChoiceOption(it, it.name) },
                selected = state.source,
                onSelect = vm::setSource,
                showIcons = false,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Сортировщик каталога — во внутреннем углу у разделителя, зеркально левой панели.
            SortButton(ModSort.entries, state.query.sort, { stringResource(it.label()) }, vm::setSort)
            OutlinedTextField(
                value = state.query.text,
                onValueChange = vm::setText,
                placeholder = { Text(stringResource(Res.string.mods_search, state.source.name)) },
                leadingIcon = { Icon(Symbols.Search, null) },
                trailingIcon = if (state.query.text.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { vm.setText("") }) }) else null,
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
        }
        when {
            !vm.catalog.available -> EmptyState(Illustration.Offline, stringResource(Res.string.curseforge_title), stringResource(Res.string.curseforge_body), artSize = 120.dp) {
                OutlinedButton(onClick = { vm.setSource(CatalogSource.Modrinth) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.curseforge_use_modrinth)) }
            }
            state.loading && state.results.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            state.error != null -> EmptyState(Illustration.Offline, stringResource(Res.string.mods_error_title), stringResource(Res.string.mods_error_body), artSize = 120.dp) {
                Button(onClick = { vm.search() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Refresh, stringResource(Res.string.action_retry)) }
            }
            state.results.isEmpty() -> EmptyState(Illustration.Search, stringResource(Res.string.mods_no_results_title), stringResource(Res.string.mods_no_results_body), artSize = 120.dp)
            else -> {
                val listState = rememberLazyListState(vm.scrollIndex, vm.scrollOffset)
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, o) -> vm.scrollIndex = i; vm.scrollOffset = o }
                }
                val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.results.size - 3 } == true } }
                LaunchedEffect(nearEnd, state.results.size) { if (nearEnd) vm.loadMore() }
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.padding(end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(state.results, key = { _, p -> p.id }) { index, project ->
                            CatalogRow(
                                project,
                                installed = project.id in state.installed,
                                progress = progress[project.id],
                                checking = project.id in checking,
                                onInstall = { vm.install(project) },
                                onOpen = { navigator.navigate(Screen.ModProject(project.id, state.source.name, inst.id)) },
                                modifier = Modifier.staggered(index % 20, project.id).let { m ->
                                    if (project.id in state.installed) m
                                    else m.dragSource({ DragPayload.Catalog(project.id, project.title, project.iconUrl) { vm.install(project) } }, project.id)
                                },
                            )
                        }
                        if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        ExpressiveScrollbar(listState, label = { state.results.getOrNull(it)?.title })
                    }
                }
            }
        }
    }
    InstallSheetHost(vm)
}

/** Строка каталога в панели: компактнее карточки большого каталога, кнопка установки справа. */
@Composable
private fun CatalogRow(project: ModProject, installed: Boolean, progress: InstallProgress?, checking: Boolean, onInstall: () -> Unit, onOpen: () -> Unit, modifier: Modifier) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectIcon(project.title, project.iconUrl, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(project.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(project.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Symbols.Download, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(compactNumber(project.downloads), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InstallButton(installed, progress, checking, onInstall)
        }
    }
}

@Composable
private fun ContentRow(file: ContentFile, onToggle: (Boolean) -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val enabledLabel = stringResource(Res.string.content_enabled)
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
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

/** Порядок установленного: по названию, новые, тяжёлые, включённые или с обновлениями сверху. */
enum class InstalledSort(val label: StringResource) {
    Name(Res.string.content_sort_name),
    Newest(Res.string.content_sort_newest),
    Size(Res.string.content_sort_size),
    Enabled(Res.string.content_sort_enabled),
    Updates(Res.string.content_sort_updates);

    fun sort(files: List<ContentFile>): List<ContentFile> = when (this) {
        Name -> files.sortedBy { it.title.lowercase() }
        Newest -> files.sortedByDescending { it.addedAt }
        Size -> files.sortedByDescending { it.sizeBytes }
        Enabled -> files.sortedWith(compareByDescending<ContentFile> { it.enabled }.thenBy { it.title.lowercase() })
        Updates -> files.sortedWith(compareByDescending<ContentFile> { it.updateAvailable != null }.thenBy { it.title.lowercase() })
    }
}

/** Сортировщик-таблетка: текущий порядок на кнопке, остальные в меню. */
@Composable
private fun <T> SortButton(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(onClick = { open = true }, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(Symbols.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Text(
                stringResource(Res.string.content_sort),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(label(o)) },
                    trailingIcon = if (o == selected) ({ Icon(Symbols.Check, null) }) else null,
                    onClick = { onSelect(o); open = false },
                )
            }
        }
    }
}
