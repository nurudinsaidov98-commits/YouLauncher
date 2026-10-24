package app.youlauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.icons.Symbols
import kotlin.math.sin

/** Оригинальные иллюстрации пустых состояний: композиции из форм M3 и символа, рисуются кодом. */
enum class Illustration(
    val main: RoundedPolygon,
    val accent: RoundedPolygon,
    val small: RoundedPolygon,
    val icon: () -> ImageVector,
) {
    Library(MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Pill, { Symbols.FilterHdrFilled }),
    Search(MaterialShapes.Clover8Leaf, MaterialShapes.Gem, MaterialShapes.Circle, { Symbols.SearchOff }),
    Accounts(MaterialShapes.Flower, MaterialShapes.Puffy, MaterialShapes.Pill, { Symbols.PersonAddFilled }),
    Console(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst, MaterialShapes.Circle, { Symbols.TerminalFilled }),
    Offline(MaterialShapes.Puffy, MaterialShapes.Cookie4Sided, MaterialShapes.Pill, { Symbols.CloudOff }),
    Crash(MaterialShapes.Boom, MaterialShapes.Cookie6Sided, MaterialShapes.Circle, { Symbols.BugReportFilled }),
    Worlds(MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Oval, { Symbols.Public }),
    Screenshots(MaterialShapes.Cookie7Sided, MaterialShapes.Pill, MaterialShapes.Gem, { Symbols.PhotoCameraFilled }),
    Mods(MaterialShapes.SoftBurst, MaterialShapes.Flower, MaterialShapes.Circle, { Symbols.ExtensionFilled }),
    Done(MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Circle, { Symbols.CheckCircleFilled }),
}

@Composable
fun IllustrationArt(kind: Illustration, size: Dp = 200.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Время читается в Canvas, а не здесь: тик анимации перерисовывает картинку без пересборки экрана.
    val time = rememberAmbientTime()
    val appear = remember(kind) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(kind) { appear.animateTo(1f, spec) }
    val polygons = remember(kind) {
        Triple(kind.main.normalized(), kind.accent.normalized(), kind.small.normalized())
    }
    val morph = remember(kind) { Morph(polygons.first, MaterialShapes.Circle.normalized()) }
    Box(modifier.size(size).graphicsLayer { scaleX = 0.6f + 0.4f * appear.value; scaleY = scaleX; alpha = appear.value.coerceIn(0f, 1f) }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val m = (sin(t * 0.6f) + 1f) / 2f * 0.25f
            drawPolygonPath(morphPath(morph, m), scheme.primaryContainer, 0.08f, 0.08f, 0.84f, t * 6f)
            drawPolygonPath(polygonPath(polygons.second), scheme.tertiaryContainer, 0.66f + sin(t * 0.8f) * 0.02f, 0.02f, 0.30f, -t * 10f)
            drawPolygonPath(polygonPath(polygons.third), scheme.secondaryContainer, 0.0f, 0.70f + sin(t * 0.9f + 1f) * 0.02f, 0.24f, t * 8f)
            drawPolygonPath(polygonPath(MaterialShapes.Circle.normalized()), scheme.tertiary.copy(alpha = 0.7f), 0.82f, 0.74f, 0.08f, 0f)
        }
        Icon(kind.icon(), contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(size * 0.3f))
    }
}

private val tmpMatrix = Matrix()

private fun morphPath(morph: Morph, progress: Float): Path = morph.toPath(progress, Path())

private fun polygonPath(p: RoundedPolygon): Path = Morph(p, p).toPath(0f, Path())

private fun DrawScope.drawPolygonPath(path: Path, color: Color, x: Float, y: Float, s: Float, rotation: Float) {
    val px = size.width * s
    tmpMatrix.reset()
    tmpMatrix.translate(size.width * x + px / 2, size.height * y + px / 2)
    tmpMatrix.rotateZ(rotation)
    tmpMatrix.translate(-px / 2, -px / 2)
    tmpMatrix.scale(px, px)
    path.transform(tmpMatrix)
    drawPath(path, color)
}

/** Пустое состояние: иллюстрация, заголовок, пояснение и действия. */
@Composable
fun EmptyState(
    illustration: Illustration,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    artSize: Dp = 180.dp,
    actions: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IllustrationArt(illustration, artSize)
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
            Text(
                body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}
