package app.youlauncher

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import app.youlauncher.ui.screens.settings.ColorPickerLogic
import app.youlauncher.ui.theme.ColorOverrides
import app.youlauncher.ui.theme.ColorRole
import app.youlauncher.ui.theme.SeedColors
import app.youlauncher.ui.theme.toSeedLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorOverridesTest {
    private val base = lightColorScheme()

    @Test
    fun `overridden role changes and its text gets readable contrast`() {
        val custom = ColorOverrides.tonalRow(SeedColors[2].color)[2] // светлота 40
        val scheme = ColorOverrides.apply(base, mapOf("primary" to custom.toSeedLong()))
        assertEquals(custom.toArgb(), scheme.primary.toArgb())
        assertTrue(ColorPickerLogic.contrast(scheme.primary, scheme.onPrimary) >= 4.5, "Текст на своём цвете читается")
        assertEquals(base.secondary, scheme.secondary, "Остальные роли не тронуты")
    }

    @Test
    fun `explicit text color wins over the automatic one`() {
        val bg = ColorOverrides.tonalRow(SeedColors[0].color)[7]
        val text = ColorOverrides.tonalRow(SeedColors[1].color)[0]
        val scheme = ColorOverrides.apply(base, mapOf("primaryContainer" to bg.toSeedLong(), "onPrimaryContainer" to text.toSeedLong()))
        assertEquals(text.toArgb(), scheme.onPrimaryContainer.toArgb())
    }

    @Test
    fun `every role is applied and unknown keys are ignored`() {
        val color = ColorOverrides.tonalRow(SeedColors[3].color)[4]
        val all = ColorRole.entries.associate { it.key to color.toSeedLong() } + ("bogus" to 0L)
        val scheme = ColorOverrides.apply(base, all)
        ColorRole.entries.forEach { assertEquals(color.toArgb(), it.get(scheme).toArgb(), it.key) }
        assertEquals(35, ColorRole.entries.size)
        assertEquals(ColorRole.entries.size, ColorRole.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `hex parsing and formatting`() {
        val c = ColorPickerLogic.parseHex("#1e6fb8")!!
        assertEquals("#1E6FB8", ColorPickerLogic.hex(c))
        assertNull(ColorPickerLogic.parseHex("#12345"))
        assertNull(ColorPickerLogic.parseHex("zzzzzz"))
        assertEquals(21.0, ColorPickerLogic.contrast(ColorPickerLogic.parseHex("000000")!!, ColorPickerLogic.parseHex("FFFFFF")!!), 0.01)
    }

    @Test
    fun `tonal rows run from dark to light`() {
        SeedColors.forEach { seed ->
            val row = ColorOverrides.tonalRow(seed.color)
            assertEquals(8, row.size)
            assertTrue(row.zipWithNext().all { (a, b) -> a.toArgb() != b.toArgb() })
        }
    }
}
