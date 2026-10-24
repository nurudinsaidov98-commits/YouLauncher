package app.youlauncher.ui.setup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Место фигуры на фоне: доли окна по x и y, размер в долях высоты, номер формы. */
internal data class Placement(val x: Float, val y: Float, val size: Float, val poly: Int)

/**
 * Раскладка фигур фона по шагам установщика. На первом шаге фигуры собраны справа от текста;
 * на следующих они поворачиваются вокруг центра композиции, меняют размер и форму. Фигуры 0 и 10 —
 * сцепленные шестерёнки, их форма не меняется. Чистые функции: проверяются тестом.
 */
internal object BackdropLayout {
    const val GEAR_BIG = 0
    const val GEAR_SMALL = 10
    private const val CX = 0.76f
    private const val CY = 0.5f
    /** Высота окна установщика к ширине (660 / 1040). */
    private const val ASPECT = 0.635f

    val base = listOf(
        Placement(0.80f, 0.42f, 0.34f, 0),
        Placement(0.62f, 0.18f, 0.16f, 1),
        Placement(0.94f, 0.82f, 0.21f, 2),
        Placement(0.64f, 0.80f, 0.14f, 3),
        Placement(0.97f, 0.14f, 0.13f, 4),
        Placement(0.55f, 0.50f, 0.09f, 5),
        Placement(0.84f, 0.97f, 0.12f, 6),
        Placement(0.73f, 0.66f, 0.10f, 7),
        Placement(1.02f, 0.47f, 0.15f, 8),
        Placement(0.51f, 0.90f, 0.08f, 9),
        Placement(0.62f, 0.37f, 0.15f, 0),
        Placement(0.88f, 0.23f, 0.09f, 11),
    )

    private val radius = floatArrayOf(1f, 1.18f, 0.92f, 0.82f, 1.3f)
    private val scale = floatArrayOf(1f, 0.9f, 1f, 1.12f, 0.95f)

    fun placement(slot: Int, step: Int, polygons: Int): Placement {
        val p = base[slot]
        if (step == 0) return p
        val gear = slot == GEAR_BIG || slot == GEAR_SMALL
        val angle = step * 0.85f * (if (slot % 2 == 0) 1f else -0.7f)
        val dx = (p.x - CX) * radius[step]
        val dy = (p.y - CY) * radius[step]
        val x = CX + dx * cos(angle) - dy * sin(angle)
        val y = CY + dx * sin(angle) + dy * cos(angle)
        // Шестерёнки остаются рядом и сцеплены: маленькая сидит на краю большой.
        if (gear) {
            val big = Placement(0.78f - step * 0.01f, 0.46f, base[GEAR_BIG].size * scale[step] * (if (step == 3) 1.2f else 1f), 0)
            return if (slot == GEAR_BIG) big
            // Размер в долях высоты, x в долях ширины: смещение по x переводится через соотношение сторон окна.
            else Placement(big.x - big.size * 0.62f * ASPECT, big.y - big.size * 0.44f, base[GEAR_SMALL].size * scale[step], 0)
        }
        var poly = (p.poly + step * 3) % polygons
        if (poly == 0) poly = 1
        return Placement(x.coerceIn(0.68f, 1.06f), y.coerceIn(-0.06f, 1.06f), p.size * scale[step], poly)
    }
}

/** Состояние одной фигуры: положение и размер на пружинах, морфинг формы. */
private class SlotState(start: Placement, polygon: RoundedPolygon) {
    val x = Animatable(start.x)
    val y = Animatable(start.y)
    val size = Animatable(start.size)
    val appear = Animatable(0f)
    val morphProgress = Animatable(1f)
    var target: RoundedPolygon = polygon
    var morph: Morph = Morph(polygon, polygon)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val polygons: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie12Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf, MaterialShapes.Pill,
        MaterialShapes.SoftBurst, MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.Cookie9Sided,
        MaterialShapes.Pentagon, MaterialShapes.Puffy, MaterialShapes.Arch, MaterialShapes.Clover8Leaf,
        MaterialShapes.Heart, MaterialShapes.SoftBoom, MaterialShapes.Diamond,
    ).map { runCatching { it.normalized() }.getOrDefault(it) }
}

/**
 * Фон установщика: фигуры Material 3 плавно перестраиваются и перетекают в другие формы при каждом шаге
 * (пространственные пружины темы), шестерёнки вращаются, на этапе установки быстрее. Всё рисуется кодом.
 */
@Composable
fun SetupBackdrop(step: Int, busy: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val appearSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val slots = remember {
        BackdropLayout.base.indices.map { i ->
            val p = BackdropLayout.placement(i, 0, polygons.size)
            SlotState(p, polygons[p.poly])
        }
    }
    val speed by animateFloatAsState(if (busy) 5f else 1f, MaterialTheme.motionScheme.slowEffectsSpec(), label = "gears")
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle.floatValue += (now - last) / 1e9f * speed
                last = now
            }
        }
    }
    LaunchedEffect(Unit) {
        // Появление по очереди, с отскоком пружины.
        slots.forEachIndexed { i, s -> launch { delay(i * 45L); s.appear.animateTo(1f, appearSpec) } }
    }
    LaunchedEffect(step) {
        slots.forEachIndexed { i, s ->
            val p = BackdropLayout.placement(i, step, polygons.size)
            launch {
                delay(i * 22L)
                launch { s.x.animateTo(p.x, spatial) }
                launch { s.y.animateTo(p.y, spatial) }
                launch { s.size.animateTo(p.size, spatial) }
                val next = polygons[p.poly]
                if (next !== s.target) {
                    s.morph = Morph(s.target, next)
                    s.target = next
                    s.morphProgress.snapTo(0f)
                    s.morphProgress.animateTo(1f, spatial)
                }
            }
        }
    }
    val colors = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer, scheme.primary, scheme.tertiary, scheme.secondary)
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier) {
        val unit = size.height
        val t = angle.floatValue
        slots.forEachIndexed { i, s ->
            val w = unit * s.size.value * s.appear.value
            if (w <= 0f) return@forEachIndexed
            val gear = i == BackdropLayout.GEAR_BIG || i == BackdropLayout.GEAR_SMALL
            val rotation = when (i) {
                BackdropLayout.GEAR_BIG -> t * 18f
                // Маленькая шестерёнка крутится навстречу и быстрее (передаточное число по размерам).
                BackdropLayout.GEAR_SMALL -> -t * 18f * (BackdropLayout.base[BackdropLayout.GEAR_BIG].size / BackdropLayout.base[BackdropLayout.GEAR_SMALL].size) + 15f
                else -> i * 37f + t * (if (i % 2 == 0) 5f else -4f)
            }
            val center = Offset(size.width * s.x.value, size.height * s.y.value)
            path.rewind()
            s.morph.toPath(s.morphProgress.value, path)
            matrix.reset()
            matrix.translate(center.x, center.y)
            matrix.rotateZ(rotation)
            matrix.translate(-w / 2, -w / 2)
            matrix.scale(w, w)
            path.transform(matrix)
            drawPath(path, colors[i % colors.size])
            // Ось шестерёнки: отверстие цвета фона.
            if (gear) drawCircle(scheme.surface, radius = w * 0.16f, center = center)
        }
    }
}
