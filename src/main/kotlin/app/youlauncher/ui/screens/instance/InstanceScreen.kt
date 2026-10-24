package app.youlauncher.ui.screens.instance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.resources.Res
import app.youlauncher.resources.instance_background_choose
import app.youlauncher.resources.instance_background_reset
import app.youlauncher.resources.instance_cover_choose
import app.youlauncher.resources.instance_banner_reset
import app.youlauncher.resources.instance_look_hint
import app.youlauncher.resources.instance_look_menu
import androidx.compose.material3.HorizontalDivider
import app.youlauncher.ui.background.UserWallpapers
import app.youlauncher.resources.export_mrpack
import app.youlauncher.resources.export_prism
import app.youlauncher.resources.export_title
import app.youlauncher.resources.action_play
import app.youlauncher.resources.action_stop
import app.youlauncher.resources.instance_not_found
import app.youlauncher.resources.menu_favorite_add
import app.youlauncher.resources.menu_favorite_remove
import app.youlauncher.resources.menu_open_folder
import app.youlauncher.resources.nav_back
import app.youlauncher.resources.tab_log
import app.youlauncher.resources.tab_mods
import app.youlauncher.resources.tab_overview
import app.youlauncher.resources.tab_resourcepacks
import app.youlauncher.resources.tab_screenshots
import app.youlauncher.resources.tab_settings
import app.youlauncher.resources.tab_worlds
import app.youlauncher.resources.tab_servers
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.InstanceCover
import app.youlauncher.ui.components.LaunchStatus
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.VersionCover
import app.youlauncher.ui.components.isBusy
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.InstanceTab
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.navigation.sharedElement
import app.youlauncher.ui.rememberViewModel
import app.youlauncher.ui.theme.onScrimColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun InstanceTab.label(): StringResource = when (this) {
    InstanceTab.Overview -> Res.string.tab_overview
    InstanceTab.Mods -> Res.string.tab_mods
    InstanceTab.ResourcePacks -> Res.string.tab_resourcepacks
    InstanceTab.Worlds -> Res.string.tab_worlds
    InstanceTab.Servers -> Res.string.tab_servers
    InstanceTab.Screenshots -> Res.string.tab_screenshots
    InstanceTab.Settings -> Res.string.tab_settings
    InstanceTab.Log -> Res.string.tab_log
}

fun InstanceTab.icon() = when (this) {
    InstanceTab.Overview -> Symbols.Dashboard
    InstanceTab.Mods -> Symbols.Extension
    InstanceTab.ResourcePacks -> Symbols.Texture
    InstanceTab.Worlds -> Symbols.Public
    InstanceTab.Servers -> Symbols.Dns
    InstanceTab.Screenshots -> Symbols.PhotoLibrary
    InstanceTab.Settings -> Symbols.Tune
    InstanceTab.Log -> Symbols.Description
}

@Composable
fun InstanceScreen(screen: Screen.Instance) {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val vm = rememberViewModel(screen.id) { InstanceViewModel(graph, screen.id) }
    val instance by vm.instance.collectAsState()
    val launch by vm.launch.collectAsState()
    val inst = instance ?: run {
        EmptyState(Illustration.Search, stringResource(Res.string.instance_not_found), "") {
            Button(onClick = { navigator.back() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.ArrowBack, stringResource(Res.string.nav_back)) }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        InstanceHeader(inst, launch, vm)
        PrimaryScrollableTabRow(
            selectedTabIndex = screen.tab.ordinal,
            edgePadding = 24.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            InstanceTab.entries.forEach { tab ->
                Tab(
                    selected = tab == screen.tab,
                    onClick = { navigator.replace(screen.copy(tab = tab)) },
                    text = { Text(stringResource(tab.label())) },
                    icon = { Icon(tab.icon(), contentDescription = null) },
                )
            }
        }
        val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = screen.tab,
            transitionSpec = { fadeIn(effectsIn) togetherWith fadeOut(effectsOut) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "instance-tab",
        ) { tab ->
            when (tab) {
                InstanceTab.Overview -> OverviewTab(inst, vm)
                InstanceTab.Mods -> ContentTab(inst, vm, if (inst.loader == LoaderType.Vanilla) null else ContentKind.Mods)
                InstanceTab.ResourcePacks -> ContentTab(inst, vm, ContentKind.ResourcePacks)
                InstanceTab.Worlds -> WorldsTab(vm)
                InstanceTab.Servers -> ServersTab(inst.id)
                InstanceTab.Screenshots -> ScreenshotsTab(inst, vm)
                InstanceTab.Settings -> InstanceSettingsTab(inst, vm)
                InstanceTab.Log -> LogTab(vm)
            }
        }
    }
}

@Composable
private fun InstanceHeader(inst: Instance, launch: LaunchState, vm: InstanceViewModel) {
    val navigator = LocalNavigator.current
    val time = rememberAmbientTime()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(12.dp).sharedElement("instance-${inst.id}"),
    ) {
        Box(Modifier.fillMaxWidth().height(232.dp)) {
            InstanceCover(inst, Modifier.matchParentSize(), time = time, blur = 40.dp)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f), 1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f))))
            Row(Modifier.padding(12.dp)) {
                TIconButton(Symbols.ArrowBack, stringResource(Res.string.nav_back), { if (!navigator.back()) navigator.selectTop(TopLevel.Library) })
                Spacer(Modifier.weight(1f))
                TIconButton(
                    if (inst.favorite) Symbols.StarFilled else Symbols.Star,
                    stringResource(if (inst.favorite) Res.string.menu_favorite_remove else Res.string.menu_favorite_add),
                    { vm.update { it.copy(favorite = !it.favorite) } },
                )
                LookButton(inst, vm)
                ExportButton(inst, vm)
                TIconButton(Symbols.FolderOpen, stringResource(Res.string.menu_open_folder), { Platform.openFolder(vm.gameDir) })
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                InstanceBadge(inst.icon, inst.gameVersion, size = 80.dp, accent = inst.accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(inst.name, style = MaterialTheme.typography.displaySmallEmphasized, color = onScrimColor(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(inst.gameVersion, inst.loader.title, inst.loaderVersion).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        color = onScrimColor().copy(alpha = 0.85f),
                    )
                    LaunchStatusOnScrim(launch, vm)
                }
                val busy = launch.isBusy
                Button(
                    onClick = { if (busy) vm.stop() else vm.play() },
                    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                    colors = if (busy) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary) else ButtonDefaults.buttonColors(),
                ) {
                    Icon(
                        if (busy) Symbols.StopFilled else Symbols.PlayArrowFilled,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (busy) Res.string.action_stop else Res.string.action_play), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
    }
}

@Composable
private fun LaunchStatusOnScrim(launch: LaunchState, vm: InstanceViewModel) {
    val navigator = LocalNavigator.current
    if (launch is LaunchState.Idle) return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)) {
        LaunchStatus(
            launch,
            onOpenLog = { navigator.replace(Screen.Instance(vm.id, InstanceTab.Log)) },
            onDismiss = vm::dismiss,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).width(420.dp),
        )
    }
}

@Composable
private fun ExportButton(inst: Instance, vm: InstanceViewModel) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.export_title)
    Box {
        TIconButton(Symbols.IosShare, title, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.export_mrpack)) },
                leadingIcon = { Icon(Symbols.Package2, null) },
                onClick = {
                    open = false
                    Platform.chooseSaveFile(title, "${inst.name}.mrpack")?.let { vm.export(it, mrpack = true) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.export_prism)) },
                leadingIcon = { Icon(Symbols.FolderZip, null) },
                onClick = {
                    open = false
                    Platform.chooseSaveFile(title, "${inst.name}.zip")?.let { vm.export(it, mrpack = false) }
                },
            )
        }
    }
}

/** Оформление только этого экземпляра: баннер в шапке и свой фон страницы. Общий фон и другие экземпляры не меняются. */
@Composable
private fun LookButton(inst: Instance, vm: InstanceViewModel) {
    var open by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.instance_look_menu)
    val bannerTitle = stringResource(Res.string.instance_cover_choose)
    val backgroundTitle = stringResource(Res.string.instance_background_choose)
    val ownBackground = inst.atmosphere.wallpaper?.startsWith("file:") == true
    Box {
        TIconButton(Symbols.Wallpaper, title, { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            Text(
                stringResource(Res.string.instance_look_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(260.dp),
            )
            DropdownMenuItem(
                text = { Text(bannerTitle) },
                leadingIcon = { Icon(Symbols.AddPhotoAlternate, null) },
                onClick = {
                    open = false
                    Platform.chooseFiles(bannerTitle, UserWallpapers.extensions.toList()).firstOrNull()?.let(vm::setCover)
                },
            )
            if (inst.coverPath != null) DropdownMenuItem(
                text = { Text(stringResource(Res.string.instance_banner_reset)) },
                leadingIcon = { Icon(Symbols.HideImage, null) },
                onClick = { open = false; vm.setCover(null) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(backgroundTitle) },
                leadingIcon = { Icon(Symbols.Wallpaper, null) },
                onClick = {
                    open = false
                    Platform.chooseFiles(backgroundTitle, UserWallpapers.extensions.toList()).firstOrNull()?.let(vm::setBackground)
                },
            )
            if (inst.atmosphere.wallpaper != null) DropdownMenuItem(
                text = { Text(stringResource(Res.string.instance_background_reset)) },
                leadingIcon = { Icon(if (ownBackground) Symbols.HideImage else Symbols.Refresh, null) },
                onClick = { open = false; vm.setBackground(null) },
            )
        }
    }
}
