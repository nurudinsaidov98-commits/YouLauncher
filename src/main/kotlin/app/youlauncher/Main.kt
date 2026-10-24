package app.youlauncher

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import app.youlauncher.data.system.LauncherCrash
import app.youlauncher.ui.CrashScreen
import app.youlauncher.ui.screens.settings.AppInfo
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.youlauncher.ui.brand.LogoPainter
import app.youlauncher.ui.brand.brandLogoColors
import app.youlauncher.ui.App
import app.youlauncher.ui.AppUiState
import app.youlauncher.ui.Hotkey
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.TopLevel
import app.youlauncher.ui.window.LocalWindowController
import app.youlauncher.ui.window.WindowController
import app.youlauncher.ui.window.WindowsDwm
import java.awt.Dimension

@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    System.setProperty("file.encoding", "UTF-8")
    val graph = AppGraph.create()
    Runtime.getRuntime().addShutdownHook(Thread { graph.shutdown() })
    val crashes = LauncherCrash(graph.dirs.logsDir, AppInfo.version)
    // Ошибки в фоновых потоках не роняют окно, но попадают в отчёт.
    Thread.setDefaultUncaughtExceptionHandler { t, e -> crashes.write(e, "поток ${t.name}") }
    // Шрифты из файла: одна копия в памяти на все начертания (см. FontFiles).
    app.youlauncher.ui.theme.FontFiles.init(graph.dirs.root.resolve("fonts"))
    app.youlauncher.ui.theme.FontFiles.flex
    app.youlauncher.ui.theme.FontFiles.code
    // Ярлык экземпляра (--launch <id>) запускает игру сразу; --open открывает нужный экран.
    val launchArgs = app.youlauncher.ui.navigation.LaunchArgs.parse(args)
    launchArgs.launchInstance?.let { id -> if (graph.instances.instances.value.any { it.id == id }) graph.launcher.launch(id) }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 820.dp, position = WindowPosition.PlatformDefault)
        var navigator by remember { mutableStateOf(Navigator(launchArgs.screen ?: app.youlauncher.ui.navigation.Screen.Home)) }
        var ui by remember { mutableStateOf(AppUiState().apply { pendingAction = launchArgs.action }) }
        var crash by remember { mutableStateOf<Pair<String, java.nio.file.Path?>?>(null) }
        var crashCount by remember { mutableStateOf(0) }
        // Сбой в интерфейсе: окно не закрывается, вместо него показывается экран ошибки с отчётом.
        val handler = remember {
            WindowExceptionHandlerFactory { _ ->
                WindowExceptionHandler { e ->
                    crashCount++
                    crash = crashes.text(e, "интерфейс") to crashes.write(e, "интерфейс")
                }
            }
        }
        CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides handler) {
        Window(
            onCloseRequest = {
                graph.shutdown()
                exitApplication()
            },
            state = windowState,
            title = "YouLauncher",
            icon = remember { LogoPainter(brandLogoColors()) },
            undecorated = true,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                // Во время записи нового сочетания клавиши получает редактор, а не действия.
                if (ui.recordingHotkey) return@Window false
                val hotkeys = graph.settings.settings.value.ui.hotkeys
                val code = event.key.nativeKeyCode
                val action = hotkeys.entries.firstOrNull { (_, combo) ->
                    Hotkey.parse(combo)?.matches(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, code) == true
                }?.key ?: return@Window false
                when (action) {
                    "palette" -> ui.paletteOpen = !ui.paletteOpen
                    "home" -> navigator.selectTop(TopLevel.Home)
                    "library" -> navigator.selectTop(TopLevel.Library)
                    "mods" -> navigator.selectTop(TopLevel.Mods)
                    "accounts" -> navigator.selectTop(TopLevel.Accounts)
                    "console" -> navigator.selectTop(TopLevel.Console)
                    "settings" -> navigator.selectTop(TopLevel.Settings)
                    else -> ui.pendingAction = action
                }
                true
            },
        ) {
            val controller = remember(window) {
                WindowController(window) {
                    graph.shutdown()
                    exitApplication()
                }
            }
            LaunchedEffect(window) {
                window.minimumSize = Dimension(controller.minWidth, controller.minHeight)
                WindowsDwm.setRoundedCorners(window, rounded = true)
                graph.notifier.appInFocus = { window.isFocused }
                val mode = graph.settings.settings.value.ui.windowMode
                if (mode != app.youlauncher.core.settings.WindowMode.Normal) controller.applyMode(mode)
            }
            // Замер отзывчивости для разработки (youlauncher.benchNavigation), в обычном запуске не включается.
            app.youlauncher.ui.NavigationBench.reportFile?.let { file ->
                LaunchedEffect(Unit) {
                    ui.skipSplash = true
                    app.youlauncher.ui.NavigationBench.run(navigator, graph.instances.instances.value.firstOrNull()?.id, app.youlauncher.ui.NavigationBench.writer(file))
                    graph.shutdown()
                    exitApplication()
                }
            }
            // Свёрнутое окно отдаёт память: кэш картинок очищается, сборка мусора сжимает кучу и возвращает её системе.
            LaunchedEffect(windowState.isMinimized) {
                if (windowState.isMinimized) {
                    kotlinx.coroutines.delay(3_000)
                    graph.images.trim()
                    System.gc()
                }
            }
            CompositionLocalProvider(LocalWindowController provides controller) {
                val c = crash
                if (c == null) App(graph, navigator, ui)
                else CrashScreen(
                    settings = graph.settings.settings.value,
                    report = c.first,
                    reportFile = c.second,
                    // Третий сбой подряд: интерфейс снова не поднимаем, чтобы не зациклиться.
                    canRetry = crashCount < 3,
                    onRetry = {
                        navigator = Navigator()
                        ui = AppUiState().apply { skipSplash = true }
                        crash = null
                    },
                    onClose = {
                        graph.shutdown()
                        exitApplication()
                    },
                )
            }
        }
        }
    }
}
