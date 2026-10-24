package app.youlauncher.ui

import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class UiSound { Launch, Success, Error }

/**
 * Звуки интерфейса синтезируются кодом (мягкие синусоиды с затуханием), файлов звука в приложении нет,
 * поэтому вопроса лицензии не возникает. Играются в отдельном потоке и никогда не блокируют интерфейс.
 */
object UiSounds {
    private const val RATE = 44_100f
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "youlauncher-sounds").apply { isDaemon = true } }

    /** Ноты звука: частота в герцах и длительность в секундах. */
    fun notes(sound: UiSound): List<Pair<Double, Double>> = when (sound) {
        UiSound.Launch -> listOf(523.25 to 0.09, 659.25 to 0.09, 783.99 to 0.18)
        UiSound.Success -> listOf(659.25 to 0.08, 987.77 to 0.16)
        UiSound.Error -> listOf(329.63 to 0.12, 261.63 to 0.18)
    }

    /** 16-битный моно PCM: синус с быстрой атакой и экспоненциальным затуханием, тихо (около трети громкости). */
    fun render(sound: UiSound): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for ((freq, seconds) in notes(sound)) {
            val n = (RATE * seconds).toInt()
            for (i in 0 until n) {
                val t = i / RATE
                val attack = (i / (RATE * 0.005)).coerceAtMost(1.0)
                val env = attack * exp(-t * 18.0)
                val v = (sin(2 * PI * freq * t) * env * 0.3 * Short.MAX_VALUE).toInt()
                out.write(v and 0xFF)
                out.write((v shr 8) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun play(sound: UiSound) {
        player.execute {
            runCatching {
                val format = AudioFormat(RATE, 16, 1, true, false)
                val line = AudioSystem.getSourceDataLine(format)
                line.open(format)
                line.start()
                val data = render(sound)
                line.write(data, 0, data.size)
                line.drain()
                line.close()
            }
        }
    }
}
