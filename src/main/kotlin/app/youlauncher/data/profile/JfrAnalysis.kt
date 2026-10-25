package app.youlauncher.data.profile

import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedFrame
import jdk.jfr.consumer.RecordingFile
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Разбор записи JFR. Замер процессора засчитывается моду, чей код ближе всего к вершине стека: если мод
 * вызвал код игры или библиотеки, нагрузка всё равно его. Замеры без кода модов — доля самой игры.
 * Так же делится выделение памяти (вес каждого замера — сколько байт он представляет).
 */
class JfrAnalysis(private val index: ModClassIndex) {
    private val owners = HashMap<String, Int>()

    fun run(chunks: List<Path>, javaMajor: Int): ProfileSession? {
        val n = index.titles.size
        val cpu = IntArray(n)
        val alloc = LongArray(n)
        var samples = 0
        var gameCpu = 0
        var gameAlloc = 0L
        var allocSeen = false
        var loadSum = 0.0
        var loadCount = 0
        var peakHeap = 0L
        var gcPauses = Duration.ZERO
        var first: Instant? = null
        var last: Instant? = null

        for (chunk in chunks) {
            // Последний фрагмент после принудительного завершения игры бывает неполным: читается, сколько получится.
            runCatching {
                RecordingFile(chunk).use { file ->
                    while (file.hasMoreEvents()) {
                        val e = file.readEvent()
                        val t = e.startTime
                        if (first == null || t < first) first = t
                        if (last == null || t > last) last = t
                        when (e.eventType.name) {
                            "jdk.ExecutionSample" -> {
                                val frames = e.stackTrace?.frames ?: continue
                                samples++
                                val mod = owner(frames)
                                if (mod >= 0) cpu[mod]++ else gameCpu++
                            }
                            "jdk.ObjectAllocationSample" -> {
                                val frames = e.stackTrace?.frames ?: continue
                                val weight = e.long("weight") ?: continue
                                allocSeen = true
                                val mod = owner(frames)
                                if (mod >= 0) alloc[mod] += weight else gameAlloc += weight
                            }
                            "jdk.CPULoad" -> {
                                val user = e.float("jvmUser") ?: continue
                                loadSum += user + (e.float("jvmSystem") ?: 0f)
                                loadCount++
                            }
                            "jdk.GCHeapSummary" -> e.long("heapUsed")?.let { if (it > peakHeap) peakHeap = it }
                            "jdk.GarbageCollection" -> runCatching { gcPauses += e.getDuration("sumOfPauses") }
                        }
                    }
                }
            }
        }
        if (samples < MIN_SAMPLES) return null
        val seconds = Duration.between(first ?: return null, last ?: return null).seconds.coerceAtLeast(1)
        val mods = (0 until n).filter { cpu[it] > 0 || alloc[it] > 0 }.map { i ->
            ModUsage(index.titles[i], index.files[i], cpu[i].toFloat() / samples, alloc[i] / seconds)
        }.sortedByDescending { it.cpuShare }
        return ProfileSession(
            endedAt = System.currentTimeMillis(),
            durationSec = seconds,
            javaMajor = javaMajor,
            cpuSamples = samples,
            avgCpu = if (loadCount > 0) (loadSum / loadCount).toFloat() else null,
            peakHeapBytes = peakHeap.takeIf { it > 0 },
            gcPauseMs = gcPauses.toMillis(),
            gameCpuShare = gameCpu.toFloat() / samples,
            gameAllocBytesPerSec = gameAlloc / seconds,
            allocationTracked = allocSeen,
            mods = mods,
        )
    }

    /** Чей это стек: первый сверху кадр из кода мода, иначе -1 (игра, Java, библиотеки). */
    private fun owner(frames: List<RecordedFrame>): Int {
        for (f in frames) {
            if (!f.isJavaFrame) continue
            val method = f.method ?: continue
            val type = method.type?.name ?: continue
            val byClass = owners.getOrPut(type) { index.modOf(type) ?: -1 }
            if (byClass >= 0) return byClass
            val name = method.name
            if ('$' in name) index.modOf("", name)?.let { return it }
        }
        return -1
    }

    private fun RecordedEvent.long(field: String): Long? = if (hasField(field)) runCatching { getLong(field) }.getOrNull() else null
    private fun RecordedEvent.float(field: String): Float? = if (hasField(field)) runCatching { getFloat(field) }.getOrNull() else null

    companion object {
        /** Меньше двух секунд работы одного потока: слишком мало, чтобы делать выводы. */
        const val MIN_SAMPLES = 40
    }
}
