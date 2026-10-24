package app.youlauncher.ui

import androidx.compose.runtime.withFrameNanos
import app.youlauncher.ui.navigation.InstanceTab
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import kotlinx.coroutines.delay
import java.nio.file.Files
import java.nio.file.Path

/**
 * Замер отзывчивости для разработки: переходит по экранам и для каждого перехода пишет паузу до первого кадра
 * и самый долгий кадр за 1,5 с. Холодный круг сразу после запуска, затем тёплый. Включается только системным
 * свойством youlauncher.benchNavigation=<файл отчёта> (обычный запуск его не задаёт).
 */
object NavigationBench {
    val reportFile: Path? get() = System.getProperty("youlauncher.benchNavigation")?.let { Path.of(it) }

    suspend fun run(navigator: Navigator, instanceId: String?, report: (String) -> Unit) {
        delay(2500)
        val screens = buildList {
            add(Screen.of(TopLevel.Library))
            if (instanceId != null) {
                add(Screen.Instance(instanceId))
                add(Screen.Instance(instanceId, InstanceTab.Mods))
                add(Screen.Instance(instanceId, InstanceTab.Settings))
            }
            add(Screen.Mods(null)); add(Screen.of(TopLevel.Accounts)); add(Screen.of(TopLevel.Console)); add(Screen.Settings); add(Screen.Home)
        }
        for (round in listOf("cold", "warm")) {
            for (s in screens) {
                val gcBefore = gcMillis()
                val start = System.nanoTime()
                if (s is Screen.Instance) navigator.navigate(s) else navigator.selectTop(s.top)
                var last = withFrameNanos { it }
                val first = (System.nanoTime() - start) / 1e6
                var worst = 0.0
                val until = System.nanoTime() + 1_500_000_000L
                while (System.nanoTime() < until) {
                    val now = withFrameNanos { it }
                    worst = maxOf(worst, (now - last) / 1e6)
                    last = now
                }
                report("%-5s %-34s first %5.0f ms, worst %5.0f ms, gc %4d ms".format(round, s.toString().take(34), first, worst, gcMillis() - gcBefore))
            }
        }
    }

    private fun gcMillis(): Long = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().sumOf { it.collectionTime.coerceAtLeast(0) }

    fun writer(file: Path): (String) -> Unit {
        Files.deleteIfExists(file)
        return { line -> Files.writeString(file, line + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND) }
    }
}
