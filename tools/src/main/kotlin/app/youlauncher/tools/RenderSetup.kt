package app.youlauncher.tools

import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import app.youlauncher.core.settings.AppLanguage
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.data.system.DriveInfo
import app.youlauncher.data.system.RegistryStore
import app.youlauncher.data.system.ShortcutMaker
import app.youlauncher.data.system.ShortcutPlace
import app.youlauncher.ui.i18n.AppLocale
import app.youlauncher.ui.setup.SetupModel
import app.youlauncher.ui.setup.SetupScreen
import app.youlauncher.ui.setup.SetupStep
import app.youlauncher.ui.setup.UninstallScreen
import app.youlauncher.ui.theme.YouLauncherTheme
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/** Снимки шагов установщика и удаления (светлая и тёмная тема) в docs/screens/setup. Реестр и ярлыки не трогаются. */
fun main(args: Array<String>) {
    val out = Path.of(args[0]).createDirectories()
    val language = System.getProperty("youlauncher.shot.lang")?.let { AppLanguage.valueOf(it) } ?: AppLanguage.System
    val source = Files.createTempDirectory("setup-src").also { Files.writeString(it.resolve("YouLauncher.exe"), "x".repeat(4096)) }
    val noRegistry = object : RegistryStore {
        override fun read(key: String, name: String): String? = null
        override fun write(key: String, values: Map<String, Any>) = Unit
        override fun delete(key: String) = Unit
    }
    val noShortcuts = object : ShortcutMaker {
        override fun folder(place: ShortcutPlace): Path? = null
        override fun create(folder: Path, name: String, target: Path, arguments: String) = true
    }
    // Демо-диски и пути: в снимках для README не должно быть дисков и имени пользователя этого компьютера.
    val gb = 1024L * 1024 * 1024
    val demoDrives = listOf(DriveInfo(Path.of("C:\\"), 476 * gb, 212 * gb), DriveInfo(Path.of("D:\\"), 931 * gb, 604 * gb))
    // На снимке выбран диск D: так видна галочка «Хранить игры и данные в этой папке».
    val demoLocation = Path.of("D:/YouLauncher")
    val demoData = Path.of("C:\\Users\\Player\\AppData\\Roaming\\YouLauncher")
    val shots = SetupStep.entries.map { it.name.lowercase() to it } + listOf("uninstall" to null, "uninstall-done" to null)
    for (mode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
        for ((name, step) in shots) {
            val scene = ImageComposeScene(1040, 660, Density(1f)) {
                AppLocale.Provide(language) {
                    YouLauncherTheme(ThemeSettings(mode = mode), UiSettings()) {
                        if (step == null) UninstallScreen(source, {}, {}, startStage = if (name == "uninstall-done") 2 else 0, dataDir = demoData)
                        else {
                            val model = remember { SetupModel(source, "1.2.2", noRegistry, noShortcuts, demoDrives, 238L * 1024 * 1024, demoLocation, check = { null }, appData = Path.of("C:/Users/Player/AppData/Roaming/YouLauncher")).apply { this.step = step; progress = 0.62f; currentFile = "skiko-windows-x64.dll" } }
                            SetupScreen(model, {}, {})
                        }
                    }
                }
            }
            var image = scene.render(0)
            for (frame in 1..150) { Thread.sleep(8); image = scene.render(frame * 16_666_667L) }
            scene.close()
            val file = out.resolve("$name-${mode.name.lowercase()}.png")
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("Снимок: $file")
        }
    }
    kotlin.system.exitProcess(0)
}
