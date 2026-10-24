package app.youlauncher.core

import app.youlauncher.core.model.LogLevel
import app.youlauncher.data.log.InMemoryLogBus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    @Test
    fun `offline uuid matches the server algorithm`() {
        // Известное значение: офлайн-UUID ника Notch.
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", offlineUuid("Notch"))
        assertEquals(offlineUuid("Steve"), offlineUuid("Steve"))
        assertTrue(offlineUuid("steve") != offlineUuid("Steve"))
    }

    @Test
    fun `nickname validation`() {
        assertTrue(isValidNick("Player_1"))
        assertFalse(isValidNick("ab"))
        assertFalse(isValidNick("a".repeat(17)))
        assertFalse(isValidNick("Игрок"))
        assertFalse(isValidNick("bad name"))
    }

    @Test
    fun `secrets are masked in logs`() {
        val masked = maskSecrets("""{"accessToken":"abcdef123456","clientToken":"zzzzzzzz","password":"hunter2"} --accessToken eyJhbGciOi.secret Bearer abc.def.ghi""")
        assertFalse(masked.contains("abcdef123456"))
        assertFalse(masked.contains("hunter2"))
        assertFalse(masked.contains("eyJhbGciOi.secret"))
        assertFalse(masked.contains("abc.def.ghi"))
        assertTrue(masked.contains("ab****56"))
        assertEquals("plain text", maskSecrets("plain text"))
    }

    @Test
    fun `log bus masks, numbers and limits lines`() {
        val bus = InMemoryLogBus(capacity = 3)
        repeat(5) { bus.log(LogLevel.Info, "line $it token=SECRETVALUE$it") }
        val lines = bus.lines.value
        assertEquals(3, lines.size)
        assertEquals(listOf("line 2", "line 3", "line 4"), lines.map { it.text.substringBefore(" token") })
        assertTrue(lines.none { it.text.contains("SECRETVALUE") })
        assertTrue(lines.zipWithNext().all { (a, b) -> a.id < b.id })
        bus.clear()
        assertTrue(bus.lines.value.isEmpty())
    }

    @Test
    fun `game log lines are parsed`() {
        val (level, thread, text) = InMemoryLogBus.parseGameLine("[12:34:56] [Render thread/WARN]: Something odd")
        assertEquals(LogLevel.Warn, level)
        assertEquals("Render thread", thread)
        assertEquals("Something odd", text)
        assertEquals(LogLevel.Error, InMemoryLogBus.parseGameLine("\tat net.example.Main.run(Main.java:1)").first)
        assertEquals(LogLevel.Info, InMemoryLogBus.parseGameLine("plain").first)
    }
}
