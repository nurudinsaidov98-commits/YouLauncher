package app.youlauncher.tools

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.youlauncher.AppGraph
import app.youlauncher.ui.theme.toSeedLong
import app.youlauncher.core.AppDirs
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.ui.App
import app.youlauncher.ui.AppUiState
import app.youlauncher.ui.navigation.InstanceTab
import app.youlauncher.ui.navigation.Navigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.navigation.TopLevel
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Скриншоты экранов без открытия окна: приложение рендерится в изображение через ImageComposeScene.
 * Аргументы: каталог вывода, затем необязательный фильтр имён экранов.
 */
fun main(args: Array<String>) {
    val out = Path.of(args[0]).createDirectories()
    val filter = args.drop(1).toSet()
    val width = System.getProperty("youlauncher.shot.width")?.toInt() ?: 1440
    val height = System.getProperty("youlauncher.shot.height")?.toInt() ?: 900
    val language = System.getProperty("youlauncher.shot.lang")?.let { app.youlauncher.core.settings.AppLanguage.valueOf(it) }

    val home = Path.of(System.getProperty("youlauncher.home") ?: "build/screens-home")
    val graph = AppGraph.create(AppDirs(home).ensure())
    val shots = ScreenShots.all().filter { filter.isEmpty() || it.name in filter }

    for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
        graph.settings.update { it.copy(theme = it.theme.copy(mode = mode)) }
        for (shot in shots) {
            val navigator = Navigator(shot.screen)
            val ui = AppUiState().apply { skipSplash = shot.name != "splash" }
            graph.instances.instances.value.forEach { graph.launcher.stop(it.id) }
            graph.settings.update { it.copy(background = app.youlauncher.core.settings.BackgroundSettings(), theme = it.theme.copy(colorsFromWallpaper = false, colorOverrides = emptyMap()), lastInstanceId = "demo-survival", language = language ?: it.language) }
            shot.prepare(graph, ui)
            val scene = ImageComposeScene(width, height, Density(1f)) { App(graph, navigator, ui) }
            var image = scene.render(0)
            // Даём загрузиться ресурсам (шрифты, строки) и доиграть анимациям появления.
            for (frame in 1..shot.frames) {
                Thread.sleep(16)
                image = scene.render(frame * 16_666_667L)
            }
            scene.close()
            val file = out.resolve("${shot.name}-${mode.name.lowercase()}.png")
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("Снимок: $file")
        }
    }
    graph.shutdown()
    kotlin.system.exitProcess(0)
}

class ScreenShot(
    val name: String,
    val screen: Screen,
    val frames: Int = 90,
    val prepare: (AppGraph, AppUiState) -> Unit = { _, _ -> },
)

object ScreenShots {
    fun all(): List<ScreenShot> = TopLevel.entries.map { ScreenShot(it.name.lowercase(), Screen.of(it)) } + listOf(
        ScreenShot("palette", Screen.Home, prepare = { _, ui -> ui.paletteOpen = true }),
        ScreenShot("splash", Screen.Home, frames = 30),
        ScreenShot("profile", Screen.Home, frames = 90, prepare = { _, ui -> ui.pendingAction = "profile" }),
        ScreenShot("instance-overview", Screen.Instance("demo-survival")),
        ScreenShot("instance-mods", Screen.Instance("demo-survival", InstanceTab.Mods)),
        ScreenShot("instance-worlds", Screen.Instance("demo-survival", InstanceTab.Worlds)),
        ScreenShot("instance-screenshots", Screen.Instance("demo-survival", InstanceTab.Screenshots)),
        ScreenShot("instance-settings", Screen.Instance("demo-survival", InstanceTab.Settings)),
        ScreenShot("project", Screen.ModProject("demo-mod-0", "Modrinth", "demo-survival"), frames = 120),
        ScreenShot("wizard", Screen.Library, frames = 120, prepare = { _, ui -> ui.pendingAction = "newInstance" }),
        ScreenShot("console-log", Screen.Console, frames = 60, prepare = { g, _ ->
            g.launcher.launch("demo-survival")
            Thread.sleep(4500)
        }),
        ScreenShot("settings-background", Screen.Settings, frames = 120, prepare = { g, ui ->
            g.settings.update { it.copy(background = it.background.copy(kind = app.youlauncher.core.settings.BackgroundKind.Wallpaper, wallpaper = "builtin:dawn-blobs")) }
            ui.pendingAction = "settings:Background"
        }),
        ScreenShot("settings-network", Screen.Settings, frames = 120, prepare = { _, ui -> ui.pendingAction = "settings:Network" }),
        ScreenShot("accounts-microsoft", Screen.Accounts, frames = 90, prepare = { _, ui -> ui.pendingAction = "signInMicrosoft" }),
        ScreenShot("settings-java", Screen.Settings, frames = 150, prepare = { _, ui -> ui.pendingAction = "settings:Java" }),
        ScreenShot("settings-about", Screen.Settings, frames = 150, prepare = { _, ui -> ui.pendingAction = "settings:About" }),
        ScreenShot("settings-colors", Screen.Settings, frames = 120, prepare = { g, ui ->
            // Пример своих цветов: основной и вторичный контейнер из готовых рядов M3.
            val sunset = app.youlauncher.ui.theme.ColorOverrides.tonalRow(app.youlauncher.ui.theme.SeedColors[2].color)
            g.settings.update { it.copy(theme = it.theme.copy(colorOverrides = mapOf(
                "primary" to sunset[3].toSeedLong(),
                "secondaryContainer" to sunset[7].toSeedLong(),
            ))) }
            ui.pendingAction = "settings:Colors"
        }),
        ScreenShot("home-wallpaper", Screen.Home, prepare = { g, _ ->
            g.settings.update { it.copy(theme = it.theme.copy(colorsFromWallpaper = true), background = it.background.copy(kind = app.youlauncher.core.settings.BackgroundKind.Wallpaper, wallpaper = "builtin:dawn-blobs")) }
        }),
        ScreenShot("home-live-waves", Screen.Home, prepare = { g, _ ->
            g.settings.update { it.copy(theme = it.theme.copy(colorsFromWallpaper = false), background = it.background.copy(kind = app.youlauncher.core.settings.BackgroundKind.Live, live = app.youlauncher.core.settings.LiveBackground.Waves)) }
        }),
        ScreenShot("home-launching", Screen.Home, frames = 40, prepare = { g, _ ->
            g.launcher.stop("demo-survival")
            g.settings.update { it.copy(lastInstanceId = "demo-tech") }
            g.launcher.launch("demo-tech")
            Thread.sleep(1500)
        }),
    )
}
