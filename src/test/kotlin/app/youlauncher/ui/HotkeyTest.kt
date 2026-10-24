package app.youlauncher.ui

import app.youlauncher.core.settings.DefaultHotkeys
import app.youlauncher.ui.palette.fuzzyScore
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotkeyTest {
    @Test
    fun `parses modifiers and keys`() {
        val k = Hotkey.parse("Ctrl+Shift+K")!!
        assertTrue(k.ctrl && k.shift && !k.alt)
        assertEquals(KeyEvent.VK_K, k.keyCode)
        assertEquals("Ctrl+Shift+K", k.toString())
        assertEquals(KeyEvent.VK_COMMA, Hotkey.parse("Ctrl+Comma")!!.keyCode)
        assertEquals(KeyEvent.VK_ENTER, Hotkey.parse("Ctrl+Enter")!!.keyCode)
        assertNull(Hotkey.parse("Hyper+K"))
        assertNull(Hotkey.parse(""))
    }

    @Test
    fun `all default hotkeys are valid and unique`() {
        val parsed = DefaultHotkeys.mapValues { assertNotNull(Hotkey.parse(it.value), it.key) }
        assertEquals(parsed.size, parsed.values.toSet().size)
    }

    @Test
    fun `fuzzy search prefers direct matches`() {
        assertNotNull(fuzzyScore("lib", "Library"))
        assertNull(fuzzyScore("xyz", "Library"))
        assertTrue(fuzzyScore("set", "Settings")!! > fuzzyScore("stg", "Settings")!!)
        assertEquals(0, fuzzyScore("", "Anything"))
    }
}
