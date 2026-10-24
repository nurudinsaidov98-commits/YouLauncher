package app.youlauncher.ui.screens.settings

import app.youlauncher.ui.Platform
import java.nio.file.Files

object AppInfo {
    /** Версия сборки (из ресурса, который пишет Gradle). */
    val version: String get() = app.youlauncher.core.BuildInfo.version

    /** Открывает NOTICE со списком лицензий: из папки приложения или из ресурсов, распакованных во временный файл. */
    fun openLicenses() {
        val text = AppInfo::class.java.getResourceAsStream("/NOTICE.txt")?.use { it.readBytes().decodeToString() } ?: return
        val file = Files.createTempFile("youlauncher-notice", ".txt")
        Files.writeString(file, text)
        runCatching { java.awt.Desktop.getDesktop().open(file.toFile()) }.onFailure { Platform.openFolder(file.parent) }
    }
}
