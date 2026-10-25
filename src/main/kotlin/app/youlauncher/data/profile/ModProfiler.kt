package app.youlauncher.data.profile

import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.writeTextAtomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/** Нагрузка одного мода за сессию: доля процессорного времени и сколько памяти он выделял в секунду. */
@Serializable
data class ModUsage(val title: String, val fileName: String? = null, val cpuShare: Float = 0f, val allocBytesPerSec: Long = 0)

/** Итог одной игровой сессии. Доли считаются от всех замеров процессора в Java-коде игры. */
@Serializable
data class ProfileSession(
    val endedAt: Long,
    val durationSec: Long,
    val javaMajor: Int,
    val cpuSamples: Int,
    /** Средняя загрузка процессора процессом игры, 0..1 (все ядра). */
    val avgCpu: Float? = null,
    val peakHeapBytes: Long? = null,
    val gcPauseMs: Long? = null,
    /** Доля кода самой игры, Java и библиотек без модов. */
    val gameCpuShare: Float = 0f,
    val gameAllocBytesPerSec: Long = 0,
    /** Выделение памяти по модам есть только на Java 16+ (событие jdk.ObjectAllocationSample). */
    val allocationTracked: Boolean = false,
    val mods: List<ModUsage> = emptyList(),
)

@Serializable
data class InstanceProfile(val sessions: List<ProfileSession> = emptyList()) {
    /** Сводка по сохранённым сессиям, взвешенная по длительности: так одна короткая сессия не перекашивает картину. */
    fun summary(): ProfileSession? {
        if (sessions.isEmpty()) return null
        if (sessions.size == 1) return sessions.first()
        val total = sessions.sumOf { it.durationSec }.coerceAtLeast(1).toFloat()
        fun weight(s: ProfileSession) = s.durationSec / total
        val byMod = LinkedHashMap<String, Pair<Float, Double>>()
        val files = HashMap<String, String?>()
        for (s in sessions) for (m in s.mods) {
            val (cpu, alloc) = byMod[m.title] ?: (0f to 0.0)
            byMod[m.title] = (cpu + m.cpuShare * weight(s)) to (alloc + m.allocBytesPerSec * weight(s).toDouble())
            files[m.title] = m.fileName
        }
        val latest = sessions.first()
        return latest.copy(
            durationSec = sessions.sumOf { it.durationSec },
            cpuSamples = sessions.sumOf { it.cpuSamples },
            avgCpu = sessions.mapNotNull { s -> s.avgCpu?.let { it * weight(s) } }.takeIf { it.isNotEmpty() }?.sum(),
            peakHeapBytes = sessions.mapNotNull { it.peakHeapBytes }.maxOrNull(),
            gcPauseMs = sessions.mapNotNull { it.gcPauseMs }.takeIf { it.isNotEmpty() }?.sum(),
            gameCpuShare = sessions.sumOf { (it.gameCpuShare * weight(it)).toDouble() }.toFloat(),
            gameAllocBytesPerSec = sessions.sumOf { (it.gameAllocBytesPerSec * weight(it)).toDouble() }.toLong(),
            allocationTracked = sessions.any { it.allocationTracked },
            mods = byMod.map { (title, v) -> ModUsage(title, files[title], v.first, v.second.toLong()) }.sortedByDescending { it.cpuShare },
        )
    }
}

/**
 * Какой мод сколько нагружает игру. Во время игры работает встроенный в Java профилировщик JFR:
 * раз в 50 мс он отмечает, какой код выполняется в каждом потоке, и выборочно — где выделяется память.
 * После выхода лаунчер сопоставляет классы в этих замерах с jar-файлами модов и сохраняет итог.
 * Всё остаётся на компьютере пользователя. Нагрузка на игру — доли процента.
 */
class ModProfiler(private val storeDir: Path) {
    private val profiles = MutableStateFlow<Map<String, InstanceProfile>>(emptyMap())

    fun profile(instanceId: String): InstanceProfile = profiles.value[instanceId] ?: load(instanceId).also { p -> profiles.update { it + (instanceId to p) } }
    val all: StateFlow<Map<String, InstanceProfile>> = profiles.asStateFlow()

    /** Аргументы JVM для записи или null: на Java 8 JFR в рантайме Mojang недоступен, запуск сломался бы. */
    fun jvmArgs(javaMajor: Int, customJvmArgs: String): List<String>? {
        if (javaMajor < 11 || "StartFlightRecording" in customJvmArgs || "FlightRecorderOptions" in customJvmArgs) return null
        // Пути относительные: процесс игры запускается в папке экземпляра, кириллица в пути не мешает опциям JVM.
        return listOf(
            "-XX:StartFlightRecording=settings=$SETTINGS_FILE,disk=true,maxsize=160m,dumponexit=true,filename=$RECORDING_FILE,name=YouLauncher",
            "-XX:FlightRecorderOptions=repository=$REPOSITORY_DIR,stackdepth=96",
        )
    }

    /** Перед запуском: настройки записи в папке экземпляра и пустая папка для фрагментов записи. */
    fun prepare(gameDir: Path) {
        Files.writeString(gameDir.resolve(SETTINGS_FILE), SETTINGS)
        deleteTree(gameDir.resolve(REPOSITORY_DIR))
        Files.deleteIfExists(gameDir.resolve(RECORDING_FILE))
    }

    /**
     * После выхода игры: разбор записи и сопоставление с модами. При обычном выходе JVM сохраняет итоговый
     * файл записи; если процесс завершили принудительно, остаются фрагменты в папке записи (последний бывает
     * неполным и читается частично).
     */
    fun finish(instanceId: String, gameDir: Path, javaMajor: Int, mods: List<ModJar>): ProfileSession? {
        val repo = gameDir.resolve(REPOSITORY_DIR)
        val dump = gameDir.resolve(RECORDING_FILE)
        try {
            val chunks = if (dump.exists() && Files.size(dump) > 0) listOf(dump)
                else if (repo.exists()) Files.walk(repo).use { s -> s.filter { it.isRegularFile() && it.extension == "jfr" }.sorted().toList() }
                else emptyList()
            if (chunks.isEmpty()) return null
            val index = ModClassIndex.build(mods)
            val session = JfrAnalysis(index).run(chunks, javaMajor) ?: return null
            val updated = InstanceProfile((listOf(session) + profile(instanceId).sessions).take(MAX_SESSIONS))
            save(instanceId, updated)
            return session
        } finally {
            deleteTree(repo)
            runCatching { Files.deleteIfExists(dump) }
            runCatching { Files.deleteIfExists(gameDir.resolve(SETTINGS_FILE)) }
        }
    }

    fun save(instanceId: String, profile: InstanceProfile) {
        profiles.update { it + (instanceId to profile) }
        runCatching {
            Files.createDirectories(storeDir)
            storeDir.resolve("$instanceId.json").writeTextAtomic(YouLauncherJson.encodeToString(InstanceProfile.serializer(), profile))
        }
    }

    fun clear(instanceId: String) {
        profiles.update { it - instanceId }
        runCatching { Files.deleteIfExists(storeDir.resolve("$instanceId.json")) }
    }

    private fun load(instanceId: String): InstanceProfile = runCatching {
        YouLauncherJson.decodeFromString(InstanceProfile.serializer(), storeDir.resolve("$instanceId.json").readText())
    }.getOrDefault(InstanceProfile())

    companion object {
        const val SETTINGS_FILE = "youlauncher-profile.jfc"
        const val REPOSITORY_DIR = "youlauncher-jfr"
        const val RECORDING_FILE = "youlauncher-profile.jfr"
        const val MAX_SESSIONS = 5

        /** Только нужные события и редкие замеры: запись почти не влияет на игру. */
        val SETTINGS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <configuration version="2.0" label="YouLauncher" description="Mod load statistics" provider="YouLauncher">
              <event name="jdk.ExecutionSample"><setting name="enabled">true</setting><setting name="period">50 ms</setting></event>
              <event name="jdk.ObjectAllocationSample"><setting name="enabled">true</setting><setting name="throttle">100/s</setting><setting name="stackTrace">true</setting></event>
              <event name="jdk.CPULoad"><setting name="enabled">true</setting><setting name="period">1 s</setting></event>
              <event name="jdk.GCHeapSummary"><setting name="enabled">true</setting></event>
              <event name="jdk.GarbageCollection"><setting name="enabled">true</setting><setting name="threshold">0 ms</setting></event>
            </configuration>
        """.trimIndent()

        fun deleteTree(dir: Path) {
            if (!dir.exists()) return
            runCatching { Files.walk(dir).use { s -> s.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
        }
    }
}

/** Jar мода с названием для показа. */
data class ModJar(val path: Path, val title: String)

/**
 * Какие классы принадлежат каким модам: все .class из jar мода и из вложенных jar (Jar-in-Jar Fabric
 * и Forge — библиотеки, которые мод носит с собой, засчитываются ему). Плюс id модов: Mixin вписывает
 * их в имена внедрённых методов, так часть кода модов внутри классов игры тоже узнаётся.
 */
class ModClassIndex(val titles: List<String>, val files: List<String>, private val classes: Map<String, Int>, private val modIds: Map<String, Int>) {
    fun modOf(className: String, methodName: String? = null): Int? {
        classes[className]?.let { return it }
        // Скрытые классы лямбд: com.mod.Foo$$Lambda/0x... (или $$Lambda$12/...) принадлежат com.mod.Foo.
        val lambda = className.indexOf("\$\$Lambda")
        if (lambda > 0) classes[className.substring(0, lambda)]?.let { return it }
        if (methodName != null && '$' in methodName) {
            for (part in methodName.split('$')) modIds[part]?.let { return it }
        }
        return null
    }

    companion object {
        private val idPatterns = listOf(
            Regex("\"id\"\\s*:\\s*\"([a-z0-9_\\-]+)\""),
            Regex("modId\\s*=\\s*\"([a-z0-9_\\-]+)\""),
        )

        fun build(mods: List<ModJar>): ModClassIndex {
            val classes = HashMap<String, Int>(64 * 1024)
            val ids = HashMap<String, Int>()
            mods.forEachIndexed { i, mod ->
                runCatching {
                    ZipFile(mod.path.toFile()).use { zip ->
                        for (e in zip.entries()) {
                            val name = e.name
                            when {
                                name.endsWith(".class") -> classes.putIfAbsent(className(name), i)
                                name.endsWith(".jar") && (name.startsWith("META-INF/jars/") || name.startsWith("META-INF/jarjar/")) ->
                                    zip.getInputStream(e).use { nested(it, i, classes) }
                                name == "fabric.mod.json" || name == "quilt.mod.json" || name == "META-INF/mods.toml" || name == "META-INF/neoforge.mods.toml" ->
                                    zip.getInputStream(e).use { s -> modIdsOf(s.readBytes().decodeToString()).forEach { id -> ids.putIfAbsent(id, i) } }
                            }
                        }
                    }
                }
            }
            // Слишком общие id (например «core») давали бы ложные совпадения в именах методов.
            ids.keys.removeAll { it.length < 4 }
            return ModClassIndex(mods.map { it.title }, mods.map { it.path.name }, classes, ids)
        }

        private fun nested(input: InputStream, mod: Int, classes: HashMap<String, Int>) {
            ZipInputStream(input).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.name.endsWith(".class")) classes.putIfAbsent(className(e.name), mod)
                }
            }
        }

        fun modIdsOf(descriptor: String): List<String> = idPatterns.flatMap { p -> p.findAll(descriptor).map { it.groupValues[1] }.toList() }.distinct()

        private fun className(entry: String): String {
            val n = entry.removeSuffix(".class")
            return (if (n.startsWith("META-INF/versions/")) n.substringAfter('/').substringAfter('/').substringAfter('/') else n).replace('/', '.')
        }
    }
}
