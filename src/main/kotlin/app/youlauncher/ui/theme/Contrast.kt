package app.youlauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Контраст по WCAG 2.x: (L1 + 0.05) / (L2 + 0.05), где L: относительная яркость sRGB. */
object Wcag {
    fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Пары «фон / текст», для которых требуется уровень AA (4.5:1). */
    fun textPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("primary/onPrimary", s.primary, s.onPrimary),
        Triple("primaryContainer/onPrimaryContainer", s.primaryContainer, s.onPrimaryContainer),
        Triple("secondary/onSecondary", s.secondary, s.onSecondary),
        Triple("secondaryContainer/onSecondaryContainer", s.secondaryContainer, s.onSecondaryContainer),
        Triple("tertiary/onTertiary", s.tertiary, s.onTertiary),
        Triple("tertiaryContainer/onTertiaryContainer", s.tertiaryContainer, s.onTertiaryContainer),
        Triple("error/onError", s.error, s.onError),
        Triple("errorContainer/onErrorContainer", s.errorContainer, s.onErrorContainer),
        Triple("surface/onSurface", s.surface, s.onSurface),
        Triple("surfaceContainer/onSurface", s.surfaceContainer, s.onSurface),
        Triple("surfaceContainerHigh/onSurfaceVariant", s.surfaceContainerHigh, s.onSurfaceVariant),
        Triple("surface/onSurfaceVariant", s.surface, s.onSurfaceVariant),
        Triple("inverseSurface/inverseOnSurface", s.inverseSurface, s.inverseOnSurface),
        Triple("background/onBackground", s.background, s.onBackground),
    )

    /** Пары для нетекстовых элементов (обводки, индикаторы): 3:1. */
    fun uiPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("surface/outline", s.surface, s.outline),
        Triple("surface/primary", s.surface, s.primary),
    )
}
