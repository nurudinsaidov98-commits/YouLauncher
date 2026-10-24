package app.youlauncher.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.youlauncher.core.VersionFeature
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.ui.components.Emblems
import app.youlauncher.ui.components.VersionEmblem
import app.youlauncher.ui.theme.YouLauncherTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/** Лист всех эмблем версий для проверки глазами: docs/screens/emblems-<тема>.png. */
@OptIn(ExperimentalLayoutApi::class)
fun main(args: Array<String>) {
    val out = Path.of(args[0]).createDirectories()
    for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
        val time = mutableFloatStateOf(0f)
        val scene = ImageComposeScene(1440, 1180, Density(1f)) {
            YouLauncherTheme(ThemeSettings(mode = mode), UiSettings()) {
                Surface(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VersionFeature.entries.forEach { f ->
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                                Column(Modifier.width(164.dp).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    VersionEmblem(f, time, Modifier.size(120.dp))
                                    Text(stringResource(Emblems.label(f)), style = MaterialTheme.typography.labelMedium, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }
        }
        var image = scene.render(0)
        for (frame in 1..90) {
            Thread.sleep(16)
            image = scene.render(frame * 16_666_667L)
        }
        scene.close()
        out.resolve("emblems-${mode.name.lowercase()}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
    }
    kotlin.system.exitProcess(0)
}
