package app.youlauncher.ui.background

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Стили процедурной графики. Одни и те же функции рисуют живые фоны (с временем t)
 * и встроенные обои (t = 0). Всё строится из форм M3 и ролей цвета схемы.
 */
enum class ArtStyle(val blurred: Boolean) {
    Blobs(true), Waves(false), Mesh(true), Drifting(false), Calm(false),
    Hills(false), Arcs(false), Bubbles(true), Horizon(false), Collage(false),
    Morph(false), Petals(false), Orbits(false), Ribbons(false), Aurora(true), Confetti(false),
}

private const val TAU = (2 * PI).toFloat()

/** Псевдослучайные числа из seed без аллокаций на кадр. */
private fun rnd(seed: Long, i: Int): Float {
    var x = seed xor (i.toLong() * -0x61c8864680b583ebL)
    x = (x xor (x ushr 33)) * -0xae502812aa7333L
    x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
    x = x xor (x ushr 33)
    return ((x ushr 11).toDouble() / (1L shl 53).toDouble()).toFloat()
}

private val artPath = Path()
private val artMatrix = Matrix()
// Пути для волн, холмов, лент и сияния: переиспользуются, без новых объектов в каждом кадре.
private val bandPath = Path()

/** Полоса по ширине окна с запасом по краям (чтобы при параллаксе не было щелей), закрытая вниз. */
private inline fun DrawScope.band(shiftX: Float, steps: Int, bottom: Float, y: (u: Float) -> Float): Path {
    val w = size.width
    val margin = 0.08f
    bandPath.rewind()
    bandPath.moveTo(-w * margin + shiftX, bottom)
    for (s in 0..steps) {
        val u = -margin + (1f + 2 * margin) * s / steps
        bandPath.lineTo(u * w + shiftX, y(u))
    }
    bandPath.lineTo(w * (1f + margin) + shiftX, bottom)
    bandPath.close()
    return bandPath
}

private fun DrawScope.polygon(index: Int, morphTo: Int, progress: Float, cx: Float, cy: Float, size: Float, rotation: Float, color: Color) {
    artPath.rewind()
    ScenePolygons.morph(index % ScenePolygons.COUNT, morphTo % ScenePolygons.COUNT).toPath(progress, artPath)
    artMatrix.reset()
    artMatrix.translate(cx, cy)
    artMatrix.rotateZ(rotation)
    artMatrix.translate(-size / 2, -size / 2)
    artMatrix.scale(size, size)
    artPath.transform(artMatrix)
    drawPath(artPath, color)
}

/** Фон под формами: мягкий градиент контейнерных ролей. */
fun DrawScope.drawArtBase(scheme: ColorScheme, seed: Long) {
    val angle = rnd(seed, 99) * TAU
    val dx = cos(angle) * size.width / 2
    val dy = sin(angle) * size.height / 2
    drawRect(
        Brush.linearGradient(
            listOf(scheme.surfaceContainerLow, scheme.primaryContainer.copy(alpha = 0.7f), scheme.surfaceContainer),
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
        ),
    )
}

fun DrawScope.drawArt(style: ArtStyle, scheme: ColorScheme, seed: Long, t: Float, parallax: Offset = Offset.Zero) {
    val w = size.width
    val h = size.height
    val dim = minOf(w, h)
    val roles = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer, scheme.inversePrimary)
    when (style) {
        ArtStyle.Calm -> {
            drawRect(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow, scheme.tertiaryContainer)))
        }
        ArtStyle.Blobs -> {
            drawArtBase(scheme, seed)
            for (i in 0 until 5) {
                val size = dim * (0.55f + rnd(seed, i) * 0.5f)
                val cx = w * rnd(seed, i + 10) + cos(t * 0.11f + i) * w * 0.06f + parallax.x * (i + 1) * 4
                val cy = h * rnd(seed, i + 20) + sin(t * 0.09f + i * 1.7f) * h * 0.06f + parallax.y * (i + 1) * 4
                val progress = (sin(t * 0.07f + i) + 1f) / 2f
                polygon(i * 3, i * 3 + 5, progress, cx, cy, size, t * 3f + i * 40f, roles[i % roles.size].copy(alpha = 0.85f))
            }
        }
        ArtStyle.Waves -> {
            drawRect(Brush.verticalGradient(listOf(scheme.surfaceContainerLow, scheme.primaryContainer)))
            val layers = 5
            for (i in 0 until layers) {
                val base = h * (0.35f + i * 0.13f)
                val amp = h * (0.05f + rnd(seed, i) * 0.05f)
                val freq = 1.2f + rnd(seed, i + 7) * 1.5f
                val path = band(parallax.x * (i + 1) * 3, 64, h + 4f) { u ->
                    base + amp * sin(u * freq * TAU + t * (0.25f + i * 0.07f) + i) + parallax.y * (i + 1) * 3
                }
                drawPath(path, roles[(i + 2) % roles.size].copy(alpha = 0.35f + i * 0.12f))
            }
        }
        ArtStyle.Mesh -> {
            drawRect(scheme.surfaceContainer)
            val points = 6
            for (i in 0 until points) {
                val px = w * (rnd(seed, i) + 0.08f * sin(t * 0.13f + i)) + parallax.x * 6
                val py = h * (rnd(seed, i + 30) + 0.08f * cos(t * 0.11f + i)) + parallax.y * 6
                val r = dim * (0.6f + rnd(seed, i + 60) * 0.5f)
                val c = roles[i % roles.size]
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.9f), c.copy(alpha = 0f)), center = Offset(px, py), radius = r), radius = r, center = Offset(px, py))
            }
        }
        ArtStyle.Drifting -> {
            drawArtBase(scheme, seed)
            val count = 18
            for (i in 0 until count) {
                val speed = 0.01f + rnd(seed, i + 3) * 0.02f
                val x = ((rnd(seed, i) + t * speed) % 1.2f - 0.1f) * w + parallax.x * (2 + i % 4) * 2
                val y = h * rnd(seed, i + 40) + sin(t * 0.2f + i) * h * 0.02f + parallax.y * (2 + i % 4) * 2
                val size = dim * (0.05f + rnd(seed, i + 80) * 0.12f)
                val progress = (sin(t * 0.3f + i) + 1f) / 2f
                polygon(i, i + 7, progress, x, y, size, t * (8f + i) + i * 20f, roles[i % roles.size].copy(alpha = 0.7f))
            }
        }
        ArtStyle.Hills -> {
            drawRect(Brush.verticalGradient(listOf(scheme.tertiaryContainer, scheme.primaryContainer)))
            drawCircle(scheme.tertiary, radius = dim * 0.12f, center = Offset(w * (0.6f + rnd(seed, 1) * 0.25f), h * 0.3f))
            for (i in 0 until 3) {
                val base = h * (0.55f + i * 0.14f)
                val path = band(parallax.x * (i + 1) * 4, 48, h + 4f) { u ->
                    base + h * 0.07f * sin(u * (1.5f + i) * TAU / 2 + rnd(seed, i) * TAU + t * 0.04f) + parallax.y * (i + 1)
                }
                val color = when (i) { 0 -> scheme.secondary; 1 -> scheme.primary; else -> scheme.onPrimaryContainer }
                drawPath(path, color.copy(alpha = 0.55f + i * 0.2f))
            }
        }
        ArtStyle.Arcs -> {
            drawRect(scheme.surfaceContainerLow)
            val cx = w * (0.2f + rnd(seed, 2) * 0.6f) + parallax.x * 6
            val cy = h * (1.0f + rnd(seed, 3) * 0.2f) + parallax.y * 6
            for (i in 9 downTo 0) {
                val r = dim * (0.25f + i * 0.13f) + sin(t * 0.2f + i) * dim * 0.01f
                drawCircle(roles[i % roles.size].copy(alpha = 0.9f), radius = r, center = Offset(cx, cy))
            }
        }
        ArtStyle.Bubbles -> {
            drawArtBase(scheme, seed)
            for (i in 0 until 12) {
                val r = dim * (0.05f + rnd(seed, i) * 0.22f)
                val x = w * rnd(seed, i + 20) + parallax.x * (i % 5 + 1) * 3
                val span = h + r * 2
                val raw = (h * rnd(seed, i + 40) - t * (6f + i)) % span
                val y = (if (raw < 0) raw + span else raw) - r
                drawCircle(roles[i % roles.size].copy(alpha = 0.75f), radius = r, center = Offset(x, y))
            }
        }
        ArtStyle.Horizon -> {
            drawRect(Brush.verticalGradient(0f to scheme.primaryContainer, 0.62f to scheme.tertiaryContainer, 0.621f to scheme.secondary, 1f to scheme.primary))
            drawCircle(scheme.tertiary, radius = dim * 0.18f, center = Offset(w * 0.5f + parallax.x * 4, h * 0.62f + sin(t * 0.1f) * h * 0.01f))
            for (i in 0 until 4) {
                drawLine(scheme.surfaceContainerLow.copy(alpha = 0.5f), Offset(0f, h * (0.68f + i * 0.07f)), Offset(w, h * (0.68f + i * 0.07f)), strokeWidth = dim * 0.006f * (i + 1))
            }
        }
        ArtStyle.Collage -> {
            drawRect(scheme.surfaceContainerLow)
            val cols = 6
            val rows = 4
            val cell = maxOf(w / cols, h / rows)
            for (r in 0 until rows) for (c in 0 until cols) {
                val i = r * cols + c
                val progress = (sin(t * 0.25f + i) + 1f) / 2f
                polygon(i * 5, i * 5 + 3, progress, (c + 0.5f) * cell + parallax.x * 3, (r + 0.5f) * cell + parallax.y * 3, cell * (0.7f + rnd(seed, i) * 0.25f), rnd(seed, i + 9) * 360f + t * 2f, roles[(i + r) % roles.size])
            }
        }
        ArtStyle.Morph -> {
            // Три крупные формы M3 по очереди перетекают через весь набор полигонов.
            drawArtBase(scheme, seed)
            for (i in 0 until 3) {
                val phase = t * 0.05f + i * 1.3f + rnd(seed, i) * 4f
                val k = phase.toInt()
                val progress = smooth(phase - k)
                val cx = w * (0.3f + i * 0.22f) + sin(t * 0.08f + i * 2f) * w * 0.04f + parallax.x * (3 + i * 3)
                val cy = h * (0.42f + (i % 2) * 0.18f) + cos(t * 0.07f + i) * h * 0.04f + parallax.y * (3 + i * 3)
                polygon(k + i * 5, k + i * 5 + 1, progress, cx, cy, dim * (0.62f - i * 0.12f), t * (4f - i) + i * 60f, roles[(i * 2 + 1) % roles.size].copy(alpha = 0.9f - i * 0.12f))
            }
        }
        ArtStyle.Petals -> {
            // Лепестки (цветок и клевер M3) падают по диагонали, покачиваясь и вращаясь.
            drawRect(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow, scheme.tertiaryContainer)))
            for (i in 0 until 22) {
                val size = dim * (0.07f + rnd(seed, i + 3) * 0.08f)
                val speed = 10f + rnd(seed, i + 5) * 18f
                val span = h + size * 2
                val raw = (h * rnd(seed, i + 40) + t * speed) % span
                val y = raw - size + parallax.y * (1 + i % 4) * 2
                // Снос вбок привязан к тому же циклу падения: лепесток возвращается наверх уже за краем окна.
                val fall = raw / span
                val x = w * rnd(seed, i) + fall * w * 0.25f - w * 0.1f + sin(t * 0.6f + i) * w * 0.03f + parallax.x * (1 + i % 4) * 2
                val shape = if (i % 3 == 0) 2 else 4 // Clover4Leaf или Flower из набора сцен
                polygon(shape, shape + 8, (sin(t * 0.4f + i) + 1f) / 2f * 0.3f, x, y, size, t * (20f + i * 3f) + i * 37f, roles[(i % 3) * 2 % roles.size].copy(alpha = 0.8f))
            }
        }
        ArtStyle.Orbits -> {
            // Кольца и формы, плывущие по орбитам вокруг центра.
            drawRect(Brush.radialGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerLow), center = Offset(w * 0.5f, h * 0.5f), radius = dim))
            val cx = w * 0.5f + parallax.x * 4
            val cy = h * 0.5f + parallax.y * 4
            for (ring in 1..4) {
                val r = dim * 0.12f * ring
                drawCircle(scheme.outline.copy(alpha = 0.45f), radius = r, center = Offset(cx, cy), style = Stroke(width = dim * 0.006f))
                for (j in 0 until ring) {
                    val a = t * (0.12f / ring) * (if (ring % 2 == 0) -1 else 1) + j * TAU / ring + rnd(seed, ring * 7 + j) * TAU
                    val px = cx + cos(a) * r + parallax.x * ring * 2
                    val py = cy + sin(a) * r + parallax.y * ring * 2
                    polygon(ring * 3 + j, ring * 3 + j + 4, (sin(t * 0.3f + ring + j) + 1f) / 2f, px, py, dim * (0.08f + ring * 0.015f), t * 10f + j * 90f, roles[(ring + j) % roles.size])
                }
            }
            polygon(0, 5, (sin(t * 0.2f) + 1f) / 2f, cx, cy, dim * 0.18f, t * 6f, scheme.primary)
        }
        ArtStyle.Ribbons -> {
            // Мягкие ленты, текущие поперёк окна (размываются как акварель).
            drawArtBase(scheme, seed)
            for (i in 0 until 4) {
                val base = h * (0.25f + i * 0.17f)
                val amp = h * (0.08f + rnd(seed, i) * 0.06f)
                val freq = 0.8f + rnd(seed, i + 4) * 0.9f
                val thickness = h * (0.1f + rnd(seed, i + 8) * 0.06f)
                bandPath.rewind()
                val steps = 56
                val margin = 0.08f
                for (s in 0..steps) {
                    val u = -margin + (1f + 2 * margin) * s / steps
                    val y = base + amp * sin(u * freq * TAU + t * (0.18f + i * 0.05f) + i * 1.3f) + parallax.y * (2 + i * 2)
                    val x = u * w + parallax.x * (2 + i * 2)
                    if (s == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
                }
                drawPath(bandPath, roles[(i + 1) % roles.size].copy(alpha = 0.75f), style = Stroke(width = thickness, cap = StrokeCap.Round))
            }
        }
        ArtStyle.Aurora -> {
            // Сияние: вертикальные завесы, медленно колышущиеся, на тёмно-светлом градиенте.
            drawRect(Brush.verticalGradient(listOf(scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.primaryContainer)))
            for (i in 0 until 6) {
                val x0 = w * (0.08f + i * 0.17f) + sin(t * 0.15f + i * 1.7f) * w * 0.05f + parallax.x * (2 + i)
                val width = w * (0.07f + rnd(seed, i) * 0.06f)
                val top = h * (0.05f + rnd(seed, i + 10) * 0.2f)
                val c = roles[i % roles.size]
                bandPath.rewind()
                val steps = 24
                for (s in 0..steps) {
                    val v = s / steps.toFloat()
                    val y = top + v * h * 0.7f
                    val x = x0 + sin(v * 3f + t * 0.3f + i) * width * 0.6f
                    if (s == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
                }
                drawPath(
                    bandPath,
                    Brush.verticalGradient(listOf(c.copy(alpha = 0f), c.copy(alpha = 0.85f), c.copy(alpha = 0f)), startY = top, endY = top + h * 0.7f),
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        ArtStyle.Confetti -> {
            // Маленькие формы M3 всплывают снизу вверх, кружась.
            drawArtBase(scheme, seed)
            for (i in 0 until 30) {
                val size = dim * (0.04f + rnd(seed, i + 3) * 0.06f)
                val speed = 8f + rnd(seed, i + 6) * 14f
                val span = h + size * 2
                val raw = (h * rnd(seed, i + 40) - t * speed) % span
                val y = (if (raw < 0) raw + span else raw) - size + parallax.y * (1 + i % 5) * 2
                val x = w * rnd(seed, i) + sin(t * 0.5f + i * 0.7f) * w * 0.02f + parallax.x * (1 + i % 5) * 2
                polygon(i, i + 6, (sin(t * 0.5f + i) + 1f) / 2f, x, y, size, t * (30f + i * 4f), roles[i % roles.size].copy(alpha = 0.85f))
            }
        }
    }
}

/** Плавный переход 0..1 (smoothstep): морфинг замедляется у начала и конца, без рывка на стыке циклов. */
private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}
