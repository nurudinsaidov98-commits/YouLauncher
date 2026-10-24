package app.youlauncher.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Цвета логотипа берутся из роли цвета темы, поэтому логотип следует за seed. */
@Immutable
data class LogoColors(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val farHill: Color,
    val nearHill: Color,
    val ridge: Color,
) {
    companion object {
        fun from(scheme: ColorScheme) = LogoColors(
            skyTop = scheme.tertiaryContainer,
            skyBottom = scheme.primaryContainer,
            sun = scheme.tertiary,
            farHill = scheme.secondary,
            nearHill = scheme.primary,
            ridge = scheme.inversePrimary,
        )
    }
}

/**
 * Логотип YouLauncher: мягкий скруглённый квадрат (squircle) со слоистым рельефом и солнцем.
 * Нарисован кодом, без растровых ассетов. [progress] от 0 до 1 поднимает холмы (для заставки).
 */
@Composable
fun YouLauncherLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    colors: LogoColors = LogoColors.from(MaterialTheme.colorScheme),
    progress: Float = 1f,
) {
    Canvas(modifier.size(size)) { drawYouLauncherLogo(colors, progress) }
}

fun DrawScope.drawYouLauncherLogo(colors: LogoColors, progress: Float = 1f) {
    val w = size.width
    val h = size.height
    val corner = w * 0.3f
    val clip = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(corner, corner)))
    }
    clipPath(clip) {
        drawRect(Brush.verticalGradient(listOf(colors.skyTop, colors.skyBottom)), size = Size(w, h))
        val lift = (1f - progress) * h * 0.35f
        drawCircle(colors.sun, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.33f + lift * 0.5f))

        val far = Path().apply {
            moveTo(0f, h * 0.60f + lift)
            cubicTo(w * 0.20f, h * 0.46f + lift, w * 0.36f, h * 0.48f + lift, w * 0.52f, h * 0.57f + lift)
            cubicTo(w * 0.68f, h * 0.66f + lift, w * 0.84f, h * 0.50f + lift, w, h * 0.48f + lift)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(far, colors.farHill)

        val near = Path().apply {
            moveTo(0f, h * 0.80f + lift)
            cubicTo(w * 0.18f, h * 0.65f + lift, w * 0.38f, h * 0.63f + lift, w * 0.55f, h * 0.73f + lift)
            cubicTo(w * 0.72f, h * 0.83f + lift, w * 0.86f, h * 0.74f + lift, w, h * 0.68f + lift)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(near, colors.nearHill)
    }
}
