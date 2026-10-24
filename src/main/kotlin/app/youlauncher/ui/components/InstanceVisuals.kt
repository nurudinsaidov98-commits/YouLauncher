package app.youlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import app.youlauncher.core.model.Instance
import app.youlauncher.core.model.InstanceIcon
import app.youlauncher.core.model.LoaderType
import app.youlauncher.ui.background.SceneCanvas
import app.youlauncher.ui.background.SceneGenerator
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.LocalIsDark
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

fun InstanceIcon.symbol(): ImageVector = when (this) {
    InstanceIcon.Grass -> Symbols.FilterHdrFilled
    InstanceIcon.Castle -> Symbols.CastleFilled
    InstanceIcon.Cottage -> Symbols.CottageFilled
    InstanceIcon.Diamond -> Symbols.DiamondFilled
    InstanceIcon.Swords -> Symbols.Swords
    InstanceIcon.Forest -> Symbols.ForestFilled
    InstanceIcon.Rocket -> Symbols.RocketLaunchFilled
    InstanceIcon.Science -> Symbols.ScienceFilled
    InstanceIcon.Explore -> Symbols.ExploreFilled
    InstanceIcon.Pets -> Symbols.PetsFilled
    InstanceIcon.Bolt -> Symbols.BoltFilled
    InstanceIcon.Water -> Symbols.WaterDropFilled
}

/** Каждой иконке своя форма M3, чтобы значки различались не только символом. */
fun InstanceIcon.polygon(): RoundedPolygon = when (this) {
    InstanceIcon.Grass -> MaterialShapes.Cookie9Sided
    InstanceIcon.Castle -> MaterialShapes.Arch
    InstanceIcon.Cottage -> MaterialShapes.Bun
    InstanceIcon.Diamond -> MaterialShapes.Gem
    InstanceIcon.Swords -> MaterialShapes.Sunny
    InstanceIcon.Forest -> MaterialShapes.Clover4Leaf
    InstanceIcon.Rocket -> MaterialShapes.SoftBurst
    InstanceIcon.Science -> MaterialShapes.Cookie6Sided
    InstanceIcon.Explore -> MaterialShapes.Flower
    InstanceIcon.Pets -> MaterialShapes.Puffy
    InstanceIcon.Bolt -> MaterialShapes.PuffyDiamond
    InstanceIcon.Water -> MaterialShapes.Pill
}

fun LoaderType.symbol(): ImageVector = when (this) {
    LoaderType.Vanilla -> Symbols.FilterHdr
    LoaderType.Fabric -> Symbols.Texture
    LoaderType.Quilt -> Symbols.Checklist
    LoaderType.Forge -> Symbols.Construction
    LoaderType.NeoForge -> Symbols.Handyman
}

/** Цветовая схема сцены версии (кэшируется по seed и режиму). */
@Composable
fun rememberSceneScheme(seedArgb: Int, dark: Boolean = LocalIsDark.current): ColorScheme =
    remember(seedArgb, dark) { dynamicColorScheme(Color(seedArgb), isDark = dark, style = PaletteStyle.Vibrant) }

/** Значок экземпляра: символ внутри формы M3 в цветах сцены его версии или в своём цвете экземпляра. */
@Composable
fun InstanceBadge(icon: InstanceIcon, version: String, size: Dp = 48.dp, modifier: Modifier = Modifier, accent: Long? = null) {
    val scheme = rememberSceneScheme(accent?.toInt() ?: SceneGenerator.seedFor(version))
    val shape = icon.polygon().toShape()
    Box(
        modifier.size(size).clip(shape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon.symbol(), contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
    }
}

private val StaticTime: State<Float> = mutableFloatStateOf(0f)

/** Обложка экземпляра: статичная сцена его версии (анимированная версия используется для фона). */
@Composable
fun VersionCover(version: String, modifier: Modifier = Modifier, time: State<Float> = StaticTime, blur: Dp = 18.dp, accent: Long? = null) {
    val spec = remember(version, accent) { SceneGenerator.forVersion(version).let { s -> if (accent != null) s.copy(seedArgb = accent.toInt()) else s } }
    SceneCanvas(spec, rememberSceneScheme(spec.seedArgb), time, modifier, blur = blur)
}

/** Обложка экземпляра: своя картинка, если выбрана, иначе сцена версии в цвете экземпляра. */
@Composable
fun InstanceCover(instance: Instance, modifier: Modifier = Modifier, time: State<Float> = StaticTime, blur: Dp = 18.dp) {
    val image = instance.coverPath?.let { app.youlauncher.ui.images.rememberRemoteImage(it, 1600) }
    if (image != null) androidx.compose.foundation.Image(image, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = modifier)
    else VersionCover(instance.gameVersion, modifier, time, blur, instance.accent)
}

@Composable
fun MaterialTheme.sceneSchemeFor(version: String): ColorScheme = rememberSceneScheme(SceneGenerator.seedFor(version))
