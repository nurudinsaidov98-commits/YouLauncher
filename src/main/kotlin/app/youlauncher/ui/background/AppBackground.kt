package app.youlauncher.ui.background

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.youlauncher.ui.components.rememberSceneScheme
import app.youlauncher.ui.images.ImageLoader
import app.youlauncher.ui.images.rememberRemoteImage
import app.youlauncher.ui.theme.DefaultSeed
import com.materialkolor.ktx.themeColor
import java.nio.file.Path

/** Цвет темы из изображения (алгоритм Material You из MaterialKolor) по уменьшенной копии. */
object WallpaperColors {
    suspend fun extract(loader: ImageLoader, path: Path): Color? =
        loader.load(path.toString(), maxSize = 128)?.themeColor(fallback = DefaultSeed)
}

/**
 * Единица параллакса: слои умножают её на свою глубину (дальние на меньшее число, ближние на большее),
 * поэтому фон сдвигается как настоящие слои, а не одной картинкой.
 */
private const val PARALLAX_UNIT = 3f

/** Параллакс: положение курсора в окне (-1..1), сглаженное пружиной. */
class ParallaxState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var target by mutableStateOf(Offset.Zero)
}

fun Modifier.trackParallax(state: ParallaxState, enabled: Boolean): Modifier =
    if (!enabled) this else onPointerEvent(PointerEventType.Move) { e ->
        val pos = e.changes.firstOrNull()?.position ?: return@onPointerEvent
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        state.target = Offset((pos.x / w - 0.5f) * 2f, (pos.y / h - 0.5f) * 2f)
    }.onPointerEvent(PointerEventType.Exit) {
        // Курсор ушёл из окна: фон плавно возвращается в центр.
        state.target = Offset.Zero
    }

/**
 * Фон приложения. Смена атмосферы идёт по медленной пространственной пружине: сцены версий
 * перетекают друг в друга с морфингом форм, остальные варианты сменяются кроссфейдом.
 */
@Composable
fun AppBackground(atmosphere: Atmosphere, parallax: ParallaxState, parallaxEnabled: Boolean, saver: Boolean, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf(atmosphere.backdrop) }
    var previous by remember { mutableStateOf<Backdrop?>(null) }
    val progress = remember { Animatable(1f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    // Параллакс без отскока: фон плавно догоняет курсор и не раскачивается.
    val parallaxSpring = MaterialTheme.motionScheme.slowEffectsSpec<Offset>()
    LaunchedEffect(atmosphere.backdrop) {
        if (atmosphere.backdrop == current) return@LaunchedEffect
        previous = current
        current = atmosphere.backdrop
        progress.snapTo(0f)
        progress.animateTo(1f, spring)
        previous = null
    }
    // Цель параллакса читается вне композиции: движение мыши не пересобирает фон, пружина
    // перенацеливается на лету и сохраняет скорость (плавно, без рывков при смене направления).
    LaunchedEffect(parallaxEnabled, parallaxSpring) {
        snapshotFlow { if (parallaxEnabled) parallax.target * PARALLAX_UNIT else Offset.Zero }
            .collectLatest { target -> parallax.offset.animateTo(target, parallaxSpring) }
    }
    val time = rememberAmbientTime(saver = saver)
    val par = remember { derivedStateOf { parallax.offset.value } }
    val blurScale = atmosphere.blur.coerceIn(0f, 1f)

    Box(modifier) {
        val prev = previous
        if (prev is Backdrop.Scene && current is Backdrop.Scene) {
            val from = remember(prev) { AtmosphereResolver.sceneSpec(prev.key) }
            val to = remember(current) { AtmosphereResolver.sceneSpec((current as Backdrop.Scene).key) }
            SceneTransitionCanvas(
                to = to, toScheme = rememberSceneScheme(to.seedArgb),
                from = from, fromScheme = rememberSceneScheme(from.seedArgb),
                progress = progress.asState(), time = time, modifier = Modifier.fillMaxSize(),
                blur = (64 * blurScale).dp, parallax = par,
            )
        } else {
            // Прозрачность читается в слое отрисовки: кадры перехода не пересобирают фон.
            if (prev != null) BackdropLayer(prev, time, par, blurScale, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - progress.value.coerceIn(0f, 1f) })
            BackdropLayer(current, time, par, blurScale, Modifier.fillMaxSize().graphicsLayer { alpha = if (prev != null) progress.value.coerceIn(0f, 1f) else 1f })
        }
        val dim = (1f - atmosphere.brightness).coerceIn(0f, 0.8f)
        if (dim > 0f) {
            val scrim = MaterialTheme.colorScheme.scrim
            Canvas(Modifier.fillMaxSize()) { drawRect(scrim.copy(alpha = dim)) }
        }
    }
}

@Composable
fun BackdropLayer(backdrop: Backdrop, time: State<Float>, parallax: State<Offset>, blurScale: Float, modifier: Modifier) {
    when (backdrop) {
        is Backdrop.Live -> ArtLayer(backdrop.style, MaterialTheme.colorScheme, seed = backdrop.style.ordinal * 7919L + 17, time, parallax, blurScale, modifier)
        is Backdrop.Scene -> {
            val spec = remember(backdrop.key) { AtmosphereResolver.sceneSpec(backdrop.key) }
            SceneTransitionCanvas(spec, rememberSceneScheme(spec.seedArgb), null, null, remember { mutableStateOf(1f) }, time, modifier, (64 * blurScale).dp, parallax)
        }
        is Backdrop.Wallpaper -> when (val ref = backdrop.ref) {
            // Встроенные обои живые: медленное движение, если анимации включены (иначе время стоит).
            is WallpaperRef.Builtin -> ArtLayer(ref.wallpaper.style, rememberSceneScheme(ref.wallpaper.seedArgb), ref.wallpaper.artSeed, time, parallax, blurScale, modifier)
            is WallpaperRef.File -> FileWallpaper(ref.path, parallax, blurScale, modifier)
        }
    }
}

private val StillTime: State<Float> = mutableStateOf(0f)

@Composable
fun ArtLayer(style: ArtStyle, scheme: ColorScheme, seed: Long, time: State<Float>, parallax: State<Offset>, blurScale: Float, modifier: Modifier) {
    val blur = ArtBlur.radius(style.blurred, blurScale).dp
    val scale = if (blur > 0.dp) 1f / SOFT_BLUR_FACTOR else 1f
    Canvas(modifier.softBlur(blur, SOFT_BLUR_FACTOR)) {
        drawArt(style, scheme, seed, time.value, parallax.value * scale)
    }
}

@Composable
private fun FileWallpaper(path: Path, parallax: State<Offset>, blurScale: Float, modifier: Modifier) {
    val image = rememberRemoteImage(path.toString(), maxSize = 2560)
    if (image == null) {
        ArtLayer(ArtStyle.Calm, MaterialTheme.colorScheme, 0, StillTime, parallax, blurScale, modifier)
        return
    }
    val blur = if (blurScale > 0.02f) (48 * blurScale).dp else 0.dp
    val scale = if (blur > 0.dp) 1f / SOFT_BLUR_FACTOR else 1f
    Image(
        image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .softBlur(blur, SOFT_BLUR_FACTOR)
            .graphicsLayer {
                scaleX = 1.06f
                scaleY = 1.06f
                translationX = parallax.value.x * 7f * scale
                translationY = parallax.value.y * 7f * scale
            },
    )
}

/**
 * Радиус размытия живого фона и встроенных обоев по ползунку «Размытие» (0..1).
 * Раньше чёткие стили (волны, ленты, конфетти и другие) не размывались вовсе, а мягкие держали минимум 25%,
 * поэтому ползунок почти ничего не менял. Мягким стилям оставлен небольшой минимум, иначе пятна получают резкие края.
 */
object ArtBlur {
    fun radius(softStyle: Boolean, blurScale: Float): Float {
        val s = blurScale.coerceIn(0f, 1f)
        return if (softStyle) 12f + 68f * s else if (s < 0.02f) 0f else 56f * s
    }
}
