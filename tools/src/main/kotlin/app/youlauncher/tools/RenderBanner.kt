package app.youlauncher.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.ui.background.ArtLayer
import app.youlauncher.ui.background.ArtStyle
import app.youlauncher.ui.brand.YouLauncherLogo
import app.youlauncher.ui.theme.YouLauncherTheme
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/** Баннер для страницы проекта: docs/banner-<тема>.png, всё нарисовано кодом из темы M3. */
fun main(args: Array<String>) {
    val out = Path.of(args[0]).createDirectories()
    for (mode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
        val scene = ImageComposeScene(1600, 560, Density(1f)) {
            YouLauncherTheme(ThemeSettings(mode = mode), UiSettings()) { Banner() }
        }
        var image = scene.render(0)
        for (frame in 1..60) {
            Thread.sleep(16)
            image = scene.render(frame * 16_666_667L)
        }
        scene.close()
        out.resolve("banner-${mode.name.lowercase()}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("Баннер: ${out.resolve("banner-${mode.name.lowercase()}.png")}")
    }
    kotlin.system.exitProcess(0)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Banner() {
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(s.surface)) {
        ArtLayer(ArtStyle.Blobs, s, 17L, mutableFloatStateOf(3f), mutableStateOf(Offset.Zero), 0.8f, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to s.surface.copy(alpha = 0.92f), 0.55f to s.surface.copy(alpha = 0.55f), 1f to Color.Transparent)))

        // Композиция из форм M3 Expressive справа.
        Shape(MaterialShapes.Cookie9Sided, 300.dp, s.primaryContainer, Modifier.align(Alignment.CenterEnd).offset(x = (-150).dp, y = (-40).dp), 12f)
        Shape(MaterialShapes.Sunny, 190.dp, s.tertiaryContainer, Modifier.align(Alignment.TopEnd).offset(x = (-40).dp, y = 30.dp), -8f)
        Shape(MaterialShapes.Clover4Leaf, 150.dp, s.secondaryContainer, Modifier.align(Alignment.BottomEnd).offset(x = (-380).dp, y = (-36).dp), 20f)
        Shape(MaterialShapes.Pill, 120.dp, s.primary, Modifier.align(Alignment.BottomEnd).offset(x = (-60).dp, y = (-50).dp), -30f)
        Shape(MaterialShapes.SoftBurst, 90.dp, s.tertiary, Modifier.align(Alignment.TopEnd).offset(x = (-420).dp, y = 70.dp), 0f)
        Shape(MaterialShapes.Gem, 70.dp, s.secondary, Modifier.align(Alignment.CenterEnd).offset(x = (-60).dp, y = 70.dp), 15f)

        Row(Modifier.align(Alignment.CenterStart).padding(start = 88.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            YouLauncherLogo(size = 168.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouLauncher", style = MaterialTheme.typography.displayLargeEmphasized, color = s.onSurface)
                Text("Minecraft launcher built on Material 3 Expressive", style = MaterialTheme.typography.headlineSmall, color = s.onSurfaceVariant)
                Text("Лаунчер Minecraft на Material 3 Expressive", style = MaterialTheme.typography.titleLarge, color = s.onSurfaceVariant)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tag("Material 3 Expressive", s.primaryContainer, s.onPrimaryContainer)
                    Tag("Ely.by", s.secondaryContainer, s.onSecondaryContainer)
                    Tag("Modrinth · CurseForge", s.tertiaryContainer, s.onTertiaryContainer)
                    Tag("Fabric · Quilt · Forge · NeoForge", s.surfaceContainerHighest, s.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Shape(polygon: RoundedPolygon, size: Dp, color: Color, modifier: Modifier, rotation: Float) {
    Box(modifier.size(size).rotate(rotation).clip(polygon.toShape()).background(color))
}

@Composable
private fun Tag(text: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}
