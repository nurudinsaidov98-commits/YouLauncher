package app.youlauncher.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Собирает ImageVector из путей SVG Material Symbols. Сетка берётся из viewBox исходника:
 * у новых символов "0 -960 960 960" (группа сдвигается на 960 вниз), у части старых "0 0 24 24".
 * Цвет задаёт Icon через tint, поэтому заливка здесь только маска.
 */
internal fun symbol(name: String, viewport: Float, minY: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = viewport,
        viewportHeight = viewport,
        autoMirror = false,
    ).apply {
        addGroup(translationY = -minY)
        for (d in paths) {
            addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        clearGroup()
    }.build()
