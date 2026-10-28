package app.youlauncher.data.loaders

import app.youlauncher.core.AppDirs
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.model.LoaderType
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.mojang.Library
import app.youlauncher.core.mojang.MavenCoordinate
import app.youlauncher.core.mojang.VersionJson
import app.youlauncher.core.writeTextAtomic
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.mojang.MojangApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.exists

@Serializable
data class SidedValue(val client: String = "", val server: String = "")

@Serializable
data class Processor(
    val jar: String,
    val classpath: List<String> = emptyList(),
    val args: List<String> = emptyList(),
    val outputs: Map<String, String> = emptyMap(),
    val sides: List<String>? = null,
)

/** install_profile.json установщиков Forge (1.13+) и NeoForge, spec 1. */
@Serializable
data class InstallProfile(
    val spec: Int = 0,
    val version: String = "",
    val minecraft: String = "",
    val json: String = "/version.json",
    val data: Map<String, SidedValue> = emptyMap(),
    val processors: List<Processor> = emptyList(),
    val libraries: List<Library> = emptyList(),
)

/** Подстановка аргументов processors: {КЛЮЧ} из data, [maven:координаты] в путь библиотеки. */
object ProcessorArgs {
    fun resolveValue(raw: String, librariesDir: Path, extracted: (String) -> Path): String = when {
        raw.startsWith("[") && raw.endsWith("]") -> librariesDir.resolve(MavenCoordinate.parse(raw.substring(1, raw.length - 1)).path()).toString()
        raw.startsWith("'") && raw.endsWith("'") -> raw.substring(1, raw.length - 1)
        raw.startsWith("/") -> extracted(raw).toString()
        else -> raw
    }

    fun substitute(arg: String, data: Map<String, String>, librariesDir: Path): String {
        if (arg.startsWith("[") && arg.endsWith("]")) return librariesDir.resolve(MavenCoordinate.parse(arg.substring(1, arg.length - 1)).path()).toString()
        return Regex("""\{([A-Z_]+)}""").replace(arg) { m -> data[m.groupValues[1]] ?: m.value }
    }
}

/**
 * Установка загрузчиков модов. Fabric и Quilt: профиль из meta API (inheritsFrom ванильной версии).
 * Forge и NeoForge: официальный установщик из их Maven (с проверкой .sha1), его version.json,
 * библиотеки и processors запускаются в фоне без окна установщика.
 */
class LoaderInstaller(
    private val http: HttpClient,
    private val api: MojangApi,
    private val dirs: AppDirs,
    private val downloaderFactory: () -> Downloader,
) {
    private suspend fun text(url: String): String {
        val r = http.get(url)
        if (!r.status.isSuccess()) throw IOException("HTTP ${r.status.value}: $url")
        return r.bodyAsText()
    }

    /**
     * Готовит профиль загрузчика и возвращает id версии для запуска.
     * [javaFor] даёт java для processors Forge/NeoForge (рантайм базовой версии игры).
     */
    suspend fun ensure(loader: LoaderType, game: String, loaderVersion: String, javaFor: suspend () -> Path, log: (String) -> Unit, force: Boolean = false): String = when (loader) {
        LoaderType.Vanilla -> game
        LoaderType.Fabric -> fabricLike("${RemoteLoaderVersions.FABRIC_META}/v2/versions/loader/$game/$loaderVersion/profile/json")
        LoaderType.Quilt -> fabricLike("${RemoteLoaderVersions.QUILT_META}/v3/versions/loader/$game/$loaderVersion/profile/json")
        LoaderType.Forge -> installerBased(
            "${RemoteLoaderVersions.FORGE_MAVEN}/$game-$loaderVersion/forge-$game-$loaderVersion-installer.jar", game, javaFor, log, force,
        )
        LoaderType.NeoForge -> installerBased(
            "${RemoteLoaderVersions.NEOFORGE_MAVEN}/$loaderVersion/neoforge-$loaderVersion-installer.jar", game, javaFor, log, force,
        )
        // Horizon — отдельная игра на Rust, его ставит HorizonInstaller, а не профиль загрузчика.
        LoaderType.Horizon -> error("Horizon запускается без загрузчика модов")
    }

    private suspend fun fabricLike(url: String): String = withContext(Dispatchers.IO) {
        val body = text(url)
        val json = YouLauncherJson.decodeFromString(VersionJson.serializer(), body)
        api.versionJsonPath(json.id).writeTextAtomic(body)
        json.id
    }

    private suspend fun installerBased(url: String, game: String, javaFor: suspend () -> Path, log: (String) -> Unit, force: Boolean = false): String {
        val fileName = url.substringAfterLast('/')
        val installer = dirs.cacheDir.resolve("installers").resolve(fileName)
        val sha1 = runCatching { text("$url.sha1").trim().take(40) }.getOrNull()
        log("Установщик: $fileName")
        downloaderFactory().downloadAll(listOf(FileTask(url, installer, sha1)))

        return ZipFile(installer.toFile()).use { zip ->
            fun entryText(name: String) = zip.getEntry(name.removePrefix("/"))?.let { e -> zip.getInputStream(e).use { it.readBytes().decodeToString() } }
            val profileText = entryText("install_profile.json") ?: error("В установщике нет install_profile.json")
            val profileJson = YouLauncherJson.parseToJsonElement(profileText).jsonObject
            if (profileJson.containsKey("versionInfo")) return@use withContext(Dispatchers.IO) { legacyForge(zip, profileJson) }

            val profile = YouLauncherJson.decodeFromString(InstallProfile.serializer(), profileText)
            val versionText = entryText(profile.json) ?: error("В установщике нет ${profile.json}")
            val version = YouLauncherJson.decodeFromString(VersionJson.serializer(), versionText)
            val markerFile = api.versionDir(version.id).resolve(".youlauncher-installed")
            // При починке (force) установка повторяется; processors с верными выходными файлами всё равно пропускаются.
            val legacyMarker = api.versionDir(version.id).resolve(".${app.youlauncher.core.LegacyNames.ID}-installed")
            if (!force && (markerFile.exists() || legacyMarker.exists()) && api.versionJsonPath(version.id).exists()) return@use version.id

            withContext(Dispatchers.IO) { extractMaven(zip) }
            // Библиотеки установщика и самой версии (у вложенных url пустой: они уже распакованы).
            val tasks = (profile.libraries + version.libraries).mapNotNull { lib ->
                val coord = runCatching { MavenCoordinate.parse(lib.name) }.getOrNull() ?: return@mapNotNull null
                val a = lib.downloads?.artifact
                val path = a?.path ?: coord.path()
                val target = dirs.librariesDir.resolve(path)
                val u = a?.url?.takeIf { it.isNotBlank() } ?: lib.url?.let { base -> base.trimEnd('/') + "/" + path }
                if (u == null) {
                    if (!target.exists()) error("Нет адреса для библиотеки ${lib.name}")
                    null
                } else FileTask(u, target, a?.sha1 ?: lib.sha1, a?.size ?: lib.size ?: 0)
            }
            log("Библиотеки загрузчика: ${tasks.size}")
            downloaderFactory().downloadAll(tasks)

            val java = javaFor()
            withContext(Dispatchers.IO) {
                val tmp = Files.createTempDirectory("youlauncher-installer")
                try {
                    // Файлы data вида /data/client.lzma извлекаются из установщика во временный каталог.
                    val extract: (String) -> Path = { inJar ->
                        val out = tmp.resolve(inJar.removePrefix("/"))
                        if (!out.exists()) {
                            Files.createDirectories(out.parent)
                            zip.getInputStream(zip.getEntry(inJar.removePrefix("/")) ?: error("Нет $inJar в установщике")).use { Files.copy(it, out) }
                        }
                        out
                    }
                    val mc = profile.minecraft.ifBlank { game }
                    val data = profile.data.mapValues { ProcessorArgs.resolveValue(it.value.client, dirs.librariesDir, extract) } + mapOf(
                        "SIDE" to "client",
                        "MINECRAFT_JAR" to api.clientJar(mc).toString(),
                        "MINECRAFT_VERSION" to mc,
                        "ROOT" to dirs.root.toString(),
                        "INSTALLER" to installer.toString(),
                        "LIBRARY_DIR" to dirs.librariesDir.toString(),
                    )
                    val client = profile.processors.filter { it.sides == null || "client" in it.sides }
                    client.forEachIndexed { i, p -> runProcessor(p, data, java, log, i + 1, client.size) }
                } finally {
                    tmp.toFile().deleteRecursively()
                }
                api.versionJsonPath(version.id).writeTextAtomic(versionText)
                markerFile.writeTextAtomic("ok")
            }
            version.id
        }
    }

    /** Библиотеки, вложенные в установщик (maven/...), распаковываются в общий каталог библиотек. */
    private fun extractMaven(zip: ZipFile) {
        zip.entries().asSequence().filter { !it.isDirectory && it.name.startsWith("maven/") }.forEach { e ->
            val out = dirs.librariesDir.resolve(e.name.removePrefix("maven/")).normalize()
            if (out.startsWith(dirs.librariesDir) && !out.exists()) {
                Files.createDirectories(out.parent)
                zip.getInputStream(e).use { Files.copy(it, out) }
            }
        }
    }

    private fun runProcessor(p: Processor, data: Map<String, String>, java: Path, log: (String) -> Unit, index: Int, total: Int) {
        val lib = dirs.librariesDir
        // Пропускаем, если все выходные файлы уже на месте и совпадают по хешу.
        val outputs = p.outputs.map { (k, v) -> ProcessorArgs.substitute(k, data, lib) to ProcessorArgs.substitute(v, data, lib).trim('\'') }
        if (outputs.isNotEmpty() && outputs.all { (file, hash) -> Path.of(file).exists() && Downloader.sha1(Path.of(file)).equals(hash, true) }) return

        val jarPath = lib.resolve(MavenCoordinate.parse(p.jar).path())
        val mainClass = JarFile(jarPath.toFile()).use { it.manifest?.mainAttributes?.getValue("Main-Class") } ?: error("Нет Main-Class в ${p.jar}")
        val cp = (listOf(p.jar) + p.classpath).distinct().joinToString(File.pathSeparator) { lib.resolve(MavenCoordinate.parse(it).path()).toString() }
        val args = p.args.map { ProcessorArgs.substitute(it, data, lib) }
        log("Processor $index/$total: ${p.jar.substringAfter(':').substringBefore(':')} ${args.firstOrNull { !it.startsWith("--") && it.isNotBlank() }?.take(40) ?: ""}")
        val proc = ProcessBuilder(listOf(java.toString(), "-cp", cp, mainClass) + args).redirectErrorStream(true).directory(dirs.root.toFile()).start()
        val output = StringBuilder()
        proc.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
        if (!proc.waitFor(20, TimeUnit.MINUTES) || proc.exitValue() != 0) {
            throw IOException("Processor ${p.jar} завершился с ошибкой:\n" + output.lines().takeLast(15).joinToString("\n"))
        }
        for ((file, hash) in outputs) {
            val actual = Downloader.sha1(Path.of(file))
            if (!actual.equals(hash, true)) throw IOException("Processor ${p.jar}: хеш $file не совпал")
        }
    }

    /** Старый формат Forge (до 1.13): versionInfo и универсальный jar внутри установщика. */
    private fun legacyForge(zip: ZipFile, profile: JsonObject): String {
        val versionInfo = profile["versionInfo"]!!.jsonObject
        val install = profile["install"]?.jsonObject ?: error("Нет секции install")
        // Старые библиотеки ссылаются на http://files.minecraftforge.net/maven/: переводим на https-зеркало Forge.
        val fixed = versionInfo.toString()
            .replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
            .replace("https://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/")
        val json = YouLauncherJson.decodeFromString(VersionJson.serializer(), fixed)
        api.versionJsonPath(json.id).writeTextAtomic(fixed)
        val coords = install["path"]!!.jsonPrimitive.content
        val filePath = install["filePath"]!!.jsonPrimitive.content
        val target = dirs.librariesDir.resolve(MavenCoordinate.parse(coords).path())
        if (!target.exists()) {
            Files.createDirectories(target.parent)
            zip.getInputStream(zip.getEntry(filePath) ?: error("Нет $filePath в установщике")).use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        }
        return json.id
    }
}
