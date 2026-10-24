package app.youlauncher.data.accounts

import app.youlauncher.core.Hashes
import app.youlauncher.core.YouLauncherJson
import app.youlauncher.core.mojang.FileTask
import app.youlauncher.core.readJsonOrNull
import app.youlauncher.core.writeJson
import app.youlauncher.data.download.Downloader
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class InjectorRelease(val version: String, val fileName: String, val url: String, val sha256: String)

@Serializable
data class InjectorState(val release: InjectorRelease, val checkedAt: Long)

object InjectorParsing {
    /** Ответ GitHub /releases/latest: jar authlib-injector и его sha256 из поля digest. */
    fun release(json: String): InjectorRelease {
        val o = YouLauncherJson.parseToJsonElement(json).jsonObject
        val tag = (o["tag_name"] as? JsonPrimitive)?.contentOrNull ?: throw IOException("В релизе нет tag_name")
        val asset = (o["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .firstOrNull { a -> a.str("name").let { it != null && it.startsWith("authlib-injector") && it.endsWith(".jar") } }
            ?: throw IOException("В релизе $tag нет jar authlib-injector")
        val digest = asset.str("digest") ?: throw IOException("GitHub не дал хеш для ${asset.str("name")}")
        if (!digest.startsWith("sha256:")) throw IOException("Неизвестный тип хеша: $digest")
        return InjectorRelease(tag.removePrefix("v"), asset.str("name")!!, asset.str("browser_download_url") ?: throw IOException("Нет адреса загрузки"), digest.removePrefix("sha256:").lowercase())
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * authlib-injector для запуска с аккаунтом Ely.by: последний релиз с GitHub
 * (https://github.com/yushijinhun/authlib-injector/releases/latest), sha256 из релиза сверяется с файлом.
 * Новая версия проверяется не чаще раза в сутки; без сети используется уже скачанная.
 */
class AuthlibInjector(
    private val http: HttpClient,
    private val toolsDir: Path,
    private val downloaderFactory: () -> Downloader,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val stateFile get() = toolsDir.resolve("authlib-injector.json")

    suspend fun ensure(): Path = mutex.withLock {
        val saved = stateFile.readJsonOrNull(InjectorState.serializer())
        val savedJar = saved?.let { toolsDir.resolve(it.release.fileName) }
        val fresh = saved != null && now() - saved.checkedAt < DAY_MS
        if (fresh && savedJar != null && savedJar.exists() && sha256(savedJar) == saved.release.sha256) return savedJar

        val release = try {
            latest()
        } catch (e: Exception) {
            if (savedJar != null && savedJar.exists() && sha256(savedJar) == saved.release.sha256) return savedJar
            throw IOException("Не удалось получить authlib-injector: ${e.message}", e)
        }
        val jar = toolsDir.resolve(release.fileName)
        if (!jar.exists() || sha256(jar) != release.sha256) {
            Files.deleteIfExists(jar)
            downloaderFactory().downloadAll(listOf(FileTask(release.url, jar, null)))
            val actual = sha256(jar)
            if (actual != release.sha256) {
                Files.deleteIfExists(jar)
                throw IOException("Хеш authlib-injector не совпал: $actual вместо ${release.sha256}")
            }
        }
        stateFile.writeJson(InjectorState.serializer(), InjectorState(release, now()))
        jar
    }

    private suspend fun latest(): InjectorRelease {
        val r = http.get(LATEST_URL) { header("Accept", "application/vnd.github+json") }
        if (!r.status.isSuccess()) throw IOException("GitHub: HTTP ${r.status.value}")
        return InjectorParsing.release(r.bodyAsText())
    }

    private suspend fun sha256(file: Path): String = withContext(Dispatchers.IO) { Hashes.sha256(file) }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** JVM-аргумент для Ely.by. */
        fun javaAgent(jar: Path) = "-javaagent:$jar=ely.by"
    }
}
