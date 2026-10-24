package app.youlauncher.data.java

import app.youlauncher.core.AppDirs
import app.youlauncher.core.Hashes
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.mojang.RuleEnv
import app.youlauncher.data.download.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.writeText

data class AdoptiumBuild(val release: String, val fileName: String, val url: String, val sha256: String, val size: Long)

object AdoptiumParsing {
    /** Ответ /v3/assets/latest/{версия}/hotspot: первый zip-пакет JRE. */
    fun latest(json: String): AdoptiumBuild {
        val arr = YouLauncherJson.parseToJsonElement(json) as? JsonArray ?: throw IOException("Adoptium вернул неожиданный ответ")
        for (e in arr) {
            val o = e as? JsonObject ?: continue
            val pkg = ((o["binary"] as? JsonObject)?.get("package") as? JsonObject) ?: continue
            val name = pkg.s("name") ?: continue
            if (!name.endsWith(".zip")) continue
            return AdoptiumBuild(
                release = o.s("release_name").orEmpty(),
                fileName = name,
                url = pkg.s("link") ?: continue,
                sha256 = pkg.s("checksum")?.lowercase() ?: throw IOException("Adoptium не дал хеш для $name"),
                size = (pkg["size"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        }
        throw IOException("У Adoptium нет zip-сборки для этой системы")
    }

    fun arch(env: RuleEnv = RuleEnv()): String = when (env.arch) { "x86" -> "x86"; "arm64" -> "aarch64"; else -> "x64" }

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}

/**
 * Загрузка Eclipse Temurin (Adoptium) одной кнопкой: последняя сборка JRE нужной версии,
 * sha256 из API сверяется с архивом, распаковка в runtimes/temurin-<версия>.
 */
class AdoptiumInstaller(
    private val http: HttpClient,
    private val dirs: AppDirs,
    private val java: JavaManager,
    private val downloaderFactory: () -> Downloader,
) {
    fun homeFor(major: Int): Path = dirs.runtimesDir.resolve("temurin-$major")

    suspend fun latest(major: Int): AdoptiumBuild {
        val url = "$API/v3/assets/latest/$major/hotspot?architecture=${AdoptiumParsing.arch()}&image_type=jre&os=windows&vendor=eclipse"
        val r = http.get(url)
        if (!r.status.isSuccess()) throw IOException("Adoptium: HTTP ${r.status.value}")
        return AdoptiumParsing.latest(r.bodyAsText())
    }

    suspend fun install(major: Int, onProgress: (Float?) -> Unit = {}): JavaInstall = coroutineScope {
        val build = latest(major)
        val archive = dirs.cacheDir.resolve("java").resolve(build.fileName)
        val downloader = downloaderFactory()
        val watcher = launch { downloader.progress.collectLatest { onProgress(it.fraction) } }
        try {
            downloader.downloadAll(listOf(FileTask(build.url, archive, null, build.size)))
        } finally {
            watcher.cancel()
        }
        withContext(Dispatchers.IO) {
            val actual = Hashes.sha256(archive)
            if (actual != build.sha256) {
                Files.deleteIfExists(archive)
                throw IOException("Хеш Java не совпал: $actual вместо ${build.sha256}")
            }
            val home = homeFor(major)
            if (home.exists()) home.toFile().deleteRecursively()
            unpack(archive, home)
            home.resolve(".youlauncher-version").writeText(build.release.removePrefix("jdk-").removePrefix("jdk"))
            Files.deleteIfExists(archive)
            java.describe(home, JavaSource.Temurin) ?: throw IOException("Java ${build.release} не распознана после распаковки")
        }
    }

    /** Распаковка с отбрасыванием верхней папки архива (jdk-21...-jre) и защитой от выхода за каталог. */
    private fun unpack(archive: Path, home: Path) {
        ZipFile(archive.toFile()).use { zip ->
            zip.entries().asSequence().forEach { e ->
                val rel = e.name.substringAfter('/', "")
                if (rel.isEmpty()) return@forEach
                val out = home.resolve(rel).normalize()
                if (!out.startsWith(home)) return@forEach
                if (e.isDirectory) Files.createDirectories(out)
                else {
                    Files.createDirectories(out.parent)
                    zip.getInputStream(e).use { Files.copy(it, out, StandardCopyOption.REPLACE_EXISTING) }
                }
            }
        }
    }

    companion object {
        const val API = "https://api.adoptium.net"
        /** Версии Java, которые нужны играм: 8 (до 1.16), 17 (1.17–1.20.4), 21 (1.20.5–1.21.x), 25 (26.x). */
        val GAME_MAJORS = listOf(8, 17, 21, 25)
    }
}
