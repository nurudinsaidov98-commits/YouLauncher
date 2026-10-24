package app.youlauncher.tools

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.ui.App
import app.youlauncher.ui.AppUiState
import app.youlauncher.ui.NavigationBench
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.Screen
import java.nio.file.Path

/** Переходы между экранами в настоящем окне из исходников (см. [NavigationBench]); для собранной программы — свойство youlauncher.benchNavigation. */
fun main() {
    val graph = AppGraph.create(AppDirs(Path.of(System.getProperty("youlauncher.home"))).ensure())
    val ui = AppUiState().apply { skipSplash = true }
    val navigator = Navigator(Screen.Home)
    application {
        Window(onCloseRequest = ::exitApplication, state = rememberWindowState(width = 1280.dp, height = 820.dp), title = "bench", undecorated = true) {
            App(graph, navigator, ui)
            LaunchedEffect(Unit) {
                NavigationBench.run(navigator, graph.instances.instances.value.firstOrNull()?.id, ::println)
                graph.shutdown()
                exitApplication()
            }
        }
    }
}
