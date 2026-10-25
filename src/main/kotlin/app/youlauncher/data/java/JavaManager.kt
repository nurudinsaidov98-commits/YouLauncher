package app.youlauncher.data.java

import app.youlauncher.core.AppDirs
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.mojang.JavaRuntimeManifest
import app.youlauncher.core.mojang.JavaVersionRef
import app.youlauncher.core.mojang.RuleEnv
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.mojang.MojangApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText

enum class JavaSource { Mojang, Temurin, Zulu, Microsoft, Corretto, Liberica, Oracle, System, Custom }

data class JavaInstall(
    /** Путь к javaw.exe (на Windows) или java. */
    val executable: Path,
    val home: Path,
    val major: Int,
    val version: String,
    val vendor: String,
    val source: JavaSource,
    val component: String? = null,
)

private val demoInstalls: List<JavaInstall> = listOf(
    Triple(25, "25.0.1", listOf("Eclipse Adoptium", "jdk-25.0.1.8-hotspot")) to JavaSource.Temurin,
    Triple(21, "21.0.9", listOf("Eclipse Adoptium", "jdk-21.0.9.10-hotspot")) to JavaSource.Temurin,
    Triple(17, "17.0.16", listOf("Microsoft", "jdk-17.0.16.8-hotspot")) to JavaSource.Microsoft,
).map { (v, source) ->
    val home = Path.of("C:/Program Files", *v.third.toTypedArray())
    JavaInstall(home.resolve("bin").resolve("javaw.exe"), home, v.first, v.second, v.third.first(), source)
}

/** Разбор версии из файла release: JAVA_VERSION="1.8.0_412" или "21.0.7". */
object JavaVersions {
    fun major(version: String): Int {
        val v = version.trim('"')
        val parts = v.split('.', '_', '-', '+')
        val first = parts.firstOrNull()?.toIntOrNull() ?: return 0
        return if (first == 1) parts.getOrNull(1)?.toIntOrNull() ?: 0 else first
    }

    /** Подходит ли Java для версии игры: Java 8 строго 8 (старые версии ломаются на 9+), остальным не ниже нужной. */
    fun compatible(required: Int, actual: Int): Boolean = if (required <= 8) actual == 8 else actual >= required
}

/**
 * Java для игры: рантаймы из реестра Mojang (piston-meta, компонент из version JSON, проверка SHA-1
 * каждого файла) и найденные в системе установки. Ручной выбор пути в настройках важнее всего.
 */
class JavaManager(
    private val api: MojangApi,
    private val dirs: AppDirs,
    private val downloaderFactory: () -> Downloader,
    /** Демо и скриншоты: выдуманные установки Java вместо найденных на этом компьютере (в путях было бы имя пользователя). */
    private val demo: Boolean = false,
) {
    private val exeName = if (RuleEnv.currentOsName() == "windows") "javaw.exe" else "java"

    fun platformKey(): String {
        val env = RuleEnv()
        return when (env.osName) {
            "windows" -> when (env.arch) { "x86" -> "windows-x86"; "arm64" -> "windows-arm64"; else -> "windows-x64" }
            "osx" -> if (env.arch == "arm64") "mac-os-arm64" else "mac-os"
            else -> if (env.arch == "x86") "linux-i386" else "linux"
        }
    }

    fun runtimeDir(component: String): Path = dirs.runtimesDir.resolve(component)

    /** Уже скачанный рантайм Mojang для компонента. */
    fun installedMojang(component: String): JavaInstall? {
        val home = runtimeDir(component)
        val exe = home.resolve("bin").resolve(exeName)
        if (!exe.exists()) return null
        return describe(home, JavaSource.Mojang)?.copy(component = component)
    }

    suspend fun ensureMojangRuntime(ref: JavaVersionRef, onProgress: (Float?, String) -> Unit = { _, _ -> }): JavaInstall = coroutineScope {
        installedMojang(ref.component)?.let { if (it.major == ref.majorVersion || ref.majorVersion == 0) return@coroutineScope it }
        val index = api.javaRuntimes()
        val entry = index[platformKey()]?.get(ref.component)?.firstOrNull()
            ?: error("Mojang не публикует ${ref.component} для ${platformKey()}")
        val manifestText = api.fetchText(entry.manifest.url)
        entry.manifest.sha1?.let { check(Downloader.sha1(manifestText.byteInputStream()).equals(it, true)) { "SHA-1 манифеста Java не совпал" } }
        val manifest = YouLauncherJson.decodeFromString(JavaRuntimeManifest.serializer(), manifestText)
        val home = runtimeDir(ref.component)
        withContext(Dispatchers.IO) {
            manifest.files.filter { it.value.type == "directory" }.keys.forEach { Files.createDirectories(home.resolve(it)) }
        }
        val tasks = manifest.files.filter { it.value.type == "file" }.mapNotNull { (path, f) ->
            val raw = f.downloads?.raw ?: return@mapNotNull null
            FileTask(raw.url, home.resolve(path), raw.sha1, raw.size)
        }
        val downloader = downloaderFactory()
        val watcher = launch { downloader.progress.collectLatest { onProgress(it.fraction, "${it.doneFiles}/${it.totalFiles}") } }
        try { downloader.downloadAll(tasks) } finally { watcher.cancel() }
        withContext(Dispatchers.IO) {
            manifest.files.filter { it.value.executable }.keys.forEach { home.resolve(it).toFile().setExecutable(true) }
            home.resolve(".youlauncher-version").writeText(entry.version.name)
        }
        installedMojang(ref.component) ?: error("Java ${ref.component} не установилась")
    }

    /** Поиск Java в системе: JAVA_HOME, PATH, типичные папки производителей и наши рантаймы. */
    suspend fun detect(extra: List<String> = emptyList()): List<JavaInstall> = withContext(Dispatchers.IO) {
        if (demo) return@withContext demoInstalls
        val homes = mutableSetOf<Path>()
        System.getenv("JAVA_HOME")?.let { homes.add(Path.of(it)) }
        System.getenv("PATH")?.split(java.io.File.pathSeparator)?.forEach { p ->
            runCatching { Path.of(p) }.getOrNull()?.takeIf { it.resolve(exeName).exists() || it.resolve("java.exe").exists() }?.let { homes.add(it.parent) }
        }
        val roots = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("ProgramW6432"))
            .flatMap { pf -> listOf("Java", "Eclipse Adoptium", "Eclipse Foundation", "Zulu", "Microsoft", "Amazon Corretto", "BellSoft", "AdoptOpenJDK", "Semeru").map { Path.of(pf, it) } } +
            listOf(Path.of(System.getProperty("user.home"), ".jdks"), Path.of(System.getProperty("user.home"), ".gradle", "jdks"), dirs.runtimesDir)
        for (root in roots) {
            if (!root.isDirectory()) continue
            runCatching { root.listDirectoryEntries() }.getOrDefault(emptyList()).filter { it.isDirectory() }.forEach { homes.add(it) }
        }
        extra.forEach { runCatching { homes.add(Path.of(it)) } }
        homes.mapNotNull { home ->
            val source = sourceOf(home)
            describe(home, source)
        }.distinctBy { it.home.toAbsolutePath().normalize() }.sortedByDescending { it.major }
    }

    fun describe(home: Path, source: JavaSource): JavaInstall? {
        val exe = home.resolve("bin").resolve(exeName).takeIf { it.isRegularFile() }
            ?: home.resolve("bin").resolve("java.exe").takeIf { it.isRegularFile() }
            ?: return null
        val release = home.resolve("release")
        val props = if (release.exists()) runCatching {
            release.readLines().mapNotNull { l -> l.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim('"') } }.toMap()
        }.getOrDefault(emptyMap()) else emptyMap()
        val version = props["JAVA_VERSION"] ?: listOf(".youlauncher-version", ".${app.youlauncher.core.LegacyNames.ID}-version").map(home::resolve).firstOrNull { it.exists() }?.let { Files.readString(it).trim() } ?: return null
        return JavaInstall(exe, home, JavaVersions.major(version), version, props["IMPLEMENTOR"] ?: source.name, source, component = if (source == JavaSource.Mojang) home.name else null)
    }

    private fun sourceOf(home: Path): JavaSource {
        val p = home.toString().lowercase()
        return when {
            home.startsWith(dirs.runtimesDir) && home.name.startsWith("temurin") -> JavaSource.Temurin
            home.startsWith(dirs.runtimesDir) -> JavaSource.Mojang
            "adoptium" in p || "temurin" in p || "adoptopenjdk" in p -> JavaSource.Temurin
            "zulu" in p -> JavaSource.Zulu
            "microsoft" in p -> JavaSource.Microsoft
            "corretto" in p -> JavaSource.Corretto
            "bellsoft" in p || "liberica" in p -> JavaSource.Liberica
            "\\java\\" in p || "/java/" in p -> JavaSource.Oracle
            else -> JavaSource.System
        }
    }

    /** Проверка выбранной вручную Java: существует ли и какой версии. */
    fun inspect(executable: Path): JavaInstall? {
        val home = executable.parent?.parent ?: return null
        return describe(home, JavaSource.Custom)?.copy(executable = executable)
    }

    /**
     * Проверка запуском: `java -version` (консольный java.exe рядом с javaw.exe), до 15 секунд.
     * Ловит сломанные и неподходящие по разрядности установки, которые выглядят целыми по файлам.
     */
    suspend fun verify(install: JavaInstall): JavaCheck = withContext(Dispatchers.IO) {
        val console = install.executable.resolveSibling(if (RuleEnv.currentOsName() == "windows") "java.exe" else "java")
        val exe = if (console.exists()) console else install.executable
        runCatching {
            val p = ProcessBuilder(exe.toString(), "-version").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly()
                return@runCatching JavaCheck(false, "timeout")
            }
            JavaCheck(p.exitValue() == 0, JavaChecks.firstLine(out))
        }.getOrElse { JavaCheck(false, it.message ?: it::class.simpleName.orEmpty()) }
    }
}

data class JavaCheck(val ok: Boolean, val output: String)

object JavaChecks {
    /** Первая содержательная строка вывода `java -version`, например `openjdk version "21.0.7" 2025-04-15 LTS`. */
    fun firstLine(output: String): String = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("Picked up") }.orEmpty()

    /** Какая Java нужна версии игры, если в version JSON нет поля javaVersion (очень старые версии). */
    fun requiredMajor(gameVersion: String): Int {
        val p = gameVersion.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        return when {
            p.firstOrNull() ?: 0 >= 26 -> 25
            p.getOrElse(0) { 0 } != 1 -> 8
            p.getOrElse(1) { 0 } >= 21 || (p.getOrElse(1) { 0 } == 20 && p.getOrElse(2) { 0 } >= 5) -> 21
            p.getOrElse(1) { 0 } >= 18 -> 17
            p.getOrElse(1) { 0 } == 17 -> 16
            else -> 8
        }
    }
}
