package app.youlauncher.ui.screens.instance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.Screenshot
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_close
import app.youlauncher.resources.menu_open_folder
import app.youlauncher.resources.screenshot_copy_path
import app.youlauncher.resources.screenshots_empty_body
import app.youlauncher.resources.screenshots_empty_title
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.SceneCanvas
import app.youlauncher.ui.background.SceneGenerator
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.images.rememberRemoteImage
import org.jetbrains.compose.resources.stringResource
import java.nio.file.Path

private val still: State<Float> = mutableFloatStateOf(0f)

@Composable
fun ScreenshotImage(shot: Screenshot, maxSize: Int, modifier: Modifier = Modifier) {
    if (shot.path.startsWith("demo://")) {
        // Демо-скриншоты: сгенерированная сцена вместо файла.
        val spec = remember(shot.path) { SceneGenerator.forKey(shot.path) }
        SceneCanvas(spec, rememberSceneScheme(spec.seedArgb), still, modifier)
        return
    }
    val image = rememberRemoteImage(shot.path, maxSize)
    if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier)
}

@Composable
fun ScreenshotsTab(inst: Instance, vm: InstanceViewModel) {
    val shots by vm.screenshots.collectAsState()
    var viewing by remember { mutableStateOf<Int?>(null) }
    if (shots.isEmpty()) {
        EmptyState(Illustration.Screenshots, stringResource(Res.string.screenshots_empty_title), stringResource(Res.string.screenshots_empty_body))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(240.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(shots, key = { _, s -> s.path }) { index, shot ->
            Card(onClick = { viewing = index }, shape = MaterialTheme.shapes.large, modifier = Modifier.staggered(index)) {
                Box {
                    ScreenshotImage(shot, 480, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    ) {
                        Text(timeAgo(shot.takenAt), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
    viewing?.let { start ->
        ScreenshotViewer(shots, start, vm.gameDir.resolve("screenshots"), onDismiss = { viewing = null })
    }
}

@Composable
private fun ScreenshotViewer(shots: List<Screenshot>, start: Int, folder: Path, onDismiss: () -> Unit) {
    var index by remember { mutableStateOf(start) }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.padding(48.dp).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { index = (index - 1 + shots.size) % shots.size; true }
                    Key.DirectionRight -> { index = (index + 1) % shots.size; true }
                    Key.Escape -> { onDismiss(); true }
                    else -> false
                }
            },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedContent(index, transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) }, label = "shot") { i ->
                    ScreenshotImage(shots[i], 1920, Modifier.size(960.dp, 540.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TIconButton(Symbols.ChevronLeft, "←", { index = (index - 1 + shots.size) % shots.size })
                    Text("${index + 1} / ${shots.size}", style = MaterialTheme.typography.labelLarge)
                    TIconButton(Symbols.ChevronRight, "→", { index = (index + 1) % shots.size })
                    Box(Modifier.weight(1f))
                    TIconButton(Symbols.ContentCopy, stringResource(Res.string.screenshot_copy_path), { Platform.copy(shots[index].path) })
                    TIconButton(Symbols.FolderOpen, stringResource(Res.string.menu_open_folder), { Platform.openFolder(folder) })
                    TIconButton(Symbols.Close, stringResource(Res.string.action_close), onDismiss)
                }
            }
        }
    }
}

