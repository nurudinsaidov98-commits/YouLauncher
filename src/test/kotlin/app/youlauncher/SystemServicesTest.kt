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

class ClassCacheTest {
    @Test
    fun `class list lines become class names, service lines are skipped`() {
        val c = app.youlauncher.data.system.ClassLists
        assertEquals("java.lang.Object", c.className("java/lang/Object id: 0"))
        assertEquals("app.youlauncher.MainKt", c.className("app/youlauncher/MainKt id: 622"))
        assertEquals(null, c.className("# NOTE: Do not modify this file."))
        assertEquals(null, c.className("@lambda-proxy java/lang/Foo bar"))
        assertEquals(null, c.className("app/youlauncher/Foo\$\$Lambda/0x1 id: 5"))
        assertEquals(null, c.className("   "))
    }

    @Test
    fun `archive is rebuilt after a move or a new build`() {
        val dir = Files.createTempDirectory("app")
        Files.createDirectories(dir.resolve("app"))
        Files.writeString(dir.resolve("YouLauncher.exe"), "")
        Files.writeString(dir.resolve("app/youlauncher-1.0-aaa.jar"), "")
        val cache = app.youlauncher.data.system.ClassCache(dir, "1.0")
        assertFalse(cache.needsBuild(), "без списка классов собирать нечего")
        Files.writeString(dir.resolve("app/classes.lst"), "java/lang/Object id: 0\n")
        assertTrue(cache.needsBuild(), "списка достаточно, архива ещё нет")
        Files.writeString(dir.resolve("app/youlauncher.jsa"), "x")
        Files.writeString(dir.resolve("app/youlauncher.jsa.stamp"), cache.expectedStamp())
        assertFalse(cache.needsBuild())
        // Новая сборка той же версии: другой jar.
        Files.move(dir.resolve("app/youlauncher-1.0-aaa.jar"), dir.resolve("app/youlauncher-1.0-bbb.jar"))
        assertTrue(cache.needsBuild())
        assertTrue(app.youlauncher.data.system.ClassCache(dir, "1.0").preload() >= 1)
    }
}
