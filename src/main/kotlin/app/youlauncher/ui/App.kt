package app.youlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.youlauncher.AppGraph
import app.youlauncher.core.settings.NavLayout
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_play
import app.youlauncher.resources.nav_collapse
import app.youlauncher.resources.nav_expand
import app.youlauncher.ui.components.LocalUiDensity
import app.youlauncher.ui.background.AppBackground
import app.youlauncher.ui.background.Atmosphere
import app.youlauncher.ui.background.AtmosphereResolver
import app.youlauncher.ui.background.ParallaxState
import app.youlauncher.ui.background.WallpaperColors
import app.youlauncher.ui.background.trackParallax
import androidx.compose.foundation.background
import androidx.compose.runtime.produceState
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.i18n.AppLocale
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.images.LocalImageLoader
import app.youlauncher.ui.navigation.LocalNavAnimatedScope
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.LocalSharedTransitionScope
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.palette.CommandPalette
import app.youlauncher.ui.screens.accounts.AccountsScreen
import app.youlauncher.ui.screens.console.ConsoleScreen
import app.youlauncher.ui.screens.home.HomeScreen
import app.youlauncher.ui.screens.instance.InstanceScreen
import app.youlauncher.ui.screens.library.LibraryScreen
import app.youlauncher.ui.screens.mods.ModsScreen
import app.youlauncher.ui.screens.mods.ProjectScreen
import app.youlauncher.ui.screens.settings.SettingsScreen
import app.youlauncher.ui.theme.YouLauncherTheme
import app.youlauncher.ui.window.LocalWindowController
import app.youlauncher.ui.window.ResizeHandles
import app.youlauncher.ui.window.TitleBar
import org.jetbrains.compose.resources.stringResource

/** Состояние, общее для всего окна: открыта ли палитра команд, отложенные действия горячих клавиш. */
class AppUiState {
    var paletteOpen by mutableStateOf(false)

    /** Для скриншотов и тестов заставка не нужна. */
    var skipSplash = false

    /** Прокрутка настроек живёт здесь: при смене языка дерево интерфейса пересоздаётся, а место в настройках должно остаться. */
    val settingsList = androidx.compose.foundation.lazy.LazyListState()

    /** Каталог модов по целевому экземпляру: поиск, фильтры и прокрутка переживают переход на страницу мода. */
    val modsMemory = HashMap<String?, app.youlauncher.ui.screens.mods.ModsMemory>()

    /** Версия, выбранная в мастере создания: фон показывает её атмосферу. */
    var previewVersion by mutableStateOf<String?>(null)

    val parallax = ParallaxState()

    /** Идёт запись нового сочетания клавиш: окно не выполняет действия по клавишам. */
    var recordingHotkey by mutableStateOf(false)

    /** Действие горячей клавиши, которое должен подхватить экран (например "play" или "newInstance"). */
    var pendingAction by mutableStateOf<String?>(null)

    fun consume(action: String): Boolean {
        if (pendingAction != action) return false
        pendingAction = null
        return true
    }
}

val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("AppGraph не передан") }
val LocalAppUi = staticCompositionLocalOf { AppUiState() }

@Composable
fun App(graph: AppGraph, navigator: Navigator, ui: AppUiState) {
    val settings by graph.settings.settings.collectAsState()
    val backStack by navigator.backStack.collectAsState()
    val instances by graph.instances.instances.collectAsState()
    val atmosphere = remember(settings.background, settings.theme.colorsFromWallpaper, backStack.last(), instances, ui.previewVersion, settings.lastInstanceId) {
        AtmosphereResolver.resolve(settings.background, settings.theme.colorsFromWallpaper, backStack.last(), instances, ui.previewVersion, settings.lastInstanceId)
    }
    val extracted by produceState<androidx.compose.ui.graphics.Color?>(null, atmosphere.extractSeedFrom) {
        value = atmosphere.extractSeedFrom?.let { runCatching { WallpaperColors.extract(graph.images, it.path) }.getOrNull() }
    }
    AppLocale.Provide(settings.language) {
        YouLauncherTheme(settings.theme, settings.ui, seedOverride = atmosphere.seedOverride ?: extracted) {
            val scheme = MaterialTheme.colorScheme
            CompositionLocalProvider(
                LocalAppGraph provides graph,
                LocalNavigator provides navigator,
                LocalAppUi provides ui,
                LocalImageLoader provides graph.images,
                LocalUiDensity provides settings.ui.density,
                LocalScrollbarStyle provides ScrollbarStyle(
                    minimalHeight = 32.dp,
                    thickness = 6.dp,
                    shape = MaterialTheme.shapes.extraLarge,
                    hoverDurationMillis = 200,
                    unhoverColor = scheme.outlineVariant,
                    hoverColor = scheme.outline,
                ),
            ) {
                var ready by remember { mutableStateOf(ui.skipSplash || settings.ui.motion == app.youlauncher.core.settings.MotionLevel.Off) }
                LaunchedEffect(Unit) {
                    // Короткая заставка, пока прогреваются шрифты и данные.
                    kotlinx.coroutines.delay(900)
                    ready = true
                    // Заставка один раз за запуск: при смене языка интерфейс пересоздаётся, но заставку не повторяет.
                    ui.skipSplash = true
                }
                val effectsIn = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
                val effectsOut = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(ready, transitionSpec = { fadeIn(effectsIn) togetherWith fadeOut(effectsOut) }, label = "splash") { r ->
                    if (r) AppFrame(graph, navigator, ui, atmosphere) else SplashScreen()
                }
            }
        }
    }
}

@Composable
private fun AppFrame(graph: AppGraph, navigator: Navigator, ui: AppUiState, atmosphere: Atmosphere) {
    val settings by graph.settings.settings.collectAsState()
    val controller = LocalWindowController.current
    val snackbars = remember { SnackbarHostState() }
    // Звуки интерфейса (если включены): сообщения и начало игры.
    MessengerEffect(graph.messenger, snackbars) { m ->
        if (graph.settings.settings.value.ui.sounds) UiSounds.play(if (m.error) UiSound.Error else UiSound.Success)
    }
    LaunchedEffect(graph) {
        var running = emptySet<String>()
        graph.launcher.states.collect { states ->
            val now = states.filterValues { it is app.youlauncher.core.repo.LaunchState.Running }.keys
            if ((now - running).isNotEmpty() && graph.settings.settings.value.ui.sounds) UiSounds.play(UiSound.Launch)
            running = now
        }
    }
    val bg = settings.background
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).trackParallax(ui.parallax, bg.parallax)) {
        AppBackground(atmosphere, ui.parallax, parallaxEnabled = bg.parallax, saver = bg.saver, modifier = Modifier.fillMaxSize())
        // Затемнение под «хромом» (заголовок и навигация), чтобы текст читался на любом фоне.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = (0.35f + 0.6f * atmosphere.scrim).coerceIn(0f, 1f))))
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(
                    onOpenPalette = { ui.paletteOpen = true },
                    shortcutLabel = settings.ui.hotkeys["palette"] ?: "Ctrl+K",
                    leading = { app.youlauncher.ui.screens.profile.ProfileButton() },
                )
                val backStack by navigator.backStack.collectAsState()
                val current = backStack.last()
                if (settings.ui.navigation == NavLayout.Side) {
                    Row(Modifier.weight(1f)) {
                        SideNavigation(current.top, settings.ui.railExpanded, onExpandedChange = { expanded ->
                            graph.settings.update { it.copy(ui = it.ui.copy(railExpanded = expanded)) }
                        }, onPlay = { ui.pendingAction = "play"; navigator.selectTop(TopLevel.Home) }) { navigator.selectTop(it) }
                        ContentHost(current, atmosphere.scrim, Modifier.weight(1f).padding(end = 12.dp, bottom = 12.dp))
                    }
                } else {
                    ContentHost(current, atmosphere.scrim, Modifier.weight(1f).padding(horizontal = 12.dp))
                    BottomNavigation(current.top) { navigator.selectTop(it) }
                }
            }
            SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { data ->
                Snackbar(data, shape = MaterialTheme.shapes.large)
            }
            ResizeHandles(controller)
        }
    }
    }
    if (ui.paletteOpen) {
        CommandPalette(onDismiss = { ui.paletteOpen = false })
    }
}

@Composable
private fun SideNavigation(
    current: TopLevel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onSelect: (TopLevel) -> Unit,
) {
    val state = rememberWideNavigationRailState(if (expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed)
    LaunchedEffect(expanded) { if (expanded) state.expand() else state.collapse() }
    val railExpanded = state.targetValue == WideNavigationRailValue.Expanded
    WideNavigationRail(
        state = state,
        colors = WideNavigationRailDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        arrangement = Arrangement.spacedBy(0.dp),
        header = {
            Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TIconButton(
                    icon = if (railExpanded) Symbols.ViewSidebarFilled else Symbols.ViewSidebar,
                    contentDescription = stringResource(if (railExpanded) Res.string.nav_collapse else Res.string.nav_expand),
                    onClick = { onExpandedChange(!railExpanded) },
                )
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(Res.string.action_play)) },
                    icon = { Icon(Symbols.PlayArrowFilled, contentDescription = null) },
                    onClick = onPlay,
                    expanded = railExpanded,
                )
            }
        },
    ) {
        TopLevel.entries.forEach { top ->
            val selected = top == current
            WideNavigationRailItem(
                selected = selected,
                onClick = { onSelect(top) },
                icon = { Icon(if (selected) top.selectedIcon() else top.icon(), contentDescription = null) },
                label = { Text(stringResource(top.label)) },
                railExpanded = railExpanded,
            )
        }
    }
}

@Composable
private fun BottomNavigation(current: TopLevel, onSelect: (TopLevel) -> Unit) {
    ShortNavigationBar(containerColor = androidx.compose.ui.graphics.Color.Transparent) {
        TopLevel.entries.forEach { top ->
            val selected = top == current
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onSelect(top) },
                icon = { Icon(if (selected) top.selectedIcon() else top.icon(), contentDescription = null) },
                label = { Text(stringResource(top.label)) },
            )
        }
    }
}

private fun Screen.contentKey(): String = when (this) {
    is Screen.Instance -> "instance:$id"
    is Screen.ModProject -> "project:$projectId"
    is Screen.Mods -> "mods"
    else -> this::class.simpleName.orEmpty()
}

@Composable
private fun ContentHost(current: Screen, scrim: Float, modifier: Modifier = Modifier) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val spatialScale = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface.copy(alpha = (0.84f + 0.16f * scrim).coerceIn(0f, 1f)),
        // Цвет полупрозрачный, contentColorFor его не узнает: цвет текста задаётся явно.
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedContent(
                    targetState = current,
                    contentKey = { it.contentKey() },
                    transitionSpec = {
                        val nested = targetState is Screen.Instance || targetState is Screen.ModProject
                        // Вложенный экран «вырастает», соседний въезжает снизу; пружина темы даёт отскок.
                        if (nested) {
                            (fadeIn(effectsIn) + scaleIn(spatialScale, initialScale = 0.92f)) togetherWith
                                (fadeOut(effectsOut) + scaleOut(spatialScale, targetScale = 1.03f))
                        } else {
                            (fadeIn(effectsIn) + slideInVertically(spatial) { it / 10 } + scaleIn(spatialScale, initialScale = 0.97f)) togetherWith fadeOut(effectsOut)
                        }
                    },
                    contentAlignment = Alignment.TopStart,
                    label = "screens",
                ) { screen ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        when (screen) {
                            Screen.Home -> HomeScreen()
                            Screen.Library -> LibraryScreen()
                            is Screen.Instance -> InstanceScreen(screen)
                            is Screen.Mods -> ModsScreen(screen)
                            is Screen.ModProject -> ProjectScreen(screen)
                            Screen.Accounts -> AccountsScreen()
                            Screen.Console -> ConsoleScreen()
                            Screen.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
