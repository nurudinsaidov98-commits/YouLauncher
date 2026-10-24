package app.youlauncher.data.system

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.name

/** Диск для выбора папки установки. */
data class DriveInfo(val root: Path, val totalBytes: Long, val freeBytes: Long) {
    val label: String get() = root.toString().trimEnd('\\', '/')
}

/** Почему в выбранную папку нельзя установить. */
enum class LocationProblem { NotAbsolute, NotWritable, NotEnoughSpace, InsideSource, NotEmpty }

/** Хранилище записей реестра (HKCU) для раздела «Приложения» Windows; в тестах подменяется. */
interface RegistryStore {
    fun read(key: String, name: String): String?
    fun write(key: String, values: Map<String, Any>)
    fun delete(key: String)
}

/** Где создаются ярлыки. */
enum class ShortcutPlace(val specialFolder: String) { Desktop("Desktop"), StartMenu("Programs") }

/** Ярлыки Windows (.lnk); в тестах подменяется. */
interface ShortcutMaker {
    /** Настоящая папка (рабочий стол может быть перенесён, например в OneDrive). */
    fun folder(place: ShortcutPlace): Path?
    fun create(folder: Path, name: String, target: Path, arguments: String = ""): Boolean
}

/**
 * Установка YouLauncher для текущего пользователя (без прав администратора): копирование программы,
 * ярлыки, запись в «Приложения» Windows, удаление. Данные пользователя (%APPDATA%\YouLauncher) не трогаются,
 * если их не попросили удалить.
 */
object InstallPlan {
    const val APP_NAME = "YouLauncher"
    /** Мелкие диски (флешки, карты памяти) в выборе не показываются. */
    private const val MIN_DRIVE_BYTES = 2L * 1024 * 1024 * 1024
    const val UNINSTALL_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\YouLauncher"

    /** Файлы, которые не копируются из распакованной программы: кэш классов (пути в нём абсолютные) и данные. */
    private val skipped = setOf("youlauncher.jsa", "youlauncher.jsa.stamp", "portable.txt")
    private val skippedDirs = setOf("data")

    fun defaultLocation(env: (String) -> String? = System::getenv): Path {
        val local = env("LOCALAPPDATA") ?: (System.getProperty("user.home") + "\\AppData\\Local")
        return Path.of(local, "Programs", APP_NAME)
    }

    /** Папка по умолчанию на выбранном диске: на системном — личная папка программ, на остальных — корень диска. */
    fun locationOn(drive: DriveInfo, systemDrive: Path = defaultLocation().root): Path =
        if (drive.root == systemDrive) defaultLocation() else drive.root.resolve(APP_NAME)

    fun drives(): List<DriveInfo> = File.listRoots().mapNotNull { r ->
        runCatching { DriveInfo(r.toPath(), r.totalSpace, r.usableSpace) }.getOrNull()?.takeIf { it.totalBytes >= MIN_DRIVE_BYTES }
    }

    fun sizeOf(dir: Path): Long = runCatching {
        Files.walk(dir).use { s -> s.filter { Files.isRegularFile(it) }.mapToLong { Files.size(it) }.sum() }
    }.getOrDefault(0)

    /**
     * Проверка папки: путь полный, не внутри распакованного установщика, хватает места, можно писать.
     * Папка с чужими файлами не подходит; папка с прежней установкой YouLauncher подходит (обновление).
     */
    fun check(target: Path, source: Path, required: Long, free: (Path) -> Long = { freeSpace(it) }): LocationProblem? {
        if (!target.isAbsolute) return LocationProblem.NotAbsolute
        val t = target.normalize()
        if (t.startsWith(source.normalize())) return LocationProblem.InsideSource
        if (free(t) in 0 until required) return LocationProblem.NotEnoughSpace
        if (t.exists() && Files.isDirectory(t) && !isOurs(t) && Files.list(t).use { it.findAny().isPresent }) return LocationProblem.NotEmpty
        if (!canWrite(t)) return LocationProblem.NotWritable
        return null
    }

    /** В папке уже стоит YouLauncher (обновление поверх). */
    fun isOurs(dir: Path): Boolean = dir.resolve("$APP_NAME.exe").exists()

    private fun freeSpace(path: Path): Long {
        var p: Path? = path
        while (p != null && !p.exists()) p = p.parent
        return p?.toFile()?.usableSpace ?: -1
    }

    /** Пробная запись: создаёт папку (вместе с недостающими родительскими) и временный файл, затем убирает всё созданное. */
    fun canWrite(dir: Path): Boolean {
        var topCreated: Path? = null
        var p: Path? = dir
        while (p != null && !p.exists()) { topCreated = p; p = p.parent }
        return try {
            Files.createDirectories(dir)
            val probe = Files.createTempFile(dir, ".write", ".tmp")
            Files.delete(probe)
            true
        } catch (e: Exception) {
            false
        } finally {
            var d: Path? = dir
            while (topCreated != null && d != null) {
                runCatching { Files.deleteIfExists(d) }
                if (d == topCreated) break
                d = d.parent
            }
        }
    }

    /**
     * Копирует программу в папку установки с прогрессом (доля и имя файла). Прежние app и runtime
     * удаляются заранее: в новой версии другие jar-файлы, старые только занимали бы место.
     */
    fun copy(source: Path, target: Path, onProgress: (Float, String) -> Unit = { _, _ -> }) {
        val files = Files.walk(source).use { s ->
            s.filter { Files.isRegularFile(it) && !isSkipped(source.relativize(it)) }.toList()
        }
        val total = files.sumOf { Files.size(it) }.coerceAtLeast(1)
        Files.createDirectories(target)
        listOf("app", "runtime").map(target::resolve).filter { it.exists() }.forEach { deleteTree(it) }
        var done = 0L
        for (f in files) {
            val rel = source.relativize(f)
            val out = target.resolve(rel.toString())
            Files.createDirectories(out.parent)
            Files.copy(f, out, StandardCopyOption.REPLACE_EXISTING)
            done += Files.size(f)
            onProgress(done.toFloat() / total, rel.name)
        }
        onProgress(1f, "")
    }

    private fun isSkipped(rel: Path): Boolean =
        rel.name in skipped || (rel.nameCount > 1 && rel.getName(0).toString() in skippedDirs)

    fun deleteTree(dir: Path) {
        if (!dir.exists()) return
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(d: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(d); return FileVisitResult.CONTINUE
            }
        })
    }

    /** Запись в «Приложения» Windows: название, версия, значок, размер и команда удаления. */
    fun register(registry: RegistryStore, target: Path, version: String, sizeBytes: Long) {
        val exe = target.resolve("$APP_NAME.exe").toString()
        registry.write(UNINSTALL_KEY, mapOf(
            "DisplayName" to APP_NAME,
            "DisplayVersion" to version,
            "Publisher" to "Eroun97",
            "DisplayIcon" to "$exe,0",
            "InstallLocation" to target.toString(),
            "UninstallString" to "\"$exe\" --uninstall",
            "URLInfoAbout" to "https://github.com/nurudinsaidov98-commits/YouLauncher",
            "EstimatedSize" to (sizeBytes / 1024).toInt(),
            "NoModify" to 1,
            "NoRepair" to 1,
        ))
    }

    /** Папка прежней установки из реестра, если она ещё на месте. */
    fun installedLocation(registry: RegistryStore): Path? =
        registry.read(UNINSTALL_KEY, "InstallLocation")?.let { runCatching { Path.of(it) }.getOrNull() }?.takeIf { isOurs(it) }

    /** После автообновления установленной копии версия в «Приложениях» Windows обновляется при следующем запуске. */
    fun refreshVersion(registry: RegistryStore, appDir: Path, version: String) {
        val installed = installedLocation(registry) ?: return
        if (installed.normalize() != appDir.normalize() || registry.read(UNINSTALL_KEY, "DisplayVersion") == version) return
        registry.write(UNINSTALL_KEY, mapOf("DisplayVersion" to version))
    }

    /**
     * Удаление: ярлыки, запись в реестре и (по желанию) данные пользователя. Саму папку программы удаляет
     * [scheduleRemoval] после закрытия окна: пока оно открыто, её файлы заняты работающей программой.
     */
    fun uninstall(registry: RegistryStore, shortcuts: List<Path>, userData: Path?) {
        shortcuts.forEach { runCatching { Files.deleteIfExists(it) } }
        registry.delete(UNINSTALL_KEY)
        userData?.let { runCatching { deleteTree(it) } }
    }

    /** Отдельный процесс ждёт выхода программы и удаляет папку установки. */
    fun scheduleRemoval(dir: Path): Boolean = runCatching {
        if (!System.getProperty("os.name").startsWith("Windows")) return false
        val script = "ping 127.0.0.1 -n 4 > nul & rmdir /s /q \"$dir\""
        ProcessBuilder("cmd.exe", "/c", script).directory(File(System.getProperty("java.io.tmpdir"))).start()
        true
    }.getOrDefault(false)
}

/** Реестр Windows через reg.exe (раздел HKCU, права администратора не нужны). */
object WindowsRegistry : RegistryStore {
    override fun read(key: String, name: String): String? = runCatching {
        val p = ProcessBuilder("reg.exe", "query", key, "/v", name).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS)
        out.lineSequence().map { it.trim() }.firstOrNull { it.startsWith(name) }
            ?.split(Regex("\\s{4,}"), limit = 3)?.getOrNull(2)
    }.getOrNull()

    override fun write(key: String, values: Map<String, Any>) {
        values.forEach { (name, value) ->
            val type = if (value is Int) "REG_DWORD" else "REG_SZ"
            // Кавычки внутри значения (путь в UninstallString) экранируются, иначе reg.exe их съедает.
            ProcessBuilder("reg.exe", "add", key, "/v", name, "/t", type, "/d", value.toString().replace("\"", "\\\""), "/f")
                .redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS)
        }
    }

    override fun delete(key: String) {
        runCatching { ProcessBuilder("reg.exe", "delete", key, "/f").redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS) }
    }
}

/** Ярлыки через WScript.Shell (штатный способ Windows). */
object WindowsShortcuts : ShortcutMaker {
    override fun folder(place: ShortcutPlace): Path? = runCatching {
        // Вывод в UTF-8: путь может содержать кириллицу («Рабочий стол»).
        val script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; [Environment]::GetFolderPath('${place.specialFolder}')"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
        val out = p.inputStream.readBytes().decodeToString().trim()
        p.waitFor(20, TimeUnit.SECONDS)
        out.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    }.getOrNull()

    override fun create(folder: Path, name: String, target: Path, arguments: String): Boolean = runCatching {
        fun ps(s: String) = s.replace("'", "''")
        Files.createDirectories(folder)
        val lnk = folder.resolve("$name.lnk")
        val script = "\$s=(New-Object -ComObject WScript.Shell).CreateShortcut('${ps(lnk.toString())}');" +
            "\$s.TargetPath='${ps(target.toString())}';\$s.Arguments='${ps(arguments)}';" +
            "\$s.WorkingDirectory='${ps(target.parent.toString())}';\$s.IconLocation='${ps(target.toString())},0';\$s.Save()"
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(true).start()
        p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)
}
