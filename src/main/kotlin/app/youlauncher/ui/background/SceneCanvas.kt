package app.youlauncher.ui.background

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.ui.theme.LocalMotionLevel
import app.youlauncher.ui.window.LocalWindowController
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Набор полигонов M3 (MaterialShapes), из которых строятся сцены. */
object ScenePolygons {
    val all: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Pill,
            MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Puffy, MaterialShapes.Gem,
            MaterialShapes.Cookie6Sided, MaterialShapes.Oval, MaterialShapes.Arch, MaterialShapes.Bun,
            MaterialShapes.Clover8Leaf, MaterialShapes.PuffyDiamond, MaterialShapes.Circle, MaterialShapes.Pentagon,
        ).map { runCatching { it.normalized() }.getOrDefault(it) }
    }
    const val COUNT = 16

    private val morphs = HashMap<Long, Morph>()
    fun morph(a: Int, b: Int): Morph = synchronized(morphs) {
        morphs.getOrPut(a.toLong() shl 32 or b.toLong()) { Morph(all[a], all[b]) }
    }
}

fun ColorScheme.sceneRole(role: Int): Color = when (role % SceneGenerator.SCENE_ROLES) {
    0 -> primary
    1 -> tertiary
    2 -> secondaryContainer
    3 -> primaryContainer
    4 -> tertiaryContainer
    else -> inversePrimary
}

/**
 * Время для фоновых сцен в секундах. Это процедурное движение (позиции считаются от времени кадра),
 * переходы интерфейса при этом остаются пружинными. Время обновляется по кадрам экрана
 * (withFrameNanos), а не по таймеру, поэтому движение ровное, без рывков от рассинхронизации
 * с частотой монитора. Частота ограничена: 60 кадров в секунду в активном окне, 10 без фокуса,
 * 5 в режиме экономии, полная остановка в свёрнутом окне и при выключенных анимациях.
 * Время читается только в слоях отрисовки, поэтому кадр фона не пересобирает интерфейс.
 */
@Composable
fun rememberAmbientTime(enabled: Boolean = true, saver: Boolean = false): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val motion = LocalMotionLevel.current
    val window = LocalWindowController.current
    val active = enabled && motion == MotionLevel.Full && !window.isMinimized
    val minFrameNanos = AmbientClock.minFrameNanos(saver, window.isFocused)
    LaunchedEffect(active, minFrameNanos) {
        if (!active) return@LaunchedEffect
        val offset = time.floatValue
        var start = -1L
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                if (now - last >= minFrameNanos) {
                    last = now
                    time.floatValue = offset + (now - start) / 1e9f
                }
            }
        }
    }
    return time
}

object AmbientClock {
    /** Наименьший промежуток между обновлениями времени фона, наносекунд (небольшой допуск к частоте экрана). */
    fun minFrameNanos(saver: Boolean, focused: Boolean): Long = when {
        saver -> 195_000_000L
        !focused -> 95_000_000L
        else -> 15_500_000L
    }
}

private val NoParallax: State<Offset> = androidx.compose.runtime.mutableStateOf(Offset.Zero)

/** Сцена: градиент, размытые формы M3 с медленным морфингом и дрейфом, слоистые холмы. */
@Composable
fun SceneCanvas(
    spec: SceneSpec,
    scheme: ColorScheme,
    time: State<Float>,
    modifier: Modifier = Modifier,
    blur: Dp = 48.dp,
    parallax: State<Offset>? = null,
    showHills: Boolean = true,
) {
    SceneTransitionCanvas(spec, scheme, null, null, remember { mutableFloatStateOf(1f) }, time, modifier, blur, parallax ?: NoParallax, showHills)
}

/**
 * Переход между двумя сценами: фон и цвета перетекают, формы меняют позицию, размер и поворот,
 * а их контуры морфят из полигона старой сцены в полигон новой. Прогресс задаёт пружина снаружи.
 */
@Composable
fun SceneTransitionCanvas(
    to: SceneSpec,
    toScheme: ColorScheme,
    from: SceneSpec?,
    fromScheme: ColorScheme?,
    progress: State<Float>,
    time: State<Float>,
    modifier: Modifier = Modifier,
    blur: Dp = 48.dp,
    parallax: State<Offset> = NoParallax,
    showHills: Boolean = true,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val p = if (from == null) 1f else progress.value.coerceIn(0f, 1f)
            val a = fromScheme ?: toScheme
            val angle = lerpF(from?.gradientAngle ?: to.gradientAngle, to.gradientAngle, p) * PI.toFloat() / 180f
            val dx = cos(angle) * size.width / 2
            val dy = sin(angle) * size.height / 2
            drawRect(
                Brush.linearGradient(
                    listOf(lerp(a.primaryContainer, toScheme.primaryContainer, p), lerp(a.surfaceContainerLow, toScheme.surfaceContainerLow, p), lerp(a.tertiaryContainer, toScheme.tertiaryContainer, p)),
                    start = Offset(center.x - dx, center.y - dy),
                    end = Offset(center.x + dx, center.y + dy),
                ),
            )
        }
        // Размытые формы рисуются в уменьшенном слое: смещение параллакса тоже уменьшается.
        val scale = if (blur > 0.dp) 1f / SOFT_BLUR_FACTOR else 1f
        Canvas(Modifier.fillMaxSize().softBlur(blur, SOFT_BLUR_FACTOR)) {
            val t = time.value
            val par = parallax.value * scale
            val p = if (from == null) 1f else progress.value.coerceIn(0f, 1f)
            val count = maxOf(to.shapes.size, from?.shapes?.size ?: 0)
            for (i in 0 until count) {
                val target = to.shapes.getOrNull(i)
                val source = from?.shapes?.getOrNull(i)
                // Глубина формы: чем больше номер, тем ближе к зрителю и сильнее сдвиг.
                drawTransitionShape(source, fromScheme ?: toScheme, target, toScheme, if (from == null) 1f else p, t, par * (3f + i * 2.5f))
            }
        }
        if (showHills) {
            Canvas(Modifier.fillMaxSize()) {
                val t = time.value
                val par = parallax.value
                val p = if (from == null) 1f else progress.value.coerceIn(0f, 1f)
                val count = maxOf(to.hills.size, from?.hills?.size ?: 0)
                for (i in 0 until count) {
                    val hb = to.hills.getOrNull(i)
                    val ha = from?.hills?.getOrNull(i) ?: hb
                    val h = when {
                        hb != null && ha != null -> lerpHill(ha, hb, p)
                        hb != null -> hb
                        else -> ha!!
                    }
                    val alphaScale = when {
                        hb == null -> 1f - p
                        from != null && from.hills.getOrNull(i) == null -> p
                        else -> 1f
                    }
                    val colorB = hillColor(toScheme, h.role)
                    val colorA = hillColor(fromScheme ?: toScheme, h.role)
                    val c = lerp(colorA, colorB, p)
                    // Холмы ближе форм: сдвиг больше и по обеим осям, ближний холм сильнее дальнего.
                    drawHill(h, c.copy(alpha = c.alpha * alphaScale), t, par.x * (6f + i * 6f), par.y * (2f + i * 2f))
                }
            }
        }
    }
}

private fun hillColor(scheme: ColorScheme, role: Int): Color = when (role) {
    0 -> scheme.secondary.copy(alpha = 0.55f)
    1 -> scheme.primary.copy(alpha = 0.75f)
    else -> scheme.onPrimaryContainer.copy(alpha = 0.6f)
}

private fun lerpF(a: Float, b: Float, p: Float) = a + (b - a) * p

private fun lerpHill(a: HillSpec, b: HillSpec, p: Float) = HillSpec(
    base = lerpF(a.base, b.base, p),
    amplitude = lerpF(a.amplitude, b.amplitude, p),
    frequency = lerpF(a.frequency, b.frequency, p),
    phase = lerpF(a.phase, b.phase, p),
    role = b.role,
)

private val shapePath = Path()
private val shapeMatrix = Matrix()

private fun DrawScope.drawTransitionShape(a: SceneShape?, schemeA: ColorScheme, b: SceneShape?, schemeB: ColorScheme, p: Float, t: Float, parallax: Offset) {
    val s = b ?: a ?: return
    val src = a ?: b!!.copy(size = 0f)
    val dst = b ?: a!!.copy(size = 0f)
    val dim = minOf(size.width, size.height).coerceAtLeast(1f) * 1.2f
    val sizePx = lerpF(src.size, dst.size, p) * dim
    if (sizePx <= 0.5f) return
    val x = lerpF(src.x, dst.x, p)
    val y = lerpF(src.y, dst.y, p)
    val drift = lerpF(src.driftRadius, dst.driftRadius, p)
    val phase = lerpF(src.driftPhase, dst.driftPhase, p)
    val cx = x * size.width + cos(t * 0.21f + phase) * drift * size.width + parallax.x
    val cy = y * size.height + sin(t * 0.17f + phase) * drift * size.height + parallax.y
    shapePath.rewind()
    if (p >= 1f || a == null || b == null) {
        // Обычный режим: форма медленно «дышит», перетекая в свою пару.
        val breath = ((sin(t * s.morphSpeed * 2f * PI.toFloat() + s.driftPhase) + 1f) / 2f)
        ScenePolygons.morph(s.polygon, s.morphTo).toPath(breath, shapePath)
    } else {
        // Переход: контур морфит из формы старой сцены в форму новой.
        ScenePolygons.morph(a.polygon, b.polygon).toPath(p, shapePath)
    }
    shapeMatrix.reset()
    shapeMatrix.translate(cx, cy)
    shapeMatrix.rotateZ(lerpF(src.rotation, dst.rotation, p) + t * 2f)
    shapeMatrix.translate(-sizePx / 2, -sizePx / 2)
    shapeMatrix.scale(sizePx, sizePx)
    shapePath.transform(shapeMatrix)
    val color = lerp(schemeA.sceneRole(src.role).copy(alpha = src.alpha), schemeB.sceneRole(dst.role).copy(alpha = dst.alpha), p)
    drawPath(shapePath, color)
}

private val hillPath = Path()

private fun DrawScope.drawHill(h: HillSpec, color: Color, t: Float, shift: Float, shiftY: Float = 0f) {
    // Один переиспользуемый Path: без новых объектов в каждом кадре и пауз сборщика мусора.
    val path = hillPath
    path.rewind()
    val w = size.width
    val hgt = size.height
    val steps = 52
    // Холм шире окна на запас по краям: при сдвиге параллакса не появляются щели.
    val margin = 0.08f
    path.moveTo(-w * margin + shift, hgt + 4f)
    for (i in 0..steps) {
        val u = -margin + (1f + 2 * margin) * i / steps
        val y = hgt * (h.base + h.amplitude * sin(u * h.frequency * 2f * PI.toFloat() + h.phase + t * 0.05f) +
            h.amplitude * 0.35f * sin(u * h.frequency * 5.3f + h.phase * 1.7f))
        path.lineTo(w * u + shift, y + shiftY)
    }
    path.lineTo(w * (1f + margin) + shift, hgt + 4f)
    path.close()
    drawPath(path, color)
}
