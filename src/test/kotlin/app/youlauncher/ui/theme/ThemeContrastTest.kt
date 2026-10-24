package app.youlauncher.ui.theme

import app.youlauncher.core.settings.ContrastOption
import app.youlauncher.core.settings.PaletteStyleOption
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThemeContrastTest {
    private val styles = PaletteStyleOption.entries
    private val contrasts = listOf(ContrastOption.Standard, ContrastOption.Medium, ContrastOption.High)

    @Test
    fun `wcag formula matches reference values`() {
        assertEquals(21.0, Wcag.contrast(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Wcag.contrast(Color.White, Color.White), 0.001)
        // #777777 на белом: известное значение около 4.48.
        assertEquals(4.48, Wcag.contrast(Color(0xFF777777), Color.White), 0.02)
    }

    @Test
    fun `text roles meet WCAG AA for every seed, style and mode`() {
        val failures = mutableListOf<String>()
        for (seed in SeedColors) for (style in styles) for (dark in listOf(false, true)) for (contrast in contrasts) {
            val scheme = dynamicColorScheme(
                seedColor = seed.color,
                isDark = dark,
                style = style.toKolor(),
                contrastLevel = contrast.level,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
            for ((name, bg, fg) in Wcag.textPairs(scheme)) {
                val ratio = Wcag.contrast(bg, fg)
                if (ratio < 4.5) failures += "${seed.color} $style dark=$dark $contrast $name = ${"%.2f".format(ratio)}"
            }
        }
        assertTrue(failures.isEmpty(), "Недостаточный контраст:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `outline and primary are distinguishable from surface`() {
        val failures = mutableListOf<String>()
        for (seed in SeedColors) for (style in styles) for (dark in listOf(false, true)) {
            val scheme = dynamicColorScheme(seed.color, isDark = dark, style = style.toKolor(), specVersion = ColorSpec.SpecVersion.SPEC_2025)
            for ((name, bg, fg) in Wcag.uiPairs(scheme)) {
                val ratio = Wcag.contrast(bg, fg)
                if (ratio < 3.0) failures += "${seed.color} $style dark=$dark $name = ${"%.2f".format(ratio)}"
            }
        }
        assertTrue(failures.isEmpty(), "Недостаточный контраст элементов:\n" + failures.joinToString("\n"))
    }
}
