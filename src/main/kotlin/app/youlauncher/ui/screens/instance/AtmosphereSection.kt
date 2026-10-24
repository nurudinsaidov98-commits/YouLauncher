package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.resources.Res
import app.youlauncher.resources.atmo_color
import app.youlauncher.resources.atmo_add_image
import app.youlauncher.resources.atmo_own_image
import app.youlauncher.resources.instance_look_hint
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.UserWallpapers
import app.youlauncher.ui.images.rememberRemoteImage
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import app.youlauncher.resources.atmo_scrim
import app.youlauncher.resources.atmo_title
import app.youlauncher.resources.atmo_use_scene
import app.youlauncher.resources.atmo_wallpaper
import app.youlauncher.resources.atmo_wallpaper_none
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.background.ArtLayer
import app.youlauncher.ui.background.BuiltinWallpapers
import app.youlauncher.ui.background.SceneGenerator
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.SettingsSection
import app.youlauncher.ui.components.SliderRow
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.screens.settings.SeedPicker
import org.jetbrains.compose.resources.stringResource

@Composable
fun AtmosphereSection(inst: Instance, vm: InstanceViewModel) {
    val graph = LocalAppGraph.current
    val a = inst.atmosphere
    val still = remember { mutableFloatStateOf(0f) }
    val noParallax = remember { mutableStateOf(Offset.Zero) }
    SettingsSection(stringResource(Res.string.atmo_title), icon = Symbols.FilterHdr) {
        Text(stringResource(Res.string.instance_look_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchRow(stringResource(Res.string.atmo_use_scene), a.useVersionScene, { on -> vm.update { it.copy(atmosphere = it.atmosphere.copy(useVersionScene = on)) } }, icon = Symbols.Landscape)
        SettingLabel(stringResource(Res.string.atmo_wallpaper))
        val noneLabel = stringResource(Res.string.atmo_wallpaper_none)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTile(selected = a.wallpaper == null, label = noneLabel, onClick = { vm.setBackground(null) }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Symbols.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // Своё изображение копируется в папку экземпляра и видно только на его странице.
            val own = a.wallpaper?.takeIf { it.startsWith("file:") }
            if (own != null) {
                MiniTile(selected = true, label = stringResource(Res.string.atmo_own_image), onClick = {}) {
                    val image = rememberRemoteImage(own.removePrefix("file:"), 200)
                    if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            val addTitle = stringResource(Res.string.atmo_add_image)
            MiniTile(selected = false, label = addTitle, onClick = {
                Platform.chooseFiles(addTitle, UserWallpapers.extensions.toList()).firstOrNull()?.let(vm::setBackground)
            }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Symbols.AddPhotoAlternate, addTitle, tint = MaterialTheme.colorScheme.primary) }
            }
            BuiltinWallpapers.all.forEach { w ->
                MiniTile(selected = a.wallpaper == w.key, label = stringResource(w.name), onClick = { vm.update { it.copy(atmosphere = it.atmosphere.copy(wallpaper = w.key)) } }) {
                    ArtLayer(w.style, rememberSceneScheme(w.seedArgb), w.artSeed, still, noParallax, 0.3f, Modifier.fillMaxSize(), preview = true)
                }
            }
        }
        val ownColor = a.seed != null
        SwitchRow(stringResource(Res.string.atmo_color), ownColor, { on ->
            vm.update { it.copy(atmosphere = it.atmosphere.copy(seed = if (on) SceneGenerator.seedFor(it.gameVersion).toLong() and 0xFFFFFFFFL else null)) }
        }, icon = Symbols.Palette)
        if (ownColor) {
            SeedPicker(selectedArgb = a.seed ?: 0, onSelect = { argb -> vm.update { it.copy(atmosphere = it.atmosphere.copy(seed = argb)) } })
        }
        val ownScrim = a.scrim != null
        val global = graph.settings.settings.value.background.scrim
        SwitchRow(stringResource(Res.string.atmo_scrim), ownScrim, { on -> vm.update { it.copy(atmosphere = it.atmosphere.copy(scrim = if (on) global else null)) } }, icon = Symbols.Opacity)
        if (ownScrim) {
            SliderRow(stringResource(Res.string.atmo_scrim), a.scrim ?: global, { v -> vm.update { it.copy(atmosphere = it.atmosphere.copy(scrim = v)) } }, "${((a.scrim ?: global) * 100).toInt()}%", 0f..1f)
        }
    }
}

@Composable
private fun MiniTile(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(88.dp, 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
}

