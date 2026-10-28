package app.youlauncher.data.horizon

import app.youlauncher.core.AppDirs
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.data.download.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * Horizon: Minecraft Java Edition, переписанный на Rust (github.com/horizon-rust/horizon, MIT).
 * В релизе нет файлов Minecraft: ресурсы готовятся на этом компьютере из скачанной с серверов Mojang игры.
 */
object HorizonInfo {
    const val REPO = "horizon-rust/horizon"
    const val PAGE = "https://github.com/$REPO"
    const val ASSET = "horizon-windows-x64.zip"
    const val CLIENT = "horizon-client.exe"
    const val PREPARE = "horizon-prepare.exe"
    /** Файл, по которому видно, что ресурсы версии подготовлены (как в лаунчере Horizon). */
    const val RESOURCES_MANIFEST = "horizon-resources.json"
    /** Метка полностью распакованной версии: без неё папка считается недокачанной. */
    const val INSTALLED_MARK = ".youlauncher-installed"
    /** Теги игры: «v» и цифры (v0.2.93); теги лаунчера Horizon и nightly не подходят. */
    val GAME_TAG = Regex("^v\\d+(\\.\\d+)*$")
}

data class HorizonRelease(val tag: String, val zipUrl: String, val sha256: String?, val publishedAt: String)

/** Разбор ответов GitHub и файлов релиза без сети: проверяется тестами. */
object HorizonParsing {
    private val json = Json { ignoreUnknownKeys = true }

    /** Релизы игры для Windows, новые первыми. */
    fun releases(text: String): List<HorizonRelease> = (json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        if (!HorizonInfo.GAME_TAG.matches(tag)) return@mapNotNull null
        if (o["draft"]?.jsonPrimitive?.boolean == true || o["prerelease"]?.jsonPrimitive?.boolean == true) return@mapNotNull null
        val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull == HorizonInfo.ASSET }
            ?: return@mapNotNull null
        HorizonRelease(
            tag = tag,
            zipUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            sha256 = asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")?.lowercase(),
            publishedAt = o["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }.sortedByDescending { it.publishedAt }

    /** Версия Minecraft, под которую собран релиз (research-lock.json: minecraft.version). */
    fun lockedGameVersion(lock: String): String? =
        runCatching { json.parseToJsonElement(lock).jsonObject["minecraft"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** SHA-1 файла версии Minecraft, закреплённого в research-lock.json. */
    fun pinnedManifestSha1(lock: String): String? =
        runCatching { json.parseToJsonElement(lock).jsonObject["minecraft"]?.jsonObject?.get("manifest_sha1")?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** Путь внутри архива без верхней папки (horizon-windows-x64/...); null — запись вне папки или опасный путь. */
    fun entryPath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.any { it == ".." || it.contains(':') }) return null
        return parts.drop(1).joinToString("/")
    }
}

/** Список релизов Horizon с GitHub; кэш на полчаса, чтобы мастер и запуск не упирались в лимит API. */
class HorizonReleases(private val http: HttpClient) {
    private var cached: Pair<Long, List<HorizonRelease>>? = null
    private val lockCache = mutableMapOf<String, String>()

    suspend fun releases(): List<HorizonRelease> {
        cached?.let { (at, list) -> if (System.currentTimeMillis() - at < CACHE_MS) return list }
        val list = HorizonParsing.releases(text("https://api.github.com/repos/${HorizonInfo.REPO}/releases?per_page=30"))
        cached = System.currentTimeMillis() to list
        return list
    }

    suspend fun release(tag: String): HorizonRelease =
        releases().firstOrNull { it.tag == tag } ?: error("Релиз Horizon $tag не найден на GitHub")

    /** Версия Minecraft релиза: research-lock.json по тегу, без скачивания архива. */
    suspend fun gameVersion(tag: String): String = lockCache[tag] ?: (
        HorizonParsing.lockedGameVersion(text("https://raw.githubusercontent.com/${HorizonInfo.REPO}/$tag/research-lock.json"))
            ?: error("В релизе Horizon $tag не указана версия Minecraft")
        ).also { lockCache[tag] = it }

    private suspend fun text(url: String): String {
        val r = http.get(url) { header("Accept", "application/vnd.github+json") }
        if (!r.status.isSuccess()) throw IOException("HTTP ${r.status.value}: $url")
        return r.bodyAsText()
    }

    companion object {
        const val CACHE_MS = 30 * 60_000L
    }
}

/**
 * Установка и подготовка версий Horizon в <данные>/horizon/<тег>. Ресурсы готовит horizon-prepare из релиза:
 * он берёт client.jar, библиотеки и ассеты из папки лаунчера (та же раскладка, что у официального) и
 * server.jar той же версии; нужна Java, которую закрепляет research-lock.json (для 26.3 — Java 25).
 */
class HorizonInstaller(
    private val dirs: AppDirs,
    private val releases: HorizonReleases,
    /** Скачивание с GitHub (архив релиза). */
    private val githubDownloader: () -> Downloader,
    /** Скачивание с серверов Mojang (server.jar). */
    private val mojangDownloader: () -> Downloader,
) {
    val root: Path get() = dirs.root.resolve("horizon")
    fun versionDir(tag: String): Path = root.resolve(tag)
    fun client(tag: String): Path = versionDir(tag).resolve(HorizonInfo.CLIENT)
    fun isInstalled(tag: String): Boolean = versionDir(tag).resolve(HorizonInfo.INSTALLED_MARK).exists() && client(tag).exists()

    fun gameVersionOf(tag: String): String =
        HorizonParsing.lockedGameVersion(versionDir(tag).resolve("research-lock.json").readText())
            ?: error("В Horizon $tag нет research-lock.json")

    fun resources(tag: String): Path = versionDir(tag).resolve(".local/horizon-client").resolve(gameVersionOf(tag))
    fun isPrepared(tag: String): Boolean = isInstalled(tag) && resources(tag).resolve(HorizonInfo.RESOURCES_MANIFEST).exists()

    /** Скачивает архив релиза, сверяет SHA-256 из GitHub и распаковывает в папку версии. */
    suspend fun install(release: HorizonRelease, log: (String) -> Unit) {
        if (isInstalled(release.tag)) return
        val zip = root.resolve("downloads").resolve("${release.tag}-${HorizonInfo.ASSET}")
        log("Horizon ${release.tag}: скачивание ${HorizonInfo.ASSET}")
        githubDownloader().downloadAll(listOf(FileTask(release.zipUrl, zip, null)))
        withContext(Dispatchers.IO) {
            val expected = release.sha256
            if (expected != null) {
                val actual = sha256(zip)
                if (!actual.equals(expected, ignoreCase = true)) {
                    Files.deleteIfExists(zip)
                    throw IOException("Архив Horizon ${release.tag} повреждён: SHA-256 не совпадает")
                }
            }
            val target = versionDir(release.tag)
            if (target.exists()) target.toFile().deleteRecursively()
            unzip(zip, target)
            Files.writeString(target.resolve(HorizonInfo.INSTALLED_MARK), release.tag)
            Files.deleteIfExists(zip)
        }
        log("Horizon ${release.tag} установлен")
    }

    /** server.jar версии Minecraft с серверов Mojang, проверка SHA-1 из её json. */
    suspend fun serverJar(gameVersion: String, url: String, sha1: String?, size: Long): Path {
        val jar = root.resolve("server-jars").resolve("$gameVersion.jar")
        mojangDownloader().downloadAll(listOf(FileTask(url, jar, sha1, size)))
        return jar
    }

    /**
     * Подготовка ресурсов (один раз на версию): horizon-prepare --prepare, затем звуки.
     * Звуки необязательны: без них Horizon запускается, но молчит.
     */
    suspend fun prepare(tag: String, minecraftDir: Path, serverJar: Path, javaHome: Path, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        val dir = versionDir(tag)
        val base = listOf(dir.resolve(HorizonInfo.PREPARE).toString())
        seedManifest(tag)
        val prepare = run(base + listOf("--root", dir.toString(), "--prepare", "--server-jar", serverJar.toString(), "--minecraft-dir", minecraftDir.toString()), dir, javaHome, log)
        if (prepare != 0) throw IOException("Подготовка Horizon не удалась (код $prepare). Подробности в консоли")
        val sounds = run(base + listOf("sounds", "--root", dir.toString(), "--minecraft-dir", minecraftDir.toString()), dir, javaHome, log)
        if (sounds != 0) log("Звуки Horizon не скопированы (код $sounds): игра будет без звука")
        if (!isPrepared(tag)) throw IOException("После подготовки нет ресурсов Horizon")
    }

    /**
     * Mojang иногда переиздаёт файл версии (меняется только индекс ассетов), и horizon-prepare не узнаёт новый.
     * На этот случай он берёт свою копию из .local/minecraft/<версия>/manifest.json: кладём туда закреплённый
     * файл с серверов Mojang (проверка SHA-1). Если наш файл ему знаком, он сам заменит копию.
     */
    private suspend fun seedManifest(tag: String) {
        val mc = gameVersionOf(tag)
        val pinned = HorizonParsing.pinnedManifestSha1(versionDir(tag).resolve("research-lock.json").readText()) ?: return
        val target = versionDir(tag).resolve(".local/minecraft").resolve(mc).resolve("manifest.json")
        if (target.exists()) return
        mojangDownloader().downloadAll(listOf(FileTask("https://piston-meta.mojang.com/v1/packages/$pinned/$mc.json", target, pinned)))
    }

    private fun run(command: List<String>, dir: Path, javaHome: Path, log: (String) -> Unit): Int {
        val pb = ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true)
        pb.environment()["JAVA_HOME"] = javaHome.toString()
        pb.environment()["PATH"] = javaHome.resolve("bin").toString() + java.io.File.pathSeparator + (pb.environment()["PATH"] ?: "")
        val p = pb.start()
        p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { if (it.isNotBlank()) log(it) }
        return p.waitFor()
    }

    /** Старые версии удаляются; остаются нужные экземплярам. */
    fun cleanup(keep: Set<String>) {
        if (!root.isDirectory()) return
        root.listDirectoryEntries().filter { it.isDirectory() && HorizonInfo.GAME_TAG.matches(it.name) && it.name !in keep }
            .forEach { runCatching { it.toFile().deleteRecursively() } }
    }

    private fun unzip(zip: Path, target: Path) {
        Files.createDirectories(target)
        val base = target.toAbsolutePath().normalize()
        ZipFile(zip.toFile()).use { z ->
            for (entry in z.entries()) {
                val rel = HorizonParsing.entryPath(entry.name) ?: continue
                val out = base.resolve(rel).normalize()
                if (!out.startsWith(base)) continue
                if (entry.isDirectory) { Files.createDirectories(out); continue }
                Files.createDirectories(out.parent)
                z.getInputStream(entry).use { Files.copy(it, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            }
        }
    }

    companion object {
        fun sha256(file: Path): String = Files.newInputStream(file).use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
