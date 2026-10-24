package app.youlauncher.ui.background

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Во сколько раз уменьшается слой под размытием у фонов и обложек. */
const val SOFT_BLUR_FACTOR = 4f

/**
 * Размытие в уменьшенном виде: содержимое рисуется в слое в [factor] раз меньше, размывается
 * с радиусом в [factor] раз меньше и растягивается обратно. На размытой картинке разницы не видно,
 * а пикселей и радиуса меньше, поэтому кадр с размытым фоном на весь экран дешевле примерно в factor² раз.
 * Координаты внутри уменьшенного слоя тоже меньше: рисование опирается на size, а не на пиксели.
 */
fun Modifier.softBlur(radius: Dp, factor: Float = 4f): Modifier {
    if (radius <= 0.dp) return this
    val f = factor.coerceAtLeast(1f)
    return this
        .layout { measurable, constraints ->
            val w = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
            val h = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
            val small = measurable.measure(Constraints.fixed((w / f).roundToInt().coerceAtLeast(1), (h / f).roundToInt().coerceAtLeast(1)))
            layout(w, h) {
                small.placeWithLayer(0, 0) {
                    scaleX = w / small.width.toFloat()
                    scaleY = h / small.height.toFloat()
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
        .blur(radius / f)
}
