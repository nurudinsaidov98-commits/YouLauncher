package app.youlauncher.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.ui.background.ArtLayer
import app.youlauncher.ui.background.BuiltinWallpapers
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.theme.YouLauncherTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/** Лист всех встроенных обоев в движении (кадр на 20-й секунде): docs/screens/wallpapers-<тема>.png. */
@OptIn(ExperimentalLayoutApi::class)
fun main(args: Array<String>) {
    val out = Path.of(args[0]).createDirectories()
    for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
        val time = mutableFloatStateOf(20f)
        val noParallax = mutableStateOf(Offset.Zero)
        val scene = ImageComposeScene(1440, 1000, Density(1f)) {
            YouLauncherTheme(ThemeSettings(mode = mode), UiSettings()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BuiltinWallpapers.all.forEach { w ->
                            Column(Modifier.width(192.dp)) {
                                Box(Modifier.size(192.dp, 108.dp).clip(MaterialTheme.shapes.medium)) {
                                    ArtLayer(w.style, rememberSceneScheme(w.seedArgb), w.artSeed, time, noParallax, 0.4f, Modifier.fillMaxSize())
                                }
                                Text(stringResource(w.name), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
        var image = scene.render(0)
        for (frame in 1..30) {
            Thread.sleep(16)
            image = scene.render(frame * 16_666_667L)
        }
        scene.close()
        out.resolve("wallpapers-${mode.name.lowercase()}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    kotlin.system.exitProcess(0)
}
