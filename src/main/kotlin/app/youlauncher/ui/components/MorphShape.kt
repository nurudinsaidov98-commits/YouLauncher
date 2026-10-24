package app.youlauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** Форма-морф между двумя RoundedPolygon (MaterialShapes) в заданной фазе. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    private val path = Path()
    private val matrix = Matrix()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        path.rewind()
        morph.toPath(progress, path)
        matrix.reset()
        matrix.scale(size.width, size.height)
        if (rotation != 0f) {
            matrix.translate(0.5f, 0.5f)
            matrix.rotateZ(rotation)
            matrix.translate(-0.5f, -0.5f)
        }
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Пружинный морфинг формы: при [active] форма плавно перетекает из [from] в [to]
 * по пространственной пружине выразительной схемы движения.
 */
@Composable
fun animatedMorphShape(from: RoundedPolygon, to: RoundedPolygon, active: Boolean, rotation: Float = 0f): Shape {
    val morph = remember(from, to) { Morph(from.normalizedSafe(), to.normalizedSafe()) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "morph",
    )
    return remember(morph, progress, rotation) { MorphShape(morph, progress, rotation * progress) }
}

private fun RoundedPolygon.normalizedSafe(): RoundedPolygon = runCatching { normalized() }.getOrDefault(this)
