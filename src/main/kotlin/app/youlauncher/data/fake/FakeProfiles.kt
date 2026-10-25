package app.youlauncher.data.fake

import app.youlauncher.core.repo.ContentKind
import app.youlauncher.core.repo.InstanceContentRepository
import app.youlauncher.core.repo.InstanceRepository
import app.youlauncher.data.profile.InstanceProfile
import app.youlauncher.data.profile.ModProfiler
import app.youlauncher.data.profile.ModUsage
import app.youlauncher.data.profile.ProfileSession
import kotlinx.coroutines.flow.first

/** Демо: статистика нагрузки модов для экземпляров с модами, как после двух игровых сессий. */
object FakeProfiles {
    private val cpu = floatArrayOf(0.17f, 0.11f, 0.075f, 0.05f, 0.032f, 0.02f, 0.012f, 0.006f)
    private val alloc = longArrayOf(41, 12, 27, 6, 3, 9, 1, 2).map { it * 1024 * 1024 }

    suspend fun seed(profiler: ModProfiler, instances: InstanceRepository, content: InstanceContentRepository) {
        for (inst in instances.instances.value.filter { it.isModded }) {
            val titles = content.content(inst.id, ContentKind.Mods).first().filter { it.enabled }.map { it.title }
            if (titles.isEmpty()) continue
            val now = System.currentTimeMillis()
            val sessions = listOf(0 to 4_260L, 1 to 2_940L).map { (n, seconds) ->
                val mods = titles.take(cpu.size).mapIndexed { i, t ->
                    val jitter = 1f + (if (n == 0) 0.06f else -0.05f) * (if (i % 2 == 0) 1 else -1)
                    ModUsage(t, null, cpu[i] * jitter, (alloc[i] * jitter).toLong())
                }
                ProfileSession(
                    endedAt = now - n * 86_400_000L,
                    durationSec = seconds,
                    javaMajor = 21,
                    cpuSamples = (seconds * 20 * 4).toInt(),
                    avgCpu = if (n == 0) 0.31f else 0.27f,
                    peakHeapBytes = (if (n == 0) 3_350L else 2_900L) * 1024 * 1024,
                    gcPauseMs = if (n == 0) 840 else 610,
                    gameCpuShare = 1f - mods.sumOf { it.cpuShare.toDouble() }.toFloat(),
                    gameAllocBytesPerSec = 58L * 1024 * 1024,
                    allocationTracked = true,
                    mods = mods,
                )
            }
            profiler.save(inst.id, InstanceProfile(sessions))
        }
    }
}
