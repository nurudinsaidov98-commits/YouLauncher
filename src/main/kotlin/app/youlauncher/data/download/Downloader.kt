package app.youlauncher.data.download

import app.youlauncher.core.mojang.FileTask
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.exists
import kotlin.io.path.fileSize

/** Общий прогресс пачки загрузок. */
data class DownloadProgress(
    val totalFiles: Int = 0,
    val doneFiles: Int = 0,
    val totalBytes: Long = 0,
    val doneBytes: Long = 0,
    /** Файлы, которые качаются прямо сейчас: путь -> доля (null, если размер неизвестен). */
    val active: Map<String, Float?> = emptyMap(),
) {
    val fraction: Float get() = when {
        totalBytes > 0 -> (doneBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
        totalFiles > 0 -> doneFiles.toFloat() / totalFiles
        else -> 0f
    }
}

class DownloadException(val task: FileTask, message: String, cause: Throwable? = null) : IOException(message, cause)

data class DownloaderConfig(
    val parallelism: Int = 8,
    val retries: Int = 4,
    val initialBackoffMs: Long = 500,
    /** Байт в секунду, 0: без ограничения. */
    val speedLimit: Long = 0,
    /** Хосты, с которых разрешено скачивать (null: любые https). */
    val allowedHosts: Set<String>? = null,
    /** Только для тестов с локальным сервером: в приложении всегда https. */
    val requireHttps: Boolean = true,
)

/**
 * Загрузчик: проверяет уже скачанные файлы по размеру и SHA-1, качает недостающие параллельно,
 * докачивает прерванные (.part + Range), повторяет при сбоях с экспоненциальной паузой
 * и атомарно переносит готовый файл на место. Отмена через отмену корутины.
 */
class Downloader(private val http: HttpClient, private val config: DownloaderConfig = DownloaderConfig()) {
    private val state = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = state.asStateFlow()
    private val throttle = Throttle(config.speedLimit)

    /** Режим «починить»: пересчитывать хеши у всех файлов. */
    var forceVerify = false

    suspend fun downloadAll(tasks: List<FileTask>) = coroutineScope {
        val unique = tasks.distinctBy { it.target.toAbsolutePath().normalize() }
        val pending = withContext(Dispatchers.IO) { unique.filterNot { isValid(it) } }
        state.value = DownloadProgress(
            totalFiles = pending.size,
            totalBytes = pending.sumOf { it.size.coerceAtLeast(0) },
        )
        val semaphore = Semaphore(config.parallelism.coerceIn(1, 64))
        pending.map { task -> async(Dispatchers.IO) { semaphore.withPermit { downloadWithRetry(task) } } }.awaitAll()
    }

    /** Файл уже на месте и совпадает по размеру и хешу. */
    fun isValid(task: FileTask): Boolean {
        val f = task.target
        if (!f.exists()) return false
        if (task.size > 0 && f.fileSize() != task.size) return false
        if (task.trustExisting && task.size > 0 && !forceVerify) return true
        return task.sha1 == null || sha1(f).equals(task.sha1, ignoreCase = true)
    }

    private suspend fun downloadWithRetry(task: FileTask) {
        var attempt = 0
        var backoff = config.initialBackoffMs
        while (true) {
            try {
                downloadOnce(task)
                state.update { it.copy(doneFiles = it.doneFiles + 1, active = it.active - task.target.toString()) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt > config.retries || e is DownloadException && e.message?.startsWith("HTTP 404") == true) {
                    state.update { it.copy(active = it.active - task.target.toString()) }
                    throw if (e is DownloadException) e else DownloadException(task, e.message ?: e::class.simpleName.orEmpty(), e)
                }
                delay(backoff)
                backoff *= 2
            }
        }
    }

    private suspend fun downloadOnce(task: FileTask) {
        checkHost(task)
        val target = task.target
        Files.createDirectories(target.parent)
        val part = target.resolveSibling(target.fileName.toString() + ".part")
        var existing = if (part.exists()) part.fileSize() else 0L
        if (task.size in 1..existing) { Files.deleteIfExists(part); existing = 0 }
        val key = target.toString()
        http.prepareGet(task.url) {
            if (existing > 0) header(HttpHeaders.Range, "bytes=$existing-")
        }.execute { response ->
            val status = response.status
            if (status == HttpStatusCode.NotFound) throw DownloadException(task, "HTTP 404 ${task.url}")
            if (status.value !in 200..299) throw DownloadException(task, "HTTP ${status.value} ${task.url}")
            val append = existing > 0 && status == HttpStatusCode.PartialContent
            if (!append && existing > 0) {
                // Сервер не поддержал докачку: учитываем уже засчитанные байты и начинаем заново.
                state.update { it.copy(doneBytes = it.doneBytes - existing) }
                existing = 0
            }
            if (append) state.update { it.copy(doneBytes = it.doneBytes + existing) }
            val channel = response.bodyAsChannel()
            val options = if (append) arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)
            else arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            val written = AtomicLong(existing)
            Files.newOutputStream(part, *options).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n == -1) break
                    if (n == 0) continue
                    throttle.acquire(n.toLong())
                    out.write(buffer, 0, n)
                    val total = written.addAndGet(n.toLong())
                    state.update {
                        it.copy(
                            doneBytes = it.doneBytes + n,
                            active = it.active + (key to (if (task.size > 0) total.toFloat() / task.size else null)),
                        )
                    }
                }
            }
        }
        val size = part.fileSize()
        if (task.size > 0 && size != task.size) {
            Files.deleteIfExists(part)
            state.update { it.copy(doneBytes = it.doneBytes - size) }
            throw DownloadException(task, "Размер не совпал: $size вместо ${task.size}")
        }
        if (task.sha1 != null) {
            val actual = sha1(part)
            if (!actual.equals(task.sha1, ignoreCase = true)) {
                Files.deleteIfExists(part)
                state.update { it.copy(doneBytes = it.doneBytes - size) }
                throw DownloadException(task, "SHA-1 не совпал: $actual вместо ${task.sha1}")
            }
        }
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun checkHost(task: FileTask) {
        val url = java.net.URI(task.url)
        if (config.requireHttps && url.scheme != "https") throw DownloadException(task, "Разрешены только https-адреса: ${task.url}")
        val allowed = config.allowedHosts ?: return
        val host = url.host.lowercase()
        if (allowed.none { host == it || host.endsWith(".$it") }) throw DownloadException(task, "Хост не разрешён: $host")
    }

    companion object {
        fun sha1(file: Path): String = Files.newInputStream(file).use { sha1(it) }

        fun sha1(input: InputStream): String {
            val md = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** Официальные хосты Mojang для игровых файлов. */
        val MOJANG_HOSTS = setOf("mojang.com", "minecraft.net")
    }
}

/** Простое ограничение скорости: не больше limit байт в секунду на все потоки вместе. */
private class Throttle(private val limit: Long) {
    private val mutex = Mutex()
    private var windowStart = System.nanoTime()
    private var used = 0L

    suspend fun acquire(bytes: Long) {
        if (limit <= 0) return
        mutex.withLock {
            val now = System.nanoTime()
            if (now - windowStart > 1_000_000_000L) { windowStart = now; used = 0 }
            used += bytes
            if (used > limit) {
                val wait = 1_000L - (now - windowStart) / 1_000_000L
                if (wait > 0) delay(wait)
                windowStart = System.nanoTime()
                used = 0
            }
        }
    }
}
