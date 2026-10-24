package app.youlauncher.ui.brand

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import app.youlauncher.ui.theme.DefaultSeed
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/** Логотип как Painter: иконка окна и панели задач рисуется тем же кодом, что и в интерфейсе. */
class LogoPainter(private val colors: LogoColors) : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)
    override fun DrawScope.onDraw() = drawYouLauncherLogo(colors)
}

/** Фирменные цвета логотипа: схема Material You из основного seed-цвета. */
fun brandLogoColors(dark: Boolean = false): LogoColors =
    LogoColors.from(dynamicColorScheme(seedColor = DefaultSeed, isDark = dark, style = PaletteStyle.TonalSpot))
