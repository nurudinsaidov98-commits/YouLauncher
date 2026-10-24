package app.youlauncher.data.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Очистка кэша: удаляются файлы старше [maxAgeDays] (скачанные установщики, архивы Java, кэш картинок).
 * Индексы и манифесты при необходимости скачиваются заново, игровые файлы в кэше не хранятся.
 */
class CacheCleaner(private val cacheDir: Path, private val now: () -> Long = System::currentTimeMillis) {
    suspend fun size(): Long = withContext(Dispatchers.IO) {
        if (!cacheDir.isDirectory()) return@withContext 0L
        Files.walk(cacheDir).use { s -> s.filter { it.isRegularFile() }.mapToLong { runCatching { Files.size(it) }.getOrDefault(0) }.sum() }
    }

    /** Возвращает освобождённое место в байтах. [maxAgeDays] 0 удаляет всё. */
    suspend fun clean(maxAgeDays: Int): Long = withContext(Dispatchers.IO) {
        if (!cacheDir.isDirectory()) return@withContext 0L
        val border = now() - maxAgeDays * 24L * 3600 * 1000
        var freed = 0L
        Files.walk(cacheDir).use { s -> s.filter { it.isRegularFile() }.toList() }.forEach { f ->
            val modified = runCatching { Files.getLastModifiedTime(f).toMillis() }.getOrDefault(Long.MAX_VALUE)
            if (maxAgeDays == 0 || modified < border) {
                val size = runCatching { Files.size(f) }.getOrDefault(0)
                if (runCatching { Files.delete(f) }.isSuccess) freed += size
            }
        }
        freed
    }
}

/** Системные уведомления Windows (значок YouLauncher в области уведомлений). */
class SystemNotifier(private val enabled: () -> Boolean, private val icon: () -> java.awt.Image = { BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB) }) {
    private var tray: TrayIcon? = null

    /** Окно задаёт, в фокусе ли оно: уведомления нужны, только когда пользователь в другом окне. */
    @Volatile
    var appInFocus: () -> Boolean = { false }

    /** Показывается, только если включено в настройках и окно лаунчера не в фокусе. */
    fun notify(title: String, text: String, error: Boolean = false) {
        if (!enabled() || appInFocus() || !SystemTray.isSupported()) return
        runCatching {
            val icon = tray ?: TrayIcon(icon(), "YouLauncher").also {
                it.isImageAutoSize = true
                SystemTray.getSystemTray().add(it)
                tray = it
            }
            icon.displayMessage(title, text, if (error) TrayIcon.MessageType.ERROR else TrayIcon.MessageType.INFO)
        }
    }

    fun dispose() {
        tray?.let { runCatching { SystemTray.getSystemTray().remove(it) } }
        tray = null
    }
}
