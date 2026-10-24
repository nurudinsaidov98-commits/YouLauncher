package app.youlauncher.data.mojang

import app.youlauncher.core.AppDirs
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.mojang.AssetIndex
import app.youlauncher.core.mojang.AssetIndexRef
import app.youlauncher.core.mojang.JavaRuntimeIndex
import app.youlauncher.core.mojang.ManifestEntry
import app.youlauncher.core.mojang.VersionJson
import app.youlauncher.core.mojang.VersionManifest
import app.youlauncher.core.mojang.VersionResolver
import app.youlauncher.core.writeTextAtomic
import app.youlauncher.data.download.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/** Версия после слияния с родителями: JSON и id версии, чей client.jar используется. */
data class ResolvedVersion(val json: VersionJson, val clientId: String, val chain: List<String>)

/**
 * Метаданные Mojang: манифест версий, version JSON, индексы ассетов и реестр Java.
 * Всё кэшируется на диске; без сети используется кэш. Только официальные адреса Mojang.
 */
class MojangApi(private val http: HttpClient, private val dirs: AppDirs) {
    private val manifestCache: Path get() = dirs.cacheDir.resolve("version_manifest_v2.json")
    private val mutex = Mutex()
    @Volatile private var manifest: VersionManifest? = null

    suspend fun manifest(force: Boolean = false): VersionManifest = mutex.withLock {
        if (!force) manifest?.let { return it }
        val fresh = runCatching { fetchText(MANIFEST_URL) }.getOrNull()
        val text = if (fresh != null) {
            manifestCache.writeTextAtomic(fresh)
            fresh
        } else if (manifestCache.exists()) {
            manifestCache.readText()
        } else {
            throw OfflineException("Нет сети и нет сохранённого списка версий")
        }
        YouLauncherJson.decodeFromString(VersionManifest.serializer(), text).also { manifest = it }
    }

    fun versionDir(id: String): Path = dirs.versionsDir.resolve(id)
    fun versionJsonPath(id: String): Path = versionDir(id).resolve("$id.json")
    fun clientJar(id: String): Path = versionDir(id).resolve("$id.jar")

    /** version JSON: с диска (если хеш совпадает с манифестом) или с piston-meta. */
    suspend fun versionJson(id: String): VersionJson = withContext(Dispatchers.IO) {
        val path = versionJsonPath(id)
        val entry: ManifestEntry? = runCatching { manifest().versions.firstOrNull { it.id == id } }.getOrNull()
        val local = if (path.exists()) path.readText() else null
        val valid = local != null && (entry?.sha1 == null || Downloader.sha1(path).equals(entry.sha1, true))
        val text = if (valid) local!! else {
            val e = entry ?: (if (local != null) return@withContext decode(local) else throw OfflineException("Версия $id не найдена"))
            val body = runCatching { fetchText(e.url) }.getOrElse { if (local != null) return@withContext decode(local) else throw it }
            if (e.sha1 != null) {
                val actual = Downloader.sha1(body.byteInputStream())
                check(actual.equals(e.sha1, true)) { "SHA-1 version JSON $id не совпал" }
            }
            path.writeTextAtomic(body)
            body
        }
        decode(text)
    }

    private fun decode(text: String) = YouLauncherJson.decodeFromString(VersionJson.serializer(), text)

    /** Полная версия с учётом inheritsFrom (профили загрузчиков модов). */
    suspend fun resolve(id: String): ResolvedVersion {
        val chain = mutableListOf(id)
        var json = versionJson(id)
        var clientId = id
        while (json.inheritsFrom != null) {
            val parentId = json.inheritsFrom!!
            check(parentId !in chain) { "Циклическое наследование версий: $chain" }
            chain += parentId
            val parent = versionJson(parentId)
            if (json.downloads?.client == null) clientId = parentId
            json = VersionResolver.merge(json, parent)
        }
        return ResolvedVersion(json, clientId, chain)
    }

    suspend fun assetIndex(ref: AssetIndexRef): AssetIndex = withContext(Dispatchers.IO) {
        val path = dirs.assetsDir.resolve("indexes").resolve("${ref.id}.json")
        val ok = path.exists() && (ref.sha1 == null || Downloader.sha1(path).equals(ref.sha1, true))
        if (!ok) {
            val body = fetchText(ref.url)
            if (ref.sha1 != null) check(Downloader.sha1(body.byteInputStream()).equals(ref.sha1, true)) { "SHA-1 индекса ассетов не совпал" }
            path.writeTextAtomic(body)
        }
        YouLauncherJson.decodeFromString(AssetIndex.serializer(), path.readText())
    }

    suspend fun javaRuntimes(): JavaRuntimeIndex = withContext(Dispatchers.IO) {
        val cache = dirs.cacheDir.resolve("java-runtimes.json")
        val text = runCatching { fetchText(JAVA_RUNTIMES_URL).also { cache.writeTextAtomic(it) } }
            .getOrElse { if (cache.exists()) cache.readText() else throw it }
        YouLauncherJson.decodeFromString<JavaRuntimeIndex>(text)
    }

    suspend fun fetchText(url: String): String {
        val host = java.net.URI(url).host.orEmpty()
        require(url.startsWith("https://") && Downloader.MOJANG_HOSTS.any { host == it || host.endsWith(".$it") }) { "Не официальный адрес Mojang: $url" }
        val response = http.get(url)
        if (!response.status.isSuccess()) throw OfflineException("HTTP ${response.status.value}: $url")
        return response.bodyAsText()
    }

    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val JAVA_RUNTIMES_URL = "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
        const val RESOURCES_URL = "https://resources.download.minecraft.net/"
    }
}

class OfflineException(message: String) : Exception(message)
