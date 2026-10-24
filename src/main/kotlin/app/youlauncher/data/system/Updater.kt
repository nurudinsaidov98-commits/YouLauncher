package app.youlauncher.data.system

import app.youlauncher.core.Hashes
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.settings.UpdateChannel
import app.youlauncher.data.download.Downloader
import app.youlauncher.data.loaders.NumericComparator
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

data class UpdateInfo(
    val version: String,
    val notes: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val pageUrl: String,
)

object UpdateParsing {
    /** Имя архива портативной сборки в релизе (задача exportZip). */
    fun assetName(version: String) = "YouLauncher-$version-windows.zip"

    fun isNewer(candidate: String, current: String): Boolean = current == "dev" || compareVersions(candidate, current) > 0

    /** Сравнение как в semver: сначала числа до дефиса, затем релиз старше своего пре-релиза (1.1.0 > 1.1.0-beta.2 > 1.1.0-beta.1). */
    fun compareVersions(a: String, b: String): Int {
        val (coreA, preA) = a.removePrefix("v").split('-', limit = 2).let { it[0] to it.getOrNull(1) }
        val (coreB, preB) = b.removePrefix("v").split('-', limit = 2).let { it[0] to it.getOrNull(1) }
        val core = NumericComparator.compare(coreA, coreB)
        return when {
            core != 0 -> core
            preA == preB -> 0
            preA == null -> 1
            preB == null -> -1
            else -> NumericComparator.compare(preA, preB)
        }
    }

    /**
     * Самый новый подходящий релиз: стабильный канал берёт только не-пре-релизы, бета все; черновики пропускаются.
     * Хеш sha256 берётся из поля digest ассета; без хеша обновление не предлагается.
     */
    fun pick(json: String, channel: UpdateChannel, current: String): UpdateInfo? {
        if (channel == UpdateChannel.Off) return null
        val arr = YouLauncherJson.parseToJsonElement(json) as? JsonArray ?: return null
        return arr.mapNotNull { it as? JsonObject }
            .filter { it.b("draft") != true && (channel == UpdateChannel.Beta || it.b("prerelease") != true) }
            .mapNotNull { r ->
                val version = r.s("tag_name")?.removePrefix("v") ?: return@mapNotNull null
                val asset = (r["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { it.s("name") == assetName(version) } ?: return@mapNotNull null
                val digest = asset.s("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:") ?: return@mapNotNull null
                UpdateInfo(version, r.s("body").orEmpty(), asset.s("browser_download_url") ?: return@mapNotNull null, digest.lowercase(),
                    (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0, r.s("html_url").orEmpty())
            }
            .filter { isNewer(it.version, current) }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    /**
     * Сценарий замены файлов: ждёт выхода лаунчера, переносит новую сборку в папку приложения
     * (данные пользователя лежат отдельно, в AppData или рядом с portable.txt, и не трогаются), запускает YouLauncher.
     */
    fun script(pid: Long, newApp: Path, appDir: Path): String = """
        ${'$'}ErrorActionPreference = 'Stop'
        Wait-Process -Id $pid -Timeout 60 -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
        robocopy "$newApp" "$appDir" /E /XF portable.txt /NFL /NDL /NJH /NJS /NP | Out-Null
        Start-Process -FilePath "${appDir.resolve("YouLauncher.exe")}"
    """.trimIndent()

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull
}

/**
 * Автообновление из GitHub Releases: проверка, загрузка архива с проверкой sha256 из релиза,
 * замена файлов портативной сборки после выхода и перезапуск. Репозиторий задаётся при сборке.
 */
class Updater(
    private val http: HttpClient,
    private val repo: String?,
    private val cacheDir: Path,
    private val downloaderFactory: () -> Downloader,
) {
    val configured: Boolean get() = repo != null

    suspend fun check(channel: UpdateChannel, current: String): UpdateInfo? {
        val r = repo ?: return null
        val response = http.get("https://api.github.com/repos/$r/releases?per_page=20") { header("Accept", "application/vnd.github+json") }
        if (!response.status.isSuccess()) throw IOException("GitHub: HTTP ${response.status.value}")
        return UpdateParsing.pick(response.bodyAsText(), channel, current)
    }

    suspend fun download(info: UpdateInfo): Path {
        val zip = cacheDir.resolve("updates").resolve(UpdateParsing.assetName(info.version))
        downloaderFactory().downloadAll(listOf(FileTask(info.url, zip, null, info.size)))
        return withContext(Dispatchers.IO) {
            val actual = Hashes.sha256(zip)
            if (actual != info.sha256) {
                Files.deleteIfExists(zip)
                throw IOException("Хеш обновления не совпал: $actual вместо ${info.sha256}")
            }
            zip
        }
    }

    /** Распаковка и запуск сценария замены; после вызова лаунчер должен завершиться. */
    suspend fun install(zip: Path, appDir: Path): Unit = withContext(Dispatchers.IO) {
        val target = cacheDir.resolve("updates").resolve("unpacked")
        target.toFile().deleteRecursively()
        java.util.zip.ZipFile(zip.toFile()).use { z ->
            z.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                val out = target.resolve(e.name).normalize()
                if (!out.startsWith(target)) return@forEach
                Files.createDirectories(out.parent)
                z.getInputStream(e).use { Files.copy(it, out) }
            }
        }
        val newApp = target.resolve("YouLauncher-App").takeIf { Files.isDirectory(it) } ?: target
        if (!Files.exists(newApp.resolve("YouLauncher.exe"))) throw IOException("В архиве обновления нет YouLauncher.exe")
        val script = cacheDir.resolve("updates").resolve("apply-update.ps1")
        Files.writeString(script, UpdateParsing.script(ProcessHandle.current().pid(), newApp, appDir))
        ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString()).start()
    }
}
