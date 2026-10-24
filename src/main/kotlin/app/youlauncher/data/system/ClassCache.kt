package app.youlauncher.data.system

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * Кэш классов Java (AppCDS) для быстрого первого открытия экранов: без него каждый запуск заново разбирает
 * около 12 тысяч классов Compose, и первый переход на экран «думает» до секунды.
 *
 * Список классов (`app/classes.lst`) готовится при сборке. Сам архив хранит абсолютные пути к jar-файлам,
 * поэтому собирается на компьютере пользователя рядом с программой (`app/youlauncher.jsa`, путь задан в
 * YouLauncher.cfg): в фоне, отдельным процессом той же программы с -Xshare:dump. Метка рядом с архивом
 * хранит папку и версию; после переноса папки или обновления архив пересобирается. Если архив не подходит,
 * Java просто работает без него.
 */
class ClassCache(private val appDir: Path?, private val version: String) {
    private val app get() = appDir?.resolve("app")
    val classList: Path? get() = app?.resolve("classes.lst")
    val archive: Path? get() = app?.resolve("youlauncher.jsa")
    private val stamp: Path? get() = app?.resolve("youlauncher.jsa.stamp")
    private val exe: Path? get() = appDir?.resolve("YouLauncher.exe")

    /**
     * Заранее загружает классы из списка в фоновом потоке с низким приоритетом (без статической инициализации),
     * чтобы первый переход на экран не ждал загрузки классов. Помогает и там, где архив неполный: Java не кладёт
     * в архив jar-файлы из пути с кириллицей.
     */
    fun preload(loader: ClassLoader = ClassCache::class.java.classLoader): Int {
        val list = classList?.takeIf { it.exists() } ?: return 0
        var loaded = 0
        runCatching {
            Files.newBufferedReader(list).useLines { lines ->
                lines.mapNotNull { ClassLists.className(it) }.forEach { name ->
                    if (runCatching { Class.forName(name, false, loader) }.isSuccess) loaded++
                }
            }
        }
        return loaded
    }

    /** Метка: для какой папки и версии собран архив. */
    fun expectedStamp(): String {
        // Имя основного jar содержит хеш сборки: пересборка той же версии тоже требует нового архива.
        val jar = app?.toFile()?.list()?.firstOrNull { it.startsWith("youlauncher-") && it.endsWith(".jar") }
        return "${appDir?.toAbsolutePath()}|$version|$jar"
    }

    /** Нужно ли собрать архив: есть список классов и программа, архива нет или он для другой папки или версии. */
    fun needsBuild(): Boolean {
        val list = classList ?: return false
        if (!list.exists() || exe?.exists() != true) return false
        val current = runCatching { stamp?.let { Files.readString(it).trim() } }.getOrNull()
        return archive?.exists() != true || current != expectedStamp()
    }

    /**
     * Собирает архив отдельным процессом (несколько десятков секунд в фоне, интерфейс не трогает).
     * Папка только для чтения (установка в Program Files) или ошибка: просто остаёмся без кэша.
     */
    fun build(timeoutMinutes: Long = 5): Boolean = runCatching {
        val list = classList ?: return false
        val app = app ?: return false
        if (!Files.isWritable(app)) return false
        stamp?.let { Files.deleteIfExists(it) }
        archive?.let { Files.deleteIfExists(it) }
        val pb = ProcessBuilder(exe.toString()).directory(appDir!!.toFile()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        // Путь к архиву берётся из YouLauncher.cfg (-XX:SharedArchiveFile=$APPDIR\youlauncher.jsa). Список классов
        // задаётся относительно папки программы: в JAVA_TOOL_OPTIONS нельзя передать путь с пробелами или кириллицей.
        if (!list.exists()) return false
        pb.environment()["JAVA_TOOL_OPTIONS"] = "-Xshare:dump -XX:SharedClassListFile=app\\classes.lst"
        val p = pb.start()
        if (!p.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            p.destroyForcibly()
            return false
        }
        val ok = archive?.exists() == true && Files.size(archive!!) > 0
        if (ok) stamp?.let { Files.writeString(it, expectedStamp()) }
        ok
    }.getOrDefault(false)
}

/** Строки списка классов Java (-XX:DumpLoadedClassList): «java/lang/Object id: 0», служебные строки начинаются с # или @. */
object ClassLists {
    fun className(line: String): String? {
        val token = line.trim().substringBefore(' ')
        if (token.isEmpty() || token.startsWith('#') || token.startsWith('@')) return null
        // Скрытые и сгенерированные классы (лямбды, прокси) по имени не загружаются.
        if ("\$\$Lambda" in token || "\$Proxy" in token || "/LambdaForm\$" in token) return null
        return token.replace('/', '.')
    }
}
