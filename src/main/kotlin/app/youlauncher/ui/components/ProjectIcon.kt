package app.youlauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.youlauncher.ui.background.SceneGenerator
import app.youlauncher.ui.images.rememberRemoteImage

private val iconShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Puffy,
    MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie6Sided,
)

private const val ICON_MAX_PX = 160

/**
 * Значок проекта из каталога: картинка проекта, если она есть, иначе первая буква
 * в форме M3, выбранной по хешу названия (цвета сцены из того же хеша).
 */
@Composable
fun ProjectIcon(title: String, iconUrl: String?, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    // Значок не больше 2x от размера на экране: полноразмерные значки каталогов занимали по мегабайту и больше.
    val image = iconUrl?.let { rememberRemoteImage(it, ICON_MAX_PX) }
    val shapeIndex = ((SceneGenerator.hash(title) ushr 1) % iconShapes.size).toInt()
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(MaterialTheme.shapes.large))
        return
    }
    val scheme = rememberSceneScheme(SceneGenerator.seedFor(title))
    Box(
        modifier.size(size).clip(iconShapes[shapeIndex].toShape()).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = scheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}
