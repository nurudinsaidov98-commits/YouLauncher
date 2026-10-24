package app.youlauncher.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.settings.HomeWidget
import app.youlauncher.resources.Res
import app.youlauncher.resources.home_add_account
import app.youlauncher.resources.home_choose_instance
import app.youlauncher.resources.home_continue
import app.youlauncher.resources.home_empty_body
import app.youlauncher.resources.home_empty_title
import app.youlauncher.resources.home_greeting_day
import app.youlauncher.resources.home_greeting_evening
import app.youlauncher.resources.home_greeting_morning
import app.youlauncher.resources.home_greeting_night
import app.youlauncher.resources.home_last_session
import app.youlauncher.resources.home_last_session_body
import app.youlauncher.resources.home_last_session_none
import app.youlauncher.resources.home_not_played
import app.youlauncher.resources.home_open_instance
import app.youlauncher.resources.home_play_as
import app.youlauncher.resources.home_ready
import app.youlauncher.resources.home_recent
import app.youlauncher.resources.home_shortcuts
import app.youlauncher.resources.home_stats
import app.youlauncher.resources.home_stats_instances
import app.youlauncher.resources.home_stats_last14
import app.youlauncher.resources.home_stats_launches
import app.youlauncher.resources.home_stats_playtime
import app.youlauncher.resources.library_new
import app.youlauncher.resources.shortcut_console
import app.youlauncher.resources.shortcut_folder
import app.youlauncher.resources.shortcut_mods
import app.youlauncher.resources.shortcut_new_instance
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.components.AdaptiveWidth
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.InstanceCover
import app.youlauncher.ui.components.LaunchStatus
import app.youlauncher.ui.components.MaxWidth
import app.youlauncher.ui.components.PlayerHead
import app.youlauncher.ui.components.PlaySplitButton
import app.youlauncher.ui.components.SectionTitle
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.VersionCover
import app.youlauncher.ui.components.WidthClass
import app.youlauncher.ui.components.duration
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.navigation.sharedElement
import app.youlauncher.ui.rememberViewModel
import app.youlauncher.ui.theme.onScrimColor
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime

@Composable
fun HomeScreen() {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel { HomeViewModel(graph) }
    val state by vm.state.collectAsState()
    val settings by graph.settings.settings.collectAsState()
    val navigator = LocalNavigator.current
    val ui = LocalAppUi.current

    LaunchedEffect(ui.pendingAction) { if (ui.consume("play")) vm.play() }

    if (state.instances.isEmpty()) {
        EmptyState(
            Illustration.Library,
            stringResource(Res.string.home_empty_title),
            stringResource(Res.string.home_empty_body),
        ) {
            Button(onClick = { navigator.selectTop(TopLevel.Library); ui.pendingAction = "newInstance" }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Add, stringResource(Res.string.library_new))
            }
        }
        return
    }

    AdaptiveWidth { width ->
        val wide = width >= WidthClass.Expanded
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Gaps.screen),
            verticalArrangement = Arrangement.spacedBy(Gaps.section),
        ) {
            MaxWidth {
                Column(verticalArrangement = Arrangement.spacedBy(Gaps.section)) {
                    Greeting(state)
                    val widgets = settings.ui.homeWidgets
                    val main = widgets.filter { it == HomeWidget.ContinueCard || it == HomeWidget.RecentInstances }
                    val side = widgets.filterNot { it in main }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Gaps.section)) {
                            Column(Modifier.weight(1.8f), verticalArrangement = Arrangement.spacedBy(Gaps.section)) {
                                main.forEachIndexed { i, w -> Widget(w, state, vm, Modifier.staggered(i)) }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gaps.section)) {
                                side.forEachIndexed { i, w -> Widget(w, state, vm, Modifier.staggered(i + 2)) }
                            }
                        }
                    } else {
                        widgets.forEachIndexed { i, w -> Widget(w, state, vm, Modifier.staggered(i)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Widget(widget: HomeWidget, state: HomeState, vm: HomeViewModel, modifier: Modifier) {
    when (widget) {
        HomeWidget.ContinueCard -> state.selected?.let { ContinueCard(state, it, vm, modifier) }
        HomeWidget.RecentInstances -> RecentCarousel(state.instances, modifier)
        HomeWidget.Stats -> StatsCard(state.stats, modifier)
        HomeWidget.Shortcuts -> ShortcutsCard(state, modifier)
        HomeWidget.LastSession -> LastSessionCard(state.lastPlayed, modifier)
    }
}

@Composable
private fun Greeting(state: HomeState) {
    val navigator = LocalNavigator.current
    val hour = remember { LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> Res.string.home_greeting_morning
        in 12..17 -> Res.string.home_greeting_day
        in 18..22 -> Res.string.home_greeting_evening
        else -> Res.string.home_greeting_night
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(greeting), style = MaterialTheme.typography.displaySmallEmphasized)
            Text(stringResource(Res.string.home_ready), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val account = state.account
        AssistChip(
            onClick = { navigator.selectTop(TopLevel.Accounts) },
            label = { Text(if (account != null) stringResource(Res.string.home_play_as, account.username) else stringResource(Res.string.home_add_account)) },
            leadingIcon = {
                if (account != null) app.youlauncher.ui.components.AccountHead(account, size = 20.dp) else Icon(Symbols.PersonAdd, contentDescription = null)
            },
            shape = MaterialTheme.shapes.extraLarge,
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = null,
            modifier = Modifier.heightIn(min = 40.dp),
        )
    }
}

@Composable
private fun ContinueCard(state: HomeState, instance: Instance, vm: HomeViewModel, modifier: Modifier) {
    val navigator = LocalNavigator.current
    val graph = LocalAppGraph.current
    val time = rememberAmbientTime()
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 300.dp),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
            InstanceCover(instance, Modifier.matchParentSize(), time = time, blur = 40.dp)
            // Затемнение снизу, чтобы текст читался на любой сцене.
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                        1f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    ),
                ),
            )
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)) {
                        Text(
                            stringResource(Res.string.home_continue),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TIconButton(Symbols.OpenInFull, stringResource(Res.string.home_open_instance), { navigator.navigate(Screen.Instance(instance.id)) })
                    TIconButton(Symbols.FolderOpen, stringResource(Res.string.shortcut_folder), { Platform.openFolder(graph.instances.gameDir(instance.id)) })
                }
                Spacer(Modifier.height(48.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InstanceBadge(instance.icon, instance.gameVersion, size = 64.dp, accent = instance.accent)
                    Column(Modifier.weight(1f)) {
                        Text(instance.name, style = MaterialTheme.typography.headlineLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val played = if (instance.lastPlayedAt != null) timeAgo(instance.lastPlayedAt) else stringResource(Res.string.home_not_played)
                        Text(
                            "${instance.gameVersion} · ${instance.loader.title} · $played",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    PlaySplitButton(
                        state = state.launch,
                        onPlay = vm::play,
                        onStop = { vm.stop() },
                        instances = state.instances,
                        selectedId = instance.id,
                        onSelect = vm::select,
                    )
                    LaunchStatus(
                        state.launch,
                        onOpenLog = { navigator.selectTop(TopLevel.Console) },
                        onDismiss = { vm.dismiss() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCarousel(instances: List<Instance>, modifier: Modifier) {
    val navigator = LocalNavigator.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(Res.string.home_recent)) {
            TIconButton(Symbols.GridView, stringResource(TopLevel.Library.label), { navigator.selectTop(TopLevel.Library) })
        }
        val carousel = rememberCarouselState { instances.size }
        HorizontalMultiBrowseCarousel(
            state = carousel,
            preferredItemWidth = 260.dp,
            itemSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) { index ->
            val inst = instances[index]
            Card(
                onClick = { navigator.navigate(Screen.Instance(inst.id)) },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize().maskClip(MaterialTheme.shapes.extraLarge),
            ) {
                Box(Modifier.fillMaxSize()) {
                    InstanceCover(inst, Modifier.matchParentSize())
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0.3f to Color.Transparent, 1f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f)),
                        ),
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(
                            inst.name,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = onScrimColor(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${inst.gameVersion} · ${inst.loader.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = onScrimColor().copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            content()
        }
    }
}

@Composable
private fun StatsCard(stats: HomeStats, modifier: Modifier) {
    WidgetCard(stringResource(Res.string.home_stats), modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(Res.string.home_stats_playtime), duration(stats.totalPlayMs), Symbols.Schedule, Modifier.weight(1f))
            StatTile(stringResource(Res.string.home_stats_launches), stats.launches.toString(), Symbols.RocketLaunch, Modifier.weight(1f))
            StatTile(stringResource(Res.string.home_stats_instances), stats.instances.toString(), Symbols.GridView, Modifier.weight(1f))
        }
        Text(stringResource(Res.string.home_stats_last14), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BarChart(stats.last14, Modifier.fillMaxWidth().height(96.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Столбики по дням, вырастают на пружине при появлении. */
@Composable
fun BarChart(values: List<Int>, modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(values) { grow.animateTo(1f, spec) }
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(modifier) {
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = (size.height * v / max * grow.value).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (v == 0) empty else bar,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2, w / 2),
            )
        }
    }
}

@Composable
private fun ShortcutsCard(state: HomeState, modifier: Modifier) {
    val navigator = LocalNavigator.current
    val ui = LocalAppUi.current
    val graph = LocalAppGraph.current
    WidgetCard(stringResource(Res.string.home_shortcuts), modifier) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { navigator.selectTop(TopLevel.Library); ui.pendingAction = "newInstance" }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Add, stringResource(Res.string.shortcut_new_instance))
            }
            FilledTonalButton(onClick = { navigator.selectTop(TopLevel.Mods) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Extension, stringResource(Res.string.shortcut_mods))
            }
            FilledTonalButton(onClick = { navigator.selectTop(TopLevel.Console) }, shapes = ButtonDefaults.shapes()) {
                IconLabel(Symbols.Terminal, stringResource(Res.string.shortcut_console))
            }
            FilledTonalButton(
                onClick = { state.selected?.let { Platform.openFolder(graph.instances.gameDir(it.id)) } },
                shapes = ButtonDefaults.shapes(),
                enabled = state.selected != null,
            ) {
                IconLabel(Symbols.FolderOpen, stringResource(Res.string.shortcut_folder))
            }
        }
    }
}

@Composable
private fun LastSessionCard(last: Instance?, modifier: Modifier) {
    WidgetCard(stringResource(Res.string.home_last_session), modifier) {
        if (last == null) {
            Text(stringResource(Res.string.home_last_session_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InstanceBadge(last.icon, last.gameVersion, size = 40.dp, accent = last.accent)
                Text(
                    stringResource(Res.string.home_last_session_body, duration(last.lastSessionMs), last.name, timeAgo(last.lastPlayedAt)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

