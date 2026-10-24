package app.youlauncher.ui.background

import androidx.compose.runtime.Immutable
import com.materialkolor.hct.Hct

/** Форма сцены: индекс полигона из [ScenePolygons], позиция и размер в долях холста. */
@Immutable
data class SceneShape(
    val polygon: Int,
    val morphTo: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val role: Int,
    val alpha: Float,
    val driftPhase: Float,
    val driftRadius: Float,
    val morphSpeed: Float,
)

/** Слой холмов внизу сцены (как в логотипе): синусоиды с параметрами из хеша. */
@Immutable
data class HillSpec(val base: Float, val amplitude: Float, val frequency: Float, val phase: Float, val role: Int)

@Immutable
data class SceneSpec(
    val key: String,
    val seedArgb: Int,
    val shapes: List<SceneShape>,
    val hills: List<HillSpec>,
    val gradientAngle: Float,
)

/**
 * Детерминированная «сцена» из строки (номер версии игры, id обоев, пресет).
 * Оттенок seed-цвета берётся из семейства версии (1.21, 26.1), поэтому соседние версии родственны
 * по цвету, а формы и их расположение из полного номера версии. Никаких ассетов игры.
 */
object SceneGenerator {
    /** FNV-1a 64: стабилен между запусками и платформами (в отличие от String.hashCode у разных строк). */
    fun hash(text: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (b in text.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h *= 0x100000001b3L
        }
        return h
    }

    /** "1.21.10" -> "1.21", "26.1" -> "26.1", "b1.7.3" -> "b1.7", "26.4-snapshot-2" -> "26.4". */
    fun family(version: String): String {
        val core = version.substringBefore('-').substringBefore(' ')
        val parts = core.split('.')
        return if (parts.size >= 2) parts[0] + "." + parts[1] else core
    }

    fun seedFor(version: String): Int {
        val familyHash = hash("family:" + family(version))
        val patchHash = hash("patch:$version")
        val hue = ((familyHash ushr 1) % 360).toDouble() + ((patchHash ushr 1) % 31 - 15).toDouble()
        val chroma = 36.0 + ((familyHash ushr 20) % 24).toDouble()
        return Hct.from((hue + 360) % 360, chroma, 50.0).toInt()
    }

    fun forVersion(version: String): SceneSpec = build("version:$version", seedFor(version))

    /** Сцена для произвольного ключа (встроенные обои, пресеты атмосферы). */
    fun forKey(key: String, seedArgb: Int? = null): SceneSpec {
        val h = hash(key)
        val seed = seedArgb ?: Hct.from(((h ushr 1) % 360).toDouble(), 44.0, 50.0).toInt()
        return build(key, seed)
    }

    private fun build(key: String, seedArgb: Int): SceneSpec {
        val rnd = java.util.Random(hash(key))
        val count = 4 + rnd.nextInt(4)
        val shapes = (0 until count).map {
            val polygon = rnd.nextInt(ScenePolygons.COUNT)
            SceneShape(
                polygon = polygon,
                morphTo = (polygon + 1 + rnd.nextInt(ScenePolygons.COUNT - 1)) % ScenePolygons.COUNT,
                x = rnd.nextFloat() * 1.1f - 0.05f,
                y = rnd.nextFloat() * 0.75f - 0.1f,
                size = 0.18f + rnd.nextFloat() * 0.38f,
                rotation = rnd.nextFloat() * 360f,
                role = rnd.nextInt(SCENE_ROLES),
                alpha = 0.55f + rnd.nextFloat() * 0.4f,
                driftPhase = rnd.nextFloat() * 6.283f,
                driftRadius = 0.01f + rnd.nextFloat() * 0.035f,
                morphSpeed = 0.03f + rnd.nextFloat() * 0.05f,
            )
        }
        val hillCount = 2 + rnd.nextInt(2)
        val hills = (0 until hillCount).map { i ->
            HillSpec(
                base = 0.62f + i * 0.12f + rnd.nextFloat() * 0.05f,
                amplitude = 0.04f + rnd.nextFloat() * 0.06f,
                frequency = 0.8f + rnd.nextFloat() * 1.6f,
                phase = rnd.nextFloat() * 6.283f,
                role = i,
            )
        }
        return SceneSpec(key, seedArgb, shapes, hills, gradientAngle = rnd.nextFloat() * 360f)
    }

    const val SCENE_ROLES = 6
}
