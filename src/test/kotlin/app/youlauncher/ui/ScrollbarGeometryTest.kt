package app.youlauncher.ui

import app.youlauncher.ui.components.ScrollGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScrollbarGeometryTest {
    @Test
    fun `short list has no scrollbar`() {
        assertFalse(ScrollGeometry.of(total = 3, firstIndex = 0, firstOffset = 0, visibleSizes = listOf(56, 56, 56), viewport = 400, spacing = 2).scrollable)
        assertFalse(ScrollGeometry.of(total = 0, firstIndex = 0, firstOffset = 0, visibleSizes = emptyList(), viewport = 400, spacing = 2).scrollable)
    }

    @Test
    fun `thumb follows the list from top to bottom`() {
        // 100 версий по 58 px (56 + отступ 2), видно 400 px.
        val sizes = List(7) { 56 }
        val top = ScrollGeometry.of(100, 0, 0, sizes, 400, 2)
        assertTrue(top.scrollable)
        assertEquals(0f, top.offsetFraction)
        assertEquals(400f / (58 * 100 - 2), top.visibleFraction, 1e-4f)
        val middle = ScrollGeometry.of(100, 46, 0, sizes, 400, 2)
        assertEquals(0.5f, middle.offsetFraction, 0.02f)
        val bottom = ScrollGeometry.of(100, 93, 50, sizes, 400, 2)
        assertEquals(1f, bottom.offsetFraction, 0.01f)
    }
}
