package app.youlauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import app.youlauncher.core.VersionFeature
import app.youlauncher.resources.Res
import app.youlauncher.resources.feature_amethyst
import app.youlauncher.resources.feature_autumn_forest
import app.youlauncher.resources.feature_bees
import app.youlauncher.resources.feature_biomes
import app.youlauncher.resources.feature_bundles
import app.youlauncher.resources.feature_cherry
import app.youlauncher.resources.feature_classic
import app.youlauncher.resources.feature_colors
import app.youlauncher.resources.feature_copper
import app.youlauncher.resources.feature_dandelion
import app.youlauncher.resources.feature_deep_dark
import app.youlauncher.resources.feature_elytra
import app.youlauncher.resources.feature_frost
import app.youlauncher.resources.feature_happy_ghast
import app.youlauncher.resources.feature_horses
import app.youlauncher.resources.feature_jungle
import app.youlauncher.resources.feature_mansions
import app.youlauncher.resources.feature_monuments
import app.youlauncher.resources.feature_mountains
import app.youlauncher.resources.feature_nether
import app.youlauncher.resources.feature_ocean
import app.youlauncher.resources.feature_pale_garden
import app.youlauncher.resources.feature_redstone
import app.youlauncher.resources.feature_snapshot
import app.youlauncher.resources.feature_spears
import app.youlauncher.resources.feature_sulfur_cube
import app.youlauncher.resources.feature_the_end
import app.youlauncher.resources.feature_trading
import app.youlauncher.resources.feature_trials
import app.youlauncher.resources.feature_variants
import app.youlauncher.resources.feature_villages
import app.youlauncher.resources.feature_wither
import com.materialkolor.hct.Hct
import org.jetbrains.compose.resources.StringResource
import kotlin.math.sin

/** Роль цвета части эмблемы: берётся из палитры, построенной по оттенку новинки. */
enum class EmblemRole { Primary, PrimaryContainer, Secondary, SecondaryContainer, Tertiary, TertiaryContainer, OnPrimaryContainer, Outline }

/** Часть эмблемы: форма M3 в нормированных координатах (центр, размер, растяжение, поворот). */
class EmblemPart(
    val shape: RoundedPolygon,
    val x: Float,
    val y: Float,
    val size: Float,
    val role: EmblemRole,
    val rotation: Float = 0f,
    val sx: Float = 1f,
    val sy: Float = 1f,
    val drift: Float = 1f,
)

/** Эмблема новинки: оттенок по шкале HCT (0–360) и набор фигур. */
class Emblem(val hue: Double, val parts: List<EmblemPart>)

private fun p(shape: RoundedPolygon, x: Float, y: Float, size: Float, role: EmblemRole, rotation: Float = 0f, sx: Float = 1f, sy: Float = 1f, drift: Float = 1f) =
    EmblemPart(shape, x, y, size, role, rotation, sx, sy, drift)

object Emblems {
    private val S = MaterialShapes
    private val R = EmblemRole.entries

    /** Абстрактные композиции из форм M3: не рисунки из игры, а намёк на главную новинку. */
    fun of(feature: VersionFeature): Emblem = when (feature) {
        VersionFeature.Spears -> Emblem(205.0, listOf(
            p(S.Cookie9Sided, 0.68f, 0.68f, 0.34f, EmblemRole.TertiaryContainer, drift = 0.6f),
            p(S.SoftBurst, 0.68f, 0.68f, 0.16f, EmblemRole.Tertiary, drift = 0.6f),
            p(S.Pill, 0.42f, 0.46f, 0.78f, EmblemRole.SecondaryContainer, rotation = -40f, sx = 0.09f),
            p(S.Triangle, 0.2f, 0.2f, 0.2f, EmblemRole.Secondary, rotation = -40f),
            p(S.Pill, 0.5f, 0.44f, 0.82f, EmblemRole.PrimaryContainer, rotation = 38f, sx = 0.1f),
            p(S.Triangle, 0.76f, 0.15f, 0.22f, EmblemRole.Primary, rotation = 38f),
        ))
        VersionFeature.Copper -> Emblem(40.0, listOf(
            p(S.Cookie4Sided, 0.5f, 0.6f, 0.5f, EmblemRole.PrimaryContainer),
            p(S.Circle, 0.5f, 0.3f, 0.3f, EmblemRole.Primary),
            p(S.Pill, 0.5f, 0.12f, 0.16f, EmblemRole.Tertiary, sx = 0.4f),
            p(S.Circle, 0.42f, 0.3f, 0.06f, EmblemRole.OnPrimaryContainer, drift = 0f),
            p(S.Circle, 0.58f, 0.3f, 0.06f, EmblemRole.OnPrimaryContainer, drift = 0f),
            p(S.Gem, 0.2f, 0.78f, 0.18f, EmblemRole.TertiaryContainer),
            p(S.Gem, 0.82f, 0.8f, 0.14f, EmblemRole.SecondaryContainer),
        ))
        VersionFeature.HappyGhast -> Emblem(0.0, listOf(
            p(S.Puffy, 0.5f, 0.42f, 0.62f, EmblemRole.PrimaryContainer),
            p(S.Circle, 0.4f, 0.4f, 0.07f, EmblemRole.OnPrimaryContainer),
            p(S.Circle, 0.6f, 0.4f, 0.07f, EmblemRole.OnPrimaryContainer),
            p(S.Pill, 0.5f, 0.52f, 0.2f, EmblemRole.Tertiary, sy = 0.35f),
            p(S.Pill, 0.36f, 0.84f, 0.2f, EmblemRole.SecondaryContainer, sx = 0.3f, drift = 1.6f),
            p(S.Pill, 0.5f, 0.88f, 0.2f, EmblemRole.SecondaryContainer, sx = 0.3f, drift = 1.4f),
            p(S.Pill, 0.64f, 0.84f, 0.2f, EmblemRole.SecondaryContainer, sx = 0.3f, drift = 1.6f),
        ))
        VersionFeature.Dandelion -> Emblem(85.0, listOf(
            p(S.Pill, 0.5f, 0.72f, 0.56f, EmblemRole.SecondaryContainer, sx = 0.08f, drift = 0.3f),
            p(S.VerySunny, 0.5f, 0.36f, 0.5f, EmblemRole.PrimaryContainer),
            p(S.Cookie12Sided, 0.5f, 0.36f, 0.24f, EmblemRole.Primary),
            p(S.Circle, 0.2f, 0.24f, 0.06f, EmblemRole.Tertiary, drift = 2f),
            p(S.Circle, 0.82f, 0.18f, 0.05f, EmblemRole.Tertiary, drift = 2.2f),
            p(S.Circle, 0.84f, 0.5f, 0.12f, EmblemRole.TertiaryContainer, drift = 1.5f),
        ))
        VersionFeature.SulfurCube -> Emblem(95.0, listOf(
            p(S.Gem, 0.22f, 0.72f, 0.26f, EmblemRole.TertiaryContainer, rotation = -12f),
            p(S.Gem, 0.8f, 0.7f, 0.2f, EmblemRole.SecondaryContainer, rotation = 14f),
            p(S.Square, 0.5f, 0.46f, 0.5f, EmblemRole.PrimaryContainer, rotation = 8f, drift = 1.8f),
            p(S.Square, 0.5f, 0.46f, 0.3f, EmblemRole.Primary, rotation = 8f, drift = 1.8f),
            p(S.Circle, 0.44f, 0.42f, 0.06f, EmblemRole.OnPrimaryContainer, drift = 1.8f),
            p(S.Circle, 0.56f, 0.42f, 0.06f, EmblemRole.OnPrimaryContainer, drift = 1.8f),
        ))
        VersionFeature.AutumnForest -> Emblem(30.0, listOf(
            p(S.Arch, 0.5f, 0.74f, 0.34f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Clover4Leaf, 0.32f, 0.36f, 0.34f, EmblemRole.PrimaryContainer, rotation = 20f, drift = 1.6f),
            p(S.Flower, 0.66f, 0.3f, 0.3f, EmblemRole.TertiaryContainer, rotation = -15f, drift = 1.8f),
            p(S.Clover4Leaf, 0.76f, 0.62f, 0.18f, EmblemRole.Primary, rotation = 45f, drift = 2.4f),
            p(S.Clover4Leaf, 0.18f, 0.66f, 0.14f, EmblemRole.Tertiary, rotation = 10f, drift = 2.6f),
        ))
        VersionFeature.Variants -> Emblem(110.0, listOf(
            p(S.Oval, 0.28f, 0.58f, 0.34f, EmblemRole.PrimaryContainer),
            p(S.Oval, 0.52f, 0.4f, 0.38f, EmblemRole.SecondaryContainer),
            p(S.Oval, 0.74f, 0.62f, 0.32f, EmblemRole.TertiaryContainer),
            p(S.Clover4Leaf, 0.84f, 0.2f, 0.14f, EmblemRole.Primary, drift = 2.2f),
        ))
        VersionFeature.PaleGarden -> Emblem(260.0, listOf(
            p(S.Cookie7Sided, 0.5f, 0.5f, 0.64f, EmblemRole.SecondaryContainer),
            p(S.Pill, 0.5f, 0.52f, 0.5f, EmblemRole.Outline, sx = 0.16f),
            p(S.Circle, 0.42f, 0.4f, 0.08f, EmblemRole.Tertiary),
            p(S.Circle, 0.58f, 0.4f, 0.08f, EmblemRole.Tertiary),
            p(S.Flower, 0.82f, 0.2f, 0.16f, EmblemRole.TertiaryContainer, drift = 2f),
        ))
        VersionFeature.Bundles -> Emblem(25.0, listOf(
            p(S.Bun, 0.5f, 0.58f, 0.56f, EmblemRole.PrimaryContainer),
            p(S.Pill, 0.5f, 0.3f, 0.26f, EmblemRole.Primary, sy = 0.3f),
            p(S.Gem, 0.3f, 0.2f, 0.14f, EmblemRole.TertiaryContainer, drift = 2f),
            p(S.Circle, 0.74f, 0.18f, 0.1f, EmblemRole.SecondaryContainer, drift = 2.2f),
        ))
        VersionFeature.Trials -> Emblem(185.0, listOf(
            p(S.Burst, 0.32f, 0.36f, 0.42f, EmblemRole.PrimaryContainer, drift = 1.6f),
            p(S.SoftBurst, 0.32f, 0.36f, 0.2f, EmblemRole.Primary, drift = 1.6f),
            p(S.Pill, 0.66f, 0.62f, 0.54f, EmblemRole.SecondaryContainer, rotation = 30f, sx = 0.1f),
            p(S.Cookie6Sided, 0.78f, 0.38f, 0.26f, EmblemRole.Tertiary, rotation = 30f),
        ))
        VersionFeature.Cherry -> Emblem(345.0, listOf(
            p(S.Flower, 0.4f, 0.36f, 0.48f, EmblemRole.PrimaryContainer),
            p(S.Circle, 0.4f, 0.36f, 0.14f, EmblemRole.Primary),
            p(S.Flower, 0.74f, 0.66f, 0.3f, EmblemRole.TertiaryContainer, rotation = 20f, drift = 1.6f),
            p(S.Pill, 0.24f, 0.76f, 0.34f, EmblemRole.Secondary, rotation = 45f, sx = 0.16f),
        ))
        VersionFeature.DeepDark -> Emblem(200.0, listOf(
            p(S.Circle, 0.5f, 0.5f, 0.8f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Circle, 0.5f, 0.5f, 0.56f, EmblemRole.PrimaryContainer, drift = 0.3f),
            p(S.Ghostish, 0.5f, 0.48f, 0.34f, EmblemRole.Primary),
            p(S.Circle, 0.5f, 0.44f, 0.08f, EmblemRole.OnPrimaryContainer),
        ))
        VersionFeature.Mountains -> Emblem(150.0, listOf(
            p(S.Triangle, 0.36f, 0.56f, 0.6f, EmblemRole.PrimaryContainer, drift = 0.2f),
            p(S.Triangle, 0.66f, 0.62f, 0.48f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Triangle, 0.36f, 0.4f, 0.2f, EmblemRole.Outline, drift = 0.2f),
            p(S.Sunny, 0.8f, 0.2f, 0.18f, EmblemRole.Tertiary),
        ))
        VersionFeature.Amethyst -> Emblem(290.0, listOf(
            p(S.Gem, 0.5f, 0.44f, 0.44f, EmblemRole.PrimaryContainer, sy = 1.3f),
            p(S.Gem, 0.28f, 0.6f, 0.26f, EmblemRole.Primary, rotation = -20f, sy = 1.3f),
            p(S.Gem, 0.72f, 0.62f, 0.28f, EmblemRole.TertiaryContainer, rotation = 18f, sy = 1.3f),
            p(S.Heart, 0.8f, 0.2f, 0.14f, EmblemRole.Tertiary, drift = 2f),
        ))
        VersionFeature.Nether -> Emblem(15.0, listOf(
            p(S.Boom, 0.5f, 0.56f, 0.6f, EmblemRole.PrimaryContainer, drift = 1.4f),
            p(S.SoftBoom, 0.5f, 0.56f, 0.34f, EmblemRole.Primary, drift = 1.4f),
            p(S.Cookie4Sided, 0.2f, 0.22f, 0.16f, EmblemRole.TertiaryContainer, drift = 2f),
            p(S.Cookie4Sided, 0.8f, 0.26f, 0.12f, EmblemRole.Tertiary, drift = 2.2f),
        ))
        VersionFeature.Bees -> Emblem(70.0, listOf(
            p(S.Cookie6Sided, 0.36f, 0.4f, 0.32f, EmblemRole.PrimaryContainer, drift = 0.3f),
            p(S.Cookie6Sided, 0.64f, 0.4f, 0.32f, EmblemRole.SecondaryContainer, drift = 0.3f),
            p(S.Cookie6Sided, 0.5f, 0.64f, 0.32f, EmblemRole.TertiaryContainer, drift = 0.3f),
            p(S.Oval, 0.8f, 0.18f, 0.16f, EmblemRole.Primary, drift = 2.6f),
        ))
        VersionFeature.Villages -> Emblem(35.0, listOf(
            p(S.Square, 0.4f, 0.62f, 0.36f, EmblemRole.PrimaryContainer, drift = 0.2f),
            p(S.Triangle, 0.4f, 0.38f, 0.36f, EmblemRole.Primary, drift = 0.2f),
            p(S.Arch, 0.72f, 0.66f, 0.26f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Arrow, 0.76f, 0.26f, 0.2f, EmblemRole.Tertiary, rotation = 45f),
        ))
        VersionFeature.Ocean -> Emblem(210.0, listOf(
            p(S.Pill, 0.5f, 0.74f, 0.9f, EmblemRole.SecondaryContainer, sy = 0.2f, drift = 0.6f),
            p(S.Pill, 0.5f, 0.86f, 0.9f, EmblemRole.PrimaryContainer, sy = 0.2f, drift = 0.8f),
            p(S.Pill, 0.54f, 0.4f, 0.6f, EmblemRole.Primary, sx = 0.08f),
            p(S.Clover4Leaf, 0.54f, 0.12f, 0.22f, EmblemRole.Tertiary),
            p(S.Fan, 0.22f, 0.42f, 0.2f, EmblemRole.TertiaryContainer, drift = 2f),
        ))
        VersionFeature.Colors -> Emblem(300.0, (0 until 9).map { i ->
            p(S.Cookie4Sided, 0.24f + (i % 3) * 0.26f, 0.24f + (i / 3) * 0.26f, 0.22f, R[i % 6], rotation = i * 10f, drift = 0.5f + i * 0.1f)
        })
        VersionFeature.Mansions -> Emblem(120.0, listOf(
            p(S.Square, 0.42f, 0.5f, 0.56f, EmblemRole.SecondaryContainer, rotation = -6f),
            p(S.Diamond, 0.42f, 0.5f, 0.2f, EmblemRole.Primary),
            p(S.Arrow, 0.76f, 0.3f, 0.22f, EmblemRole.Tertiary, rotation = 30f, drift = 1.6f),
            p(S.Pentagon, 0.78f, 0.74f, 0.2f, EmblemRole.TertiaryContainer),
        ))
        VersionFeature.Frost -> Emblem(220.0, listOf(
            p(S.Cookie12Sided, 0.5f, 0.46f, 0.56f, EmblemRole.PrimaryContainer),
            p(S.Clover8Leaf, 0.5f, 0.46f, 0.3f, EmblemRole.Primary),
            p(S.SoftBurst, 0.2f, 0.78f, 0.18f, EmblemRole.TertiaryContainer, drift = 2f),
            p(S.Circle, 0.82f, 0.22f, 0.1f, EmblemRole.Tertiary, drift = 2.2f),
        ))
        VersionFeature.Elytra -> Emblem(270.0, listOf(
            p(S.Fan, 0.32f, 0.44f, 0.42f, EmblemRole.PrimaryContainer, rotation = -20f),
            p(S.Fan, 0.68f, 0.44f, 0.42f, EmblemRole.PrimaryContainer, rotation = 110f),
            p(S.Arch, 0.5f, 0.76f, 0.24f, EmblemRole.Tertiary, drift = 0.6f),
        ))
        VersionFeature.Monuments -> Emblem(180.0, listOf(
            p(S.Square, 0.5f, 0.66f, 0.56f, EmblemRole.PrimaryContainer, drift = 0.2f),
            p(S.Square, 0.5f, 0.38f, 0.34f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Diamond, 0.5f, 0.2f, 0.16f, EmblemRole.Primary),
            p(S.Puffy, 0.82f, 0.3f, 0.14f, EmblemRole.Tertiary, drift = 2f),
        ))
        VersionFeature.Biomes -> Emblem(100.0, listOf(
            p(S.Circle, 0.32f, 0.36f, 0.34f, EmblemRole.PrimaryContainer),
            p(S.Circle, 0.66f, 0.34f, 0.3f, EmblemRole.SecondaryContainer),
            p(S.Circle, 0.48f, 0.68f, 0.36f, EmblemRole.TertiaryContainer),
            p(S.Sunny, 0.82f, 0.74f, 0.16f, EmblemRole.Primary, drift = 2f),
        ))
        VersionFeature.Horses -> Emblem(45.0, listOf(
            p(S.Arch, 0.5f, 0.6f, 0.52f, EmblemRole.PrimaryContainer, rotation = 180f, drift = 0.4f),
            p(S.Oval, 0.5f, 0.32f, 0.3f, EmblemRole.Primary),
            p(S.Clover4Leaf, 0.8f, 0.2f, 0.14f, EmblemRole.Tertiary, drift = 2f),
        ))
        VersionFeature.Redstone -> Emblem(10.0, listOf(
            p(S.Cookie9Sided, 0.44f, 0.5f, 0.5f, EmblemRole.PrimaryContainer),
            p(S.Circle, 0.44f, 0.5f, 0.18f, EmblemRole.Primary),
            p(S.Arrow, 0.8f, 0.5f, 0.22f, EmblemRole.Tertiary, rotation = 90f, drift = 0.6f),
            p(S.Square, 0.2f, 0.2f, 0.12f, EmblemRole.SecondaryContainer, drift = 1.6f),
        ))
        VersionFeature.Wither -> Emblem(280.0, listOf(
            p(S.Square, 0.5f, 0.58f, 0.22f, EmblemRole.SecondaryContainer, sx = 3.4f, sy = 0.4f, drift = 0.4f),
            p(S.Cookie4Sided, 0.22f, 0.44f, 0.22f, EmblemRole.PrimaryContainer),
            p(S.Cookie4Sided, 0.5f, 0.36f, 0.28f, EmblemRole.Primary),
            p(S.Cookie4Sided, 0.78f, 0.44f, 0.22f, EmblemRole.PrimaryContainer),
        ))
        VersionFeature.Trading -> Emblem(140.0, listOf(
            p(S.Gem, 0.5f, 0.46f, 0.5f, EmblemRole.PrimaryContainer, sy = 1.2f),
            p(S.Diamond, 0.5f, 0.46f, 0.2f, EmblemRole.Primary),
            p(S.Circle, 0.22f, 0.76f, 0.14f, EmblemRole.TertiaryContainer, drift = 2f),
            p(S.Circle, 0.8f, 0.74f, 0.1f, EmblemRole.Tertiary, drift = 2.2f),
        ))
        VersionFeature.Jungle -> Emblem(130.0, listOf(
            p(S.Clover8Leaf, 0.46f, 0.4f, 0.56f, EmblemRole.PrimaryContainer),
            p(S.Clover4Leaf, 0.72f, 0.68f, 0.3f, EmblemRole.SecondaryContainer, rotation = 30f),
            p(S.Oval, 0.24f, 0.74f, 0.2f, EmblemRole.Tertiary, drift = 2f),
        ))
        VersionFeature.TheEnd -> Emblem(275.0, listOf(
            p(S.Circle, 0.5f, 0.5f, 0.66f, EmblemRole.SecondaryContainer, drift = 0.2f),
            p(S.Diamond, 0.5f, 0.5f, 0.3f, EmblemRole.Primary),
            p(S.Circle, 0.18f, 0.2f, 0.08f, EmblemRole.Tertiary, drift = 2f),
            p(S.Circle, 0.84f, 0.3f, 0.06f, EmblemRole.Tertiary, drift = 2.2f),
            p(S.Circle, 0.76f, 0.82f, 0.07f, EmblemRole.TertiaryContainer, drift = 2f),
        ))
        VersionFeature.Snapshot -> Emblem(160.0, listOf(
            p(S.SoftBurst, 0.5f, 0.5f, 0.6f, EmblemRole.PrimaryContainer),
            p(S.Cookie4Sided, 0.5f, 0.5f, 0.24f, EmblemRole.Primary),
            p(S.Circle, 0.82f, 0.2f, 0.1f, EmblemRole.Tertiary, drift = 2f),
        ))
        VersionFeature.Classic -> Emblem(110.0, listOf(
            p(S.Square, 0.5f, 0.56f, 0.5f, EmblemRole.PrimaryContainer, drift = 0.3f),
            p(S.PixelCircle, 0.5f, 0.56f, 0.24f, EmblemRole.Primary, drift = 0.3f),
            p(S.Square, 0.8f, 0.2f, 0.12f, EmblemRole.Tertiary, drift = 2f),
        ))
    }

    fun label(feature: VersionFeature): StringResource = when (feature) {
        VersionFeature.Classic -> Res.string.feature_classic
        VersionFeature.Snapshot -> Res.string.feature_snapshot
        VersionFeature.TheEnd -> Res.string.feature_the_end
        VersionFeature.Jungle -> Res.string.feature_jungle
        VersionFeature.Trading -> Res.string.feature_trading
        VersionFeature.Wither -> Res.string.feature_wither
        VersionFeature.Redstone -> Res.string.feature_redstone
        VersionFeature.Horses -> Res.string.feature_horses
        VersionFeature.Biomes -> Res.string.feature_biomes
        VersionFeature.Monuments -> Res.string.feature_monuments
        VersionFeature.Elytra -> Res.string.feature_elytra
        VersionFeature.Frost -> Res.string.feature_frost
        VersionFeature.Mansions -> Res.string.feature_mansions
        VersionFeature.Colors -> Res.string.feature_colors
        VersionFeature.Ocean -> Res.string.feature_ocean
        VersionFeature.Villages -> Res.string.feature_villages
        VersionFeature.Bees -> Res.string.feature_bees
        VersionFeature.Nether -> Res.string.feature_nether
        VersionFeature.Amethyst -> Res.string.feature_amethyst
        VersionFeature.Mountains -> Res.string.feature_mountains
        VersionFeature.DeepDark -> Res.string.feature_deep_dark
        VersionFeature.Cherry -> Res.string.feature_cherry
        VersionFeature.Trials -> Res.string.feature_trials
        VersionFeature.Bundles -> Res.string.feature_bundles
        VersionFeature.PaleGarden -> Res.string.feature_pale_garden
        VersionFeature.Variants -> Res.string.feature_variants
        VersionFeature.HappyGhast -> Res.string.feature_happy_ghast
        VersionFeature.Copper -> Res.string.feature_copper
        VersionFeature.Spears -> Res.string.feature_spears
        VersionFeature.Dandelion -> Res.string.feature_dandelion
        VersionFeature.SulfurCube -> Res.string.feature_sulfur_cube
        VersionFeature.AutumnForest -> Res.string.feature_autumn_forest
    }
}

fun ColorScheme.emblemColor(role: EmblemRole): Color = when (role) {
    EmblemRole.Primary -> primary
    EmblemRole.PrimaryContainer -> primaryContainer
    EmblemRole.Secondary -> secondary
    EmblemRole.SecondaryContainer -> secondaryContainer
    EmblemRole.Tertiary -> tertiary
    EmblemRole.TertiaryContainer -> tertiaryContainer
    EmblemRole.OnPrimaryContainer -> onPrimaryContainer
    EmblemRole.Outline -> outline
}

/**
 * Эмблема новинки версии: фигуры появляются по очереди на пружинах темы (с отскоком)
 * и медленно покачиваются в такт времени фона. Цвета из палитры, построенной по оттенку новинки.
 */
@Composable
fun VersionEmblem(feature: VersionFeature, time: State<Float>, modifier: Modifier = Modifier) {
    val emblem = remember(feature) { Emblems.of(feature) }
    val dark = app.youlauncher.ui.theme.LocalIsDark.current
    val scheme = rememberSceneScheme(remember(feature) { Hct.from(emblem.hue, 52.0, 55.0).toInt() })
    val paths = remember(feature) { emblem.parts.map { Morph(it.shape.normalized(), it.shape.normalized()).toPath(0f, Path()) } }
    val appear = remember(feature) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(feature) { appear.animateTo(1f, spec) }
    val matrix = remember { Matrix() }
    val path = remember { Path() }
    Canvas(modifier) {
        val t = time.value
        val n = emblem.parts.size
        emblem.parts.forEachIndexed { i, part ->
            // Задержка по номеру фигуры: пружина «перехлёстывает» единицу, фигура слегка отскакивает.
            val delay = i.toFloat() / (n + 2)
            val k = ((appear.value - delay) / (1f - delay)).coerceAtLeast(0f)
            if (k <= 0f) return@forEachIndexed
            val bob = sin(t * 0.7f + i * 1.3f) * 0.012f * part.drift
            val sway = sin(t * 0.45f + i * 0.9f) * 4f * part.drift
            drawPart(paths[i], path, matrix, part, scheme.emblemColor(part.role).copy(alpha = if (dark) 0.92f else 1f), k, bob, sway)
        }
    }
}

private fun DrawScope.drawPart(source: Path, path: Path, matrix: Matrix, part: EmblemPart, color: Color, scale: Float, bob: Float, sway: Float) {
    val unit = size.minDimension
    val w = unit * part.size * part.sx * scale
    val h = unit * part.size * part.sy * scale
    path.reset()
    path.addPath(source)
    matrix.reset()
    matrix.translate(size.width * part.x, size.height * (part.y + bob))
    matrix.rotateZ(part.rotation + sway)
    matrix.translate(-w / 2, -h / 2)
    matrix.scale(w, h)
    path.transform(matrix)
    drawPath(path, color)
}
