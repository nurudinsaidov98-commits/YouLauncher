package app.youlauncher

import app.youlauncher.data.system.CacheCleaner
import app.youlauncher.ui.UiSound
import app.youlauncher.ui.UiSounds
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemServicesTest {
    @Test
    fun `cache cleaner removes only old files`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("cache")
        val now = System.currentTimeMillis()
        val old = dir.resolve("installers/old.jar").also { Files.createDirectories(it.parent); Files.write(it, ByteArray(1000)) }
        Files.setLastModifiedTime(old, FileTime.fromMillis(now - 40L * 24 * 3600 * 1000))
        val fresh = dir.resolve("images/new.png").also { Files.createDirectories(it.parent); Files.write(it, ByteArray(500)) }
        val cleaner = CacheCleaner(dir) { now }
        assertEquals(1500, cleaner.size())
        assertEquals(1000, cleaner.clean(30))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertEquals(500, cleaner.clean(0))
        assertEquals(0, cleaner.size())
    }

    @Test
    fun `interface sounds are short and quiet`() {
        UiSound.entries.forEach { s ->
            val pcm = UiSounds.render(s)
            val seconds = pcm.size / 2 / 44_100.0
            assertTrue(seconds in 0.1..0.6, "$s: $seconds с")
            val peak = (0 until pcm.size / 2).maxOf { i -> kotlin.math.abs(((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort().toInt()) }
            assertTrue(peak <= Short.MAX_VALUE * 0.31, "$s слишком громкий: $peak")
        }
    }
}
