package app.youlauncher.ui.screens.settings

import app.youlauncher.ui.Platform
import java.nio.file.Files

object AppInfo {
    /** Версия сборки (из ресурса, который пишет Gradle). */
    val version: String get() = app.youlauncher.core.BuildInfo.version

    /**
     * Форма баг-репорта на GitHub с уже заполненными версией лаунчера и Windows. Репозиторий — тот, откуда
     * приходят обновления этой сборки.
     */
    fun issueUrl(): String {
        val repo = app.youlauncher.core.BuildInfo.updateRepo ?: "nurudinsaidov98-commits/YouLauncher"
        fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")}"
        return "https://github.com/$repo/issues/new?template=bug_report.yml&version=${enc(version)}&os=${enc(os)}"
    }

    fun reportIssue() = Platform.openUrl(issueUrl())

    /** Открывает NOTICE со списком лицензий: из папки приложения или из ресурсов, распакованных во временный файл. */
    fun openLicenses() {
        val text = AppInfo::class.java.getResourceAsStream("/NOTICE.txt")?.use { it.readBytes().decodeToString() } ?: return
        val file = Files.createTempFile("youlauncher-notice", ".txt")
        Files.writeString(file, text)
        runCatching { java.awt.Desktop.getDesktop().open(file.toFile()) }.onFailure { Platform.openFolder(file.parent) }
    }
}
