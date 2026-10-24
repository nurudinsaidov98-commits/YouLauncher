package app.youlauncher.ui.images

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/**
 * LRU-кэш картинок с бюджетом в байтах (ширина × высота × 4): вытесняются давно не показанные,
 * пока сумма не уложится в бюджет. Одна картинка больше бюджета всё равно кэшируется, чтобы не грузить её заново на каждом кадре.
 */
class ImageMemoryCache<V : Any>(private val budgetBytes: Long, private val sizeOf: (V) -> Long) {
    private val map = LinkedHashMap<String, V>(64, 0.75f, true)
    var bytes = 0L
        private set

    @Synchronized
    operator fun get(key: String): V? = map[key]

    @Synchronized
    fun put(key: String, value: V) {
        map.put(key, value)?.let { bytes -= sizeOf(it) }
        bytes += sizeOf(value)
        val it = map.entries.iterator()
        while (bytes > budgetBytes && map.size > 1 && it.hasNext()) {
            val eldest = it.next()
            if (eldest.key == key) continue
            bytes -= sizeOf(eldest.value)
            it.remove()
        }
    }

    @Synchronized
    fun clear() {
        map.clear()
        bytes = 0
    }

    @get:Synchronized
    val size: Int get() = map.size
}

/**
 * Загрузка изображений по https или из локальных файлов: кэш в памяти с бюджетом в байтах, кэш на диске,
 * уменьшение больших картинок до нужного размера (скриншоты в сетке, значки, обои).
 */
class ImageLoader(private val http: HttpClient?, private val cacheDir: Path, budgetBytes: Long = DEFAULT_BUDGET) {
    private val memory = ImageMemoryCache<ImageBitmap>(budgetBytes) { it.width.toLong() * it.height * 4 }
    private val locks = HashMap<String, Mutex>()

    private fun key(source: String, maxSize: Int) = "$source#$maxSize"

    fun cached(source: String, maxSize: Int = 0): ImageBitmap? = memory[key(source, maxSize)]

    /** Освобождает кэш (окно свёрнуто): картинки загрузятся снова с диска, когда понадобятся. */
    fun trim() = memory.clear()

    suspend fun load(source: String, maxSize: Int = 0): ImageBitmap? {
        cached(source, maxSize)?.let { return it }
        val mutex = synchronized(locks) { locks.getOrPut(source) { Mutex() } }
        return mutex.withLock {
            cached(source, maxSize) ?: withContext(Dispatchers.IO) { runCatching { decode(bytes(source), maxSize) }.getOrNull() }
                ?.also { memory.put(key(source, maxSize), it) }
        }
    }

    private suspend fun bytes(source: String): ByteArray {
        if (!source.startsWith("http")) return Path.of(source.removePrefix("file:")).readBytes()
        require(source.startsWith("https://")) { "Только https" }
        val file = cacheDir.resolve(sha1(source))
        if (file.exists()) return file.readBytes()
        val client = http ?: error("Сеть недоступна")
        val response = client.get(source)
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val bytes = response.readRawBytes()
        Files.createDirectories(cacheDir)
        Files.write(file, bytes)
        return bytes
    }

    // Промежуточные объекты Skia закрываются сразу: иначе их память живёт до сборки мусора.
    private fun decode(bytes: ByteArray, maxSize: Int): ImageBitmap = Image.makeFromEncoded(bytes).use { image ->
        if (maxSize <= 0 || (image.width <= maxSize && image.height <= maxSize)) return image.toComposeImageBitmap()
        val scale = maxSize.toFloat() / maxOf(image.width, image.height)
        val w = (image.width * scale).toInt().coerceAtLeast(1)
        val h = (image.height * scale).toInt().coerceAtLeast(1)
        Surface.makeRasterN32Premul(w, h).use { surface ->
            surface.canvas.drawImageRect(image, org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()))
            surface.makeImageSnapshot().use { it.toComposeImageBitmap() }
        }
    }

    companion object {
        /** 48 МБ: десятки обложек и значков или одни обои 2560 px с запасом. */
        const val DEFAULT_BUDGET = 48L * 1024 * 1024

        fun sha1(text: String): String = MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

@Composable
fun rememberRemoteImage(source: String, maxSize: Int = 0): ImageBitmap? {
    val loader = LocalImageLoader.current ?: return null
    val image by produceState(loader.cached(source, maxSize), source, maxSize) {
        if (value == null) value = loader.load(source, maxSize)
    }
    return image
}
