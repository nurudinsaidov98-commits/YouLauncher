package app.youlauncher.data.system

import app.youlauncher.core.maskSecrets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Отчёт о сбое самого лаунчера (не игры): logs/launcher-crash-<время>.txt без секретов. */
class LauncherCrash(private val logsDir: Path, private val version: String) {
    fun text(error: Throwable, where: String): String = buildString {
        appendLine("YouLauncher $version: сбой лаунчера ($where)")
        appendLine("Время: ${LocalDateTime.now().withNano(0)}")
        appendLine("Система: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        appendLine()
        append(maskSecrets(error.stackTraceToString()))
    }

    fun write(error: Throwable, where: String): Path? = runCatching {
        Files.createDirectories(logsDir)
        val file = logsDir.resolve("launcher-crash-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt")
        Files.writeString(file, text(error, where))
        file
    }.getOrNull()
}
