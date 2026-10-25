package app.youlauncher.ui.screens.instance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.data.profile.ProfileSession
import app.youlauncher.resources.Res
import app.youlauncher.resources.modload_avg_cpu
import app.youlauncher.resources.modload_clear
import app.youlauncher.resources.modload_cpu
import app.youlauncher.resources.modload_disabled
import app.youlauncher.resources.modload_empty
import app.youlauncher.resources.modload_enable
import app.youlauncher.resources.modload_game
import app.youlauncher.resources.modload_gc
import app.youlauncher.resources.modload_memory
import app.youlauncher.resources.modload_no_alloc
import app.youlauncher.resources.modload_note
import app.youlauncher.resources.modload_peak_heap
import app.youlauncher.resources.modload_rate
import app.youlauncher.resources.modload_seconds
import app.youlauncher.resources.modload_sessions
import app.youlauncher.resources.modload_title
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.duration
import app.youlauncher.ui.components.fileSize
import app.youlauncher.ui.icons.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class LoadMetric { Cpu, Memory }

/** Строка рейтинга: название, значок, доля от самого нагружающего и подпись значения. */
private class LoadRow(val title: String, val fraction: Float, val value: String, val game: Boolean = false)

/**
 * Какие моды нагружают игру: по последним сессиям (до пяти), взвешенно по длительности. Процессор — доля
 * замеров, где выполнялся код мода; память — сколько он выделял в секунду. Данные собирает ModProfiler.
 */
@Composable
fun ModLoadCard(inst: Instance, vm: InstanceViewModel, modifier: Modifier = Modifier) {
    val graph = LocalAppGraph.current
    val profiles by graph.modProfiler.all.collectAsState()
    LaunchedEffect(inst.id) { withContext(Dispatchers.IO) { graph.modProfiler.profile(inst.id) } }
    val profile = profiles[inst.id]
    val summary = remember(profile) { profile?.summary() }
    var metric by remember { mutableStateOf(LoadMetric.Cpu) }

    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialShapes.SoftBurst.toShape(), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Symbols.Monitoring, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(22.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.modload_title), style = MaterialTheme.typography.titleMediumEmphasized)
                    if (summary != null && profile != null) {
                        Text(
                            stringResource(Res.string.modload_sessions, profile.sessions.size, duration(summary.durationSec * 1000)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (summary != null) {
                    ConnectedChoice(
                        options = listOf(
                            ChoiceOption(LoadMetric.Cpu, stringResource(Res.string.modload_cpu), Symbols.Memory),
                            ChoiceOption(LoadMetric.Memory, stringResource(Res.string.modload_memory), Symbols.Layers),
                        ),
                        selected = metric,
                        onSelect = { metric = it },
                    )
                    TIconButton(Symbols.Delete, stringResource(Res.string.modload_clear), { graph.modProfiler.clear(inst.id) })
                }
            }
            when {
                summary == null -> {
                    Text(
                        stringResource(if (inst.profileMods) Res.string.modload_empty else Res.string.modload_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!inst.profileMods) {
                        Button(onClick = { vm.update { it.copy(profileMods = true) } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.modload_enable)) }
                    }
                }
                else -> {
                    Totals(summary)
                    if (metric == LoadMetric.Memory && !summary.allocationTracked) {
                        Text(stringResource(Res.string.modload_no_alloc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val rows = rows(summary, metric)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEachIndexed { i, row -> LoadBar(i, row) }
                        }
                    }
                    Text(stringResource(Res.string.modload_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun rows(s: ProfileSession, metric: LoadMetric): List<LoadRow> {
    val game = stringResource(Res.string.modload_game)
    fun percent(v: Float) = "${Formats.decimal(v * 100.0)} %"
    return when (metric) {
        LoadMetric.Cpu -> {
            val top = s.mods.sortedByDescending { it.cpuShare }.take(TOP).filter { it.cpuShare > 0f }
            val max = (top.maxOfOrNull { it.cpuShare } ?: 0f).coerceAtLeast(s.gameCpuShare).coerceAtLeast(0.0001f)
            top.map { LoadRow(it.title, it.cpuShare / max, percent(it.cpuShare)) } + LoadRow(game, s.gameCpuShare / max, percent(s.gameCpuShare), game = true)
        }
        LoadMetric.Memory -> {
            val top = s.mods.sortedByDescending { it.allocBytesPerSec }.take(TOP).filter { it.allocBytesPerSec > 0 }
            val max = (top.maxOfOrNull { it.allocBytesPerSec } ?: 0L).coerceAtLeast(s.gameAllocBytesPerSec).coerceAtLeast(1).toFloat()
            val rate = @Composable { b: Long -> stringResource(Res.string.modload_rate, fileSize(b)) }
            top.map { LoadRow(it.title, it.allocBytesPerSec / max, rate(it.allocBytesPerSec)) } + LoadRow(game, s.gameAllocBytesPerSec / max, rate(s.gameAllocBytesPerSec), game = true)
        }
    }
}

private const val TOP = 6

/** Плашки с общими цифрами сессий: средняя загрузка процессора, пик кучи, паузы сборщика мусора. */
@Composable
private fun Totals(s: ProfileSession) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.avgCpu?.let { Pill(Symbols.Speed, stringResource(Res.string.modload_avg_cpu, "${Formats.decimal(it * 100.0)} %")) }
        s.peakHeapBytes?.let { Pill(Symbols.Layers, stringResource(Res.string.modload_peak_heap, fileSize(it))) }
        s.gcPauseMs?.let { Pill(Symbols.Timer, stringResource(Res.string.modload_gc, stringResource(Res.string.modload_seconds, Formats.decimal(it / 1000.0)))) }
    }
}

@Composable
private fun Pill(icon: ImageVector, text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Строка рейтинга: место в форме M3, значок мода, полоса на пружине и значение. */
@Composable
private fun LoadBar(index: Int, row: LoadRow) {
    val fraction by animateFloatAsState(row.fraction.coerceIn(0.02f, 1f), MaterialTheme.motionScheme.slowSpatialSpec(), label = "load")
    val color = when {
        row.game -> MaterialTheme.colorScheme.outline
        index == 0 -> MaterialTheme.colorScheme.error
        index < 3 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (row.game) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Symbols.SportsEsports, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            ProjectIcon(row.title, null, size = 36.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title,
                    style = if (row.game) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (row.game) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.value, style = MaterialTheme.typography.labelLarge, color = if (row.game) MaterialTheme.colorScheme.onSurfaceVariant else color)
            }
            Box(Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color, RoundedCornerShape(50)))
            }
        }
    }
}
