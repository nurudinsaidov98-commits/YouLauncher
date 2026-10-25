package app.youlauncher.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.core.settings.PaletteStyleOption
import app.youlauncher.core.settings.SpringBounce
import app.youlauncher.core.settings.ThemeMode
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.core.settings.UiSettings
import app.youlauncher.resources.Res
import app.youlauncher.resources.seed_amber
import app.youlauncher.resources.seed_amethyst
import app.youlauncher.resources.seed_forest
import app.youlauncher.resources.seed_indigo
import app.youlauncher.resources.seed_lagoon
import app.youlauncher.resources.seed_moss
import app.youlauncher.resources.seed_ocean
import app.youlauncher.resources.seed_rose
import app.youlauncher.resources.seed_sand
import app.youlauncher.resources.seed_sunset
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import org.jetbrains.compose.resources.StringResource

@Immutable
data class SeedColor(val name: StringResource, val color: Color)

/**
 * Seed-цвета, которые предлагаются в настройках. Это единственное место в приложении, где цвета
 * заданы числами: вся остальная палитра вычисляется из выбранного seed по алгоритму Material You.
 */
val SeedColors: List<SeedColor> = listOf(
    SeedColor(Res.string.seed_forest, Color(0xFF3F7D4E)),
    SeedColor(Res.string.seed_ocean, Color(0xFF1E6FB8)),
    SeedColor(Res.string.seed_sunset, Color(0xFFD1562B)),
    SeedColor(Res.string.seed_amethyst, Color(0xFF7B4FC9)),
    SeedColor(Res.string.seed_rose, Color(0xFFC2306B)),
    SeedColor(Res.string.seed_lagoon, Color(0xFF00897B)),
    SeedColor(Res.string.seed_amber, Color(0xFFE0A100)),
    SeedColor(Res.string.seed_indigo, Color(0xFF3F51B5)),
    SeedColor(Res.string.seed_moss, Color(0xFF6B7F2A)),
    SeedColor(Res.string.seed_sand, Color(0xFFA1785C)),
)

val DefaultSeed: Color get() = SeedColors.first().color

fun ThemeSettings.seedColor(): Color = seed?.let { Color(it.toInt()).copy(alpha = 1f) } ?: DefaultSeed

fun Color.toSeedLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun PaletteStyleOption.toKolor(): PaletteStyle = when (this) {
    PaletteStyleOption.TonalSpot -> PaletteStyle.TonalSpot
    PaletteStyleOption.Neutral -> PaletteStyle.Neutral
    PaletteStyleOption.Vibrant -> PaletteStyle.Vibrant
    PaletteStyleOption.Expressive -> PaletteStyle.Expressive
    PaletteStyleOption.Rainbow -> PaletteStyle.Rainbow
    PaletteStyleOption.FruitSalad -> PaletteStyle.FruitSalad
    PaletteStyleOption.Monochrome -> PaletteStyle.Monochrome
    PaletteStyleOption.Fidelity -> PaletteStyle.Fidelity
    PaletteStyleOption.Content -> PaletteStyle.Content
}

/** Текущий режим анимаций: фоны и декоративные эффекты сверяются с ним. */
val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.Full }

/** Тёмная ли сейчас тема (нужно фонам и иллюстрациям). */
val LocalIsDark = staticCompositionLocalOf { false }

fun motionSchemeFor(level: MotionLevel, bounce: SpringBounce = SpringBounce.Standard): MotionScheme = when (level) {
    MotionLevel.Full -> if (bounce == SpringBounce.Standard) MotionScheme.expressive() else SpringyMotionScheme(bounce)
    MotionLevel.Reduced -> MotionScheme.standard()
    MotionLevel.Off -> InstantMotionScheme
}

/**
 * Выразительная схема движения с усиленным отскоком: пространственные пружины M3 Expressive
 * с меньшим затуханием (заметный «перехлёст» и возврат), пружины эффектов без отскока,
 * чтобы цвет и прозрачность не мерцали.
 */
class SpringyMotionScheme(bounce: SpringBounce) : MotionScheme {
    private val damping = when (bounce) {
        SpringBounce.Jelly -> 0.42f
        else -> 0.58f
    }
    private val fastDamping = when (bounce) {
        SpringBounce.Jelly -> 0.45f
        else -> 0.55f
    }
    private val stiffnessScale = if (bounce == SpringBounce.Jelly) 0.85f else 1f

    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(damping, 380f * stiffnessScale)
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(fastDamping, 800f * stiffnessScale)
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(damping, 200f * stiffnessScale)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 1600f)
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 3800f)
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(1f, 800f)
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Тема YouLauncher: Material 3 Expressive через MaterialKolor (динамическая схема из seed),
 * выразительная схема движения, типографика Google Sans Flex и формы с настраиваемым скруглением.
 */
@Composable
fun YouLauncherTheme(
    theme: ThemeSettings,
    ui: UiSettings,
    seedOverride: Color? = null,
    /** false — цвета меняются без пружины: переливание само меняет seed маленькими шагами. */
    animateColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(theme.mode)
    val seed = seedOverride ?: theme.seedColor()
    val base = LocalDensity.current
    val density = Density(base.density * ui.scale, base.fontScale * ui.fontScale)

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalMotionLevel provides ui.motion,
        LocalIsDark provides dark,
    ) {
        DynamicMaterialExpressiveTheme(
            seedColor = seed,
            motionScheme = remember(ui.motion, ui.bounce) { motionSchemeFor(ui.motion, ui.bounce) },
            isDark = dark,
            isAmoled = theme.amoled,
            style = theme.style.toKolor(),
            contrastLevel = theme.contrast.level,
            shapes = youlauncherShapes(ui.cornerScale),
            typography = youlauncherTypography(ui.font),
            animate = animateColors && ui.motion != MotionLevel.Off,
        ) {
            // Свои цвета ролей поверх динамической схемы: меняют все элементы, которые берут эти роли.
            // Обёртка есть всегда: если бы она появлялась только со своими цветами, весь интерфейс
            // пересоздавался бы при первом своём цвете (сбрасывалась прокрутка и состояние экранов).
            val base = MaterialTheme.colorScheme
            val scheme = remember(base, theme.colorOverrides) {
                if (theme.colorOverrides.isEmpty()) base else ColorOverrides.apply(base, theme.colorOverrides)
            }
            MaterialExpressiveTheme(
                colorScheme = scheme,
                motionScheme = MaterialTheme.motionScheme,
                shapes = MaterialTheme.shapes,
                typography = MaterialTheme.typography,
                content = content,
            )
        }
    }
}

/** Цвет текста поверх затемнения (scrim) на изображениях и сценах: светлый в обеих темах. */
@Composable
fun onScrimColor(): Color =
    if (LocalIsDark.current) androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    else androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface

/**
 * Переливание акцента: seed плавно перетекает в второй цвет и обратно. Смешивание идёт в HCT по короткой
 * дуге оттенка, поэтому промежуточные цвета остаются насыщенными, а не сереют, как при смешивании RGB.
 */
object AccentShimmer {
    /** Полный цикл туда и обратно. */
    const val PERIOD_MS = 14_000L
    /** Шаг обновления: вся схема пересчитывается на каждом шаге, поэтому не каждый кадр. */
    const val STEP_MS = 120L

    /** Фаза 0..1 цикла → доля второго цвета 0..1..0 с мягкими разворотами. */
    fun wave(phase: Float): Float = (1f - kotlin.math.cos(phase * 2f * kotlin.math.PI.toFloat())) / 2f

    fun blend(a: Color, b: Color, f: Float): Color {
        val x = com.materialkolor.hct.Hct.fromInt(a.toArgb())
        val y = com.materialkolor.hct.Hct.fromInt(b.toArgb())
        var dh = y.hue - x.hue
        if (dh > 180) dh -= 360
        if (dh < -180) dh += 360
        val hue = (x.hue + dh * f + 360) % 360
        return Color(com.materialkolor.hct.Hct.from(hue, x.chroma + (y.chroma - x.chroma) * f, x.tone + (y.tone - x.tone) * f).toInt())
    }
}

/**
 * Текущий цвет переливания или null, если оно выключено. [active] = false замораживает цвет на месте
 * (окно не в фокусе, идёт игра): интерфейс не пересчитывается впустую, а цвет не прыгает при возврате.
 */
@Composable
fun rememberShimmerSeed(theme: ThemeSettings, active: Boolean): Color? {
    val second = theme.shimmer ?: return null
    val a = theme.seedColor()
    val b = Color(second.toInt()).copy(alpha = 1f)
    var phase by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            kotlinx.coroutines.delay(AccentShimmer.STEP_MS)
            val now = System.nanoTime()
            phase = (phase + (now - last) / 1_000_000f / AccentShimmer.PERIOD_MS) % 1f
            last = now
        }
    }
    return remember(a, b, phase) { AccentShimmer.blend(a, b, AccentShimmer.wave(phase)) }
}
