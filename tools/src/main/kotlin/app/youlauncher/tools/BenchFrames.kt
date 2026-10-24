package app.youlauncher.tools

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.youlauncher.AppGraph
import app.youlauncher.core.AppDirs
import app.youlauncher.ui.App
import app.youlauncher.ui.AppUiState
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import java.nio.file.Path

/**
 * Замер времени кадра: экран рендерится в ImageComposeScene (программная отрисовка Skia, медленнее GPU,
 * но годится для сравнения до и после), сначала прогрев, затем 120 кадров. Печатает среднее и p95.
 */
fun main(args: Array<String>) {
    val home = Path.of(System.getProperty("youlauncher.home") ?: "build/screens-home")
    val graph = AppGraph.create(AppDirs(home).ensure())
    val screens = listOf(Screen.Home, Screen.of(TopLevel.Library), Screen.Mods(null), Screen.Settings)
    // Режимы для поиска узких мест: motion-off выключает анимации, blur-off снимает размытие фона.
    val filter = args.filterNot { it.contains('-') }.toSet()
    if ("motion-off" in args) graph.settings.update { it.copy(ui = it.ui.copy(motion = app.youlauncher.core.settings.MotionLevel.Off)) }
    if ("blur-off" in args) graph.settings.update { it.copy(background = it.background.copy(blur = 0f)) }
    for (screen in screens) {
        val name = screen::class.simpleName.orEmpty()
        if (filter.isNotEmpty() && name !in filter) continue
        val ui = AppUiState().apply { skipSplash = true }
        val scene = ImageComposeScene(1440, 900, Density(1f)) { App(graph, Navigator(screen), ui) }
        var t = 0L
        repeat(60) { t += 16_666_667L; scene.render(t) }
        // Курсор ходит по окну, чтобы работал параллакс, как у живого пользователя.
        val times = (0 until 120).map { i ->
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, androidx.compose.ui.geometry.Offset(200f + i * 8f, 300f + (i % 30) * 10f))
            t += 16_666_667L
            val s = System.nanoTime()
            scene.render(t)
            (System.nanoTime() - s) / 1e6
        }.sorted()
        scene.close()
        println("%-10s среднее %.1f мс, p95 %.1f мс".format(name, times.average(), times[(times.size * 0.95).toInt()]))
    }
    graph.shutdown()
    kotlin.system.exitProcess(0)
}
