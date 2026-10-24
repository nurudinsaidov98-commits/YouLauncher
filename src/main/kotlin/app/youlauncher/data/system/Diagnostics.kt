package app.youlauncher.data.system

import app.youlauncher.core.AppDirs
import app.youlauncher.core.maskSecrets
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Отчёт для диагностики одним zip: система, настройки без путей и секретов, экземпляры,
 * журнал лаунчера и последние отчёты о сбоях. Все тексты проходят через маскировку токенов и паролей,
 * имя пользователя Windows в путях заменяется на «user».
 */
class Diagnostics(private val dirs: AppDirs, private val version: String) {
    fun systemText(): String = buildString {
        val rt = Runtime.getRuntime()
        appendLine("YouLauncher $version")
        appendLine("Время: ${LocalDateTime.now().withNano(0)}")
        appendLine("Система: ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("Java лаунчера: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
        appendLine("Процессоров: ${rt.availableProcessors()}, память лаунчера: ${rt.maxMemory() / 1_048_576} МБ")
        appendLine("Каталог данных: ${anonymize(dirs.root.toString())}")
        appendLine("Портативный режим: ${AppDirs.isPortable()}")
    }

    fun instancesText(instances: List<Instance>): String = instances.joinToString("\n") { i ->
        "${i.name} | ${i.gameVersion} | ${i.loader.title} ${i.loaderVersion.orEmpty()} | Java: ${if (i.java.javaPath != null) "своя" else "авто"} | память ${i.java.maxMemoryMb ?: "общая"} | запусков ${i.launches}"
    }

    /** Имя пользователя Windows в путях заменяется, чтобы отчёт можно было отправить без личных данных. */
    fun anonymize(text: String): String {
        val user = System.getProperty("user.name").orEmpty()
        val masked = maskSecrets(text)
        return if (user.length < 2) masked else masked.replace("\\Users\\$user", "\\Users\\user").replace("/Users/$user", "/Users/user")
    }

    suspend fun write(target: Path, settings: LauncherSettings, instances: List<Instance>, log: List<LogLine>): Path = withContext(Dispatchers.IO) {
        Files.createDirectories(target.toAbsolutePath().parent)
        ZipOutputStream(Files.newOutputStream(target)).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(anonymize(text).toByteArray())
                zip.closeEntry()
            }
            put("system.txt", systemText())
            put("settings.json", SettingsRepository.exportText(settings))
            put("instances.txt", instancesText(instances))
            put("launcher-log.txt", log.takeLast(3000).joinToString("\n") { "[${it.level}] ${it.instanceId?.let { id -> "[$id] " }.orEmpty()}${it.text}" })
            recent(dirs.logsDir, "launcher-crash-").forEach { put("crashes/${it.name}", runCatching { it.readText() }.getOrDefault("")) }
            recent(dirs.crashReportsDir, "").forEach { put("game-crashes/${it.name}", runCatching { it.readText() }.getOrDefault("")) }
        }
        target
    }

    private fun recent(dir: Path, prefix: String, limit: Int = 5): List<Path> =
        if (!dir.isDirectory()) emptyList()
        else dir.listDirectoryEntries().filter { it.name.startsWith(prefix) && Files.isRegularFile(it) }
            .sortedByDescending { it.getLastModifiedTime().toMillis() }.take(limit)
}
