package app.youlauncher.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import app.youlauncher.core.settings.AtmospherePreset
import app.youlauncher.core.settings.AtmospherePresetRepository
import app.youlauncher.core.settings.BackgroundKind
import app.youlauncher.core.settings.BackgroundSettings
import app.youlauncher.core.settings.BuiltinPresets
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.core.settings.LiveBackground
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_delete
import app.youlauncher.resources.action_save
import app.youlauncher.resources.bg_add_image
import app.youlauncher.resources.bg_blur
import app.youlauncher.resources.bg_brightness
import app.youlauncher.resources.bg_builtin
import app.youlauncher.resources.bg_colors_from_wallpaper
import app.youlauncher.resources.bg_colors_from_wallpaper_hint
import app.youlauncher.resources.bg_drop_hint
import app.youlauncher.resources.bg_google_hint
import app.youlauncher.resources.bg_google_open
import app.youlauncher.resources.bg_image_added
import app.youlauncher.resources.bg_kind
import app.youlauncher.resources.bg_kind_live
import app.youlauncher.resources.bg_kind_own
import app.youlauncher.resources.bg_kind_scene
import app.youlauncher.resources.bg_kind_wallpaper
import app.youlauncher.resources.bg_live_style
import app.youlauncher.resources.bg_motion_note
import app.youlauncher.resources.bg_open_folder
import app.youlauncher.resources.bg_parallax
import app.youlauncher.resources.bg_preset_applied
import app.youlauncher.resources.bg_preset_name
import app.youlauncher.resources.bg_preset_saved
import app.youlauncher.resources.bg_presets
import app.youlauncher.resources.bg_remove_image
import app.youlauncher.resources.bg_saver
import app.youlauncher.resources.bg_saver_hint
import app.youlauncher.resources.bg_save_preset
import app.youlauncher.resources.bg_scrim
import app.youlauncher.resources.bg_version_atmosphere
import app.youlauncher.resources.bg_version_atmosphere_hint
import app.youlauncher.resources.bg_wallpapers
import app.youlauncher.resources.bg_yours
import app.youlauncher.resources.live_blobs
import app.youlauncher.resources.live_calm
import app.youlauncher.resources.live_drifting
import app.youlauncher.resources.live_mesh
import app.youlauncher.resources.live_waves
import app.youlauncher.resources.live_aurora
import app.youlauncher.resources.live_confetti
import app.youlauncher.resources.live_morph
import app.youlauncher.resources.live_orbits
import app.youlauncher.resources.live_petals
import app.youlauncher.resources.live_ribbons
import app.youlauncher.resources.preset_aurora
import app.youlauncher.resources.preset_dawn
import app.youlauncher.resources.preset_lagoon
import app.youlauncher.resources.preset_playful
import app.youlauncher.resources.preset_quiet
import app.youlauncher.resources.preset_version
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.background.ArtLayer
import app.youlauncher.ui.background.BuiltinWallpaper
import app.youlauncher.ui.background.BuiltinWallpapers
import app.youlauncher.ui.background.UserWallpapers
import app.youlauncher.ui.background.rememberAmbientTime
import app.youlauncher.ui.background.toArt
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.components.SliderRow
import app.youlauncher.ui.components.SwitchRow
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.TextInputDialog
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.images.rememberRemoteImage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.io.File
import java.nio.file.Path

fun LiveBackground.label(): StringResource = when (this) {
    LiveBackground.Blobs -> Res.string.live_blobs
    LiveBackground.Waves -> Res.string.live_waves
    LiveBackground.MeshGradient -> Res.string.live_mesh
    LiveBackground.DriftingShapes -> Res.string.live_drifting
    LiveBackground.Calm -> Res.string.live_calm
    LiveBackground.Morph -> Res.string.live_morph
    LiveBackground.Petals -> Res.string.live_petals
    LiveBackground.Orbits -> Res.string.live_orbits
    LiveBackground.Ribbons -> Res.string.live_ribbons
    LiveBackground.Aurora -> Res.string.live_aurora
    LiveBackground.Confetti -> Res.string.live_confetti
}

fun AtmospherePreset.title(): StringResource? = if (!builtin) null else when (name) {
    "dawn" -> Res.string.preset_dawn
    "lagoon" -> Res.string.preset_lagoon
    "quiet" -> Res.string.preset_quiet
    "aurora" -> Res.string.preset_aurora
    "version" -> Res.string.preset_version
    else -> Res.string.preset_playful
}

@Composable
fun BackgroundSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    val bg = s.background
    val setBg: ((BackgroundSettings) -> BackgroundSettings) -> Unit = { f -> update { it.copy(background = f(it.background)) } }
    var refresh by remember { mutableIntStateOf(0) }
    var savingPreset by remember { mutableStateOf(false) }
    val userPresets by graph.presets.presets.collectAsState()

    SettingLabel(stringResource(Res.string.bg_presets))
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (BuiltinPresets.all + userPresets).forEach { preset ->
            val name = preset.title()?.let { stringResource(it) } ?: preset.name
            val selected = s.theme.presetId == preset.id
            InputChip(
                selected = selected,
                onClick = {
                    update { AtmospherePresetRepository.apply(preset, it) }
                    graph.messenger.show(Res.string.bg_preset_applied, name)
                },
                label = { Text(name) },
                leadingIcon = { Icon(if (selected) Symbols.CheckCircleFilled else Symbols.AutoAwesome, null, Modifier.size(18.dp)) },
                trailingIcon = if (!preset.builtin) ({
                    TIconButton(Symbols.Close, stringResource(Res.string.action_delete), { graph.presets.delete(preset.id) }, modifier = Modifier.size(24.dp))
                }) else null,
            )
        }
        AssistChip(onClick = { savingPreset = true }, label = { Text(stringResource(Res.string.bg_save_preset)) }, leadingIcon = { Icon(Symbols.Save, null, Modifier.size(18.dp)) })
    }

    val addTitle = stringResource(Res.string.bg_add_image)
    val images = remember(refresh, bg.userWallpaperFolder) {
        UserWallpapers.list(graph.dirs.wallpapersDir, bg.userWallpaperFolder?.let { Path.of(it) })
    }
    fun addFiles(files: List<Path>) {
        val added = files.mapNotNull { UserWallpapers.import(it, graph.dirs.wallpapersDir) }
        if (added.isNotEmpty()) {
            setBg { it.copy(kind = BackgroundKind.Wallpaper, wallpaper = "file:${added.first()}") }
            graph.messenger.show(Res.string.bg_image_added, added.size)
            refresh++
        }
    }
    // Вкладка «Своё изображение» отдельно от встроенных обоев: в настройках это тот же вид фона «Обои» с файлом.
    var tab by remember(bg.kind) { mutableStateOf(BackgroundTab.of(bg)) }
    SettingLabel(stringResource(Res.string.bg_kind))
    ConnectedChoice(
        options = listOf(
            ChoiceOption(BackgroundTab.Live, stringResource(Res.string.bg_kind_live), Symbols.AutoAwesome),
            ChoiceOption(BackgroundTab.Builtin, stringResource(Res.string.bg_kind_wallpaper), Symbols.Wallpaper),
            ChoiceOption(BackgroundTab.Own, stringResource(Res.string.bg_kind_own), Symbols.AddPhotoAlternate),
            ChoiceOption(BackgroundTab.Scene, stringResource(Res.string.bg_kind_scene), Symbols.FilterHdr),
        ),
        selected = tab,
        onSelect = { t ->
            tab = t
            when (t) {
                BackgroundTab.Live -> setBg { it.copy(kind = BackgroundKind.Live) }
                BackgroundTab.Scene -> setBg { it.copy(kind = BackgroundKind.VersionScene) }
                BackgroundTab.Builtin -> setBg {
                    it.copy(kind = BackgroundKind.Wallpaper, wallpaper = it.wallpaper?.takeIf { w -> w.startsWith("builtin:") } ?: BuiltinWallpapers.all.first().key)
                }
                BackgroundTab.Own -> when {
                    bg.wallpaper?.startsWith("file:") == true -> setBg { it.copy(kind = BackgroundKind.Wallpaper) }
                    images.isNotEmpty() -> setBg { it.copy(kind = BackgroundKind.Wallpaper, wallpaper = "file:${images.first()}") }
                    else -> addFiles(Platform.chooseFiles(addTitle, UserWallpapers.extensions.toList(), multiple = true))
                }
            }
        },
    )

    if (tab == BackgroundTab.Live) {
        SettingLabel(stringResource(Res.string.bg_live_style))
        val time = rememberAmbientTime(saver = bg.saver)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LiveBackground.entries.forEach { live ->
                Tile(selected = live == bg.live, label = stringResource(live.label()), onClick = { setBg { it.copy(live = live, kind = BackgroundKind.Live) } }) {
                    ArtLayer(live.toArt(), MaterialTheme.colorScheme, live.toArt().ordinal * 7919L + 17, time, remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }, 0.4f, Modifier.fillMaxSize(), preview = true)
                }
            }
        }
    }

    if (tab == BackgroundTab.Builtin) {
        SettingLabel(stringResource(Res.string.bg_builtin))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BuiltinWallpapers.all.forEach { w -> BuiltinTile(w, selected = bg.wallpaper == w.key) { setBg { it.copy(kind = BackgroundKind.Wallpaper, wallpaper = w.key) } } }
        }
    }

    if (tab == BackgroundTab.Own) {
        SettingLabel(stringResource(Res.string.bg_yours))
        val dropTarget = remember {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val t = event.awtTransferable
                    if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                    @Suppress("UNCHECKED_CAST")
                    val files = (t.getTransferData(DataFlavor.javaFileListFlavor) as List<File>).map { it.toPath() }
                    addFiles(files)
                    return true
                }
            }
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    images.forEach { path ->
                        val key = "file:$path"
                        FileTile(path, selected = bg.wallpaper == key, onClick = { setBg { it.copy(kind = BackgroundKind.Wallpaper, wallpaper = key) } })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { addFiles(Platform.chooseFiles(addTitle, UserWallpapers.extensions.toList(), multiple = true)) }, shapes = ButtonDefaults.shapes()) {
                        IconLabel(Symbols.AddPhotoAlternate, addTitle)
                    }
                    OutlinedButton(onClick = { Platform.openFolder(graph.dirs.wallpapersDir) }, shapes = ButtonDefaults.shapes()) {
                        IconLabel(Symbols.FolderOpen, stringResource(Res.string.bg_open_folder))
                    }
                    if (bg.wallpaper?.startsWith("file:") == true) {
                        TextButton(onClick = {
                            val p = Path.of(bg.wallpaper.removePrefix("file:"))
                            if (p.startsWith(graph.dirs.wallpapersDir)) runCatching { java.nio.file.Files.deleteIfExists(p) }
                            // Следующее своё изображение, если есть; иначе встроенные обои.
                            val next = images.firstOrNull { it != p }
                            if (next != null) setBg { it.copy(wallpaper = "file:$next") }
                            else { tab = BackgroundTab.Builtin; setBg { it.copy(wallpaper = BuiltinWallpapers.all.first().key) } }
                            refresh++
                        }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.HideImage, stringResource(Res.string.bg_remove_image)) }
                    }
                    Text(stringResource(Res.string.bg_drop_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Symbols.Wallpaper, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(stringResource(Res.string.bg_google_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                TextButton(onClick = { Platform.openUrl(UserWallpapers.WALLPAPERS_BY_GOOGLE) }, shapes = ButtonDefaults.shapes()) {
                    IconLabel(Symbols.OpenInNew, stringResource(Res.string.bg_google_open))
                }
            }
        }
    }
    if (tab == BackgroundTab.Builtin || tab == BackgroundTab.Own) {
        SwitchRow(
            stringResource(Res.string.bg_colors_from_wallpaper), s.theme.colorsFromWallpaper,
            { on -> update { it.copy(theme = it.theme.copy(colorsFromWallpaper = on)) } },
            subtitle = stringResource(Res.string.bg_colors_from_wallpaper_hint), icon = Symbols.Colorize,
        )
    }

    SwitchRow(stringResource(Res.string.bg_version_atmosphere), bg.versionAtmosphere, { on -> setBg { it.copy(versionAtmosphere = on) } }, subtitle = stringResource(Res.string.bg_version_atmosphere_hint), icon = Symbols.FilterHdr)
    SliderRow(stringResource(Res.string.bg_brightness), bg.brightness, { v -> setBg { it.copy(brightness = v) } }, "${(bg.brightness * 100).toInt()}%", 0.4f..1f)
    SliderRow(stringResource(Res.string.bg_blur), bg.blur, { v -> setBg { it.copy(blur = v) } }, "${(bg.blur * 100).toInt()}%", 0f..1f)
    SliderRow(stringResource(Res.string.bg_scrim), bg.scrim, { v -> setBg { it.copy(scrim = v) } }, "${(bg.scrim * 100).toInt()}%", 0f..1f)
    SwitchRow(stringResource(Res.string.bg_parallax), bg.parallax, { on -> setBg { it.copy(parallax = on) } }, icon = Symbols.Mouse)
    SwitchRow(stringResource(Res.string.bg_saver), bg.saver, { on -> setBg { it.copy(saver = on) } }, subtitle = stringResource(Res.string.bg_saver_hint), icon = Symbols.EnergySavingsLeaf)
    Text(stringResource(Res.string.bg_motion_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (savingPreset) {
        TextInputDialog(
            title = stringResource(Res.string.bg_save_preset),
            label = stringResource(Res.string.bg_preset_name),
            initial = "",
            confirmLabel = stringResource(Res.string.action_save),
            onConfirm = { name ->
                val p = graph.presets.save(name, graph.settings.settings.value)
                update { it.copy(theme = it.theme.copy(presetId = p.id)) }
                graph.messenger.show(Res.string.bg_preset_saved, name)
            },
            onDismiss = { savingPreset = false },
        )
    }
}

@Composable
private fun Tile(selected: Boolean, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(160.dp, 100.dp)
                .clip(MaterialTheme.shapes.large)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
                .selectable(selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        ) {
            content()
            if (selected) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Icon(Symbols.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp).size(16.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val still = mutableStateOf(0f)
private val noParallax = mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)

@Composable
private fun BuiltinTile(w: BuiltinWallpaper, selected: Boolean, onClick: () -> Unit) {
    Tile(selected, stringResource(w.name), onClick) {
        ArtLayer(w.style, rememberSceneScheme(w.seedArgb), w.artSeed, still, noParallax, 0.3f, Modifier.fillMaxSize(), preview = true)
    }
}

@Composable
private fun FileTile(path: Path, selected: Boolean, onClick: () -> Unit) {
    Tile(selected, path.fileName.toString().take(18), onClick) {
        val image = rememberRemoteImage(path.toString(), maxSize = 320)
        if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Вкладки выбора фона в настройках; «Своё изображение» хранится как обои с путём к файлу. */
enum class BackgroundTab {
    Live, Builtin, Own, Scene;

    companion object {
        fun of(bg: BackgroundSettings): BackgroundTab = when (bg.kind) {
            BackgroundKind.Live -> Live
            BackgroundKind.VersionScene -> Scene
            BackgroundKind.Wallpaper -> if (bg.wallpaper?.startsWith("file:") == true) Own else Builtin
        }
    }
}
