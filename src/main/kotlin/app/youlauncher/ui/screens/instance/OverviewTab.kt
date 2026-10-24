package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.resources.Res
import app.youlauncher.resources.instance_activity
import app.youlauncher.resources.instance_created
import app.youlauncher.resources.instance_folder
import app.youlauncher.resources.instance_info
import app.youlauncher.resources.instance_java
import app.youlauncher.resources.instance_java_auto
import app.youlauncher.resources.instance_last_played
import app.youlauncher.resources.instance_launches
import app.youlauncher.resources.instance_memory
import app.youlauncher.resources.instance_play_time
import app.youlauncher.resources.instance_tags
import app.youlauncher.resources.memory_gb
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.duration
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.screens.home.BarChart
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

@Composable
fun OverviewTab(inst: Instance, vm: InstanceViewModel) {
    val graph = LocalAppGraph.current
    val settings = graph.settings.settings.value
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(stringResource(Res.string.instance_play_time), duration(inst.totalPlayTimeMs), Symbols.Schedule, Modifier.weight(1f).staggered(0))
            Stat(stringResource(Res.string.instance_last_played), timeAgo(inst.lastPlayedAt), Symbols.History, Modifier.weight(1f).staggered(1))
            Stat(stringResource(Res.string.instance_launches), inst.launches.toString(), Symbols.RocketLaunch, Modifier.weight(1f).staggered(2))
            Stat(stringResource(Res.string.instance_created), timeAgo(inst.createdAt), Symbols.CalendarMonth, Modifier.weight(1f).staggered(3))
        }
        Card(stringResource(Res.string.instance_activity), Modifier.staggered(4)) {
            val today = LocalDate.now()
            val values = (29 downTo 0).map { inst.playLog[today.minusDays(it.toLong()).toString()] ?: 0 }
            BarChart(values, Modifier.fillMaxWidth().height(120.dp))
        }
        Card(stringResource(Res.string.instance_info), Modifier.staggered(5)) {
            val maxMb = inst.java.maxMemoryMb ?: settings.game.maxMemoryMb
            val minMb = inst.java.minMemoryMb ?: settings.game.minMemoryMb
            InfoRow(Symbols.Coffee, stringResource(Res.string.instance_java), inst.java.javaPath ?: stringResource(Res.string.instance_java_auto))
            InfoRow(
                Symbols.Memory, stringResource(Res.string.instance_memory),
                "${stringResource(Res.string.memory_gb, Formats.decimal(minMb / 1024.0))} – ${stringResource(Res.string.memory_gb, Formats.decimal(maxMb / 1024.0))}",
            )
            InfoRow(Symbols.Folder, stringResource(Res.string.instance_folder), vm.gameDir.toString(), onClick = { Platform.openFolder(vm.gameDir) })
            if (inst.tags.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Symbols.Sell, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(Res.string.instance_tags), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        inst.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Card(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(140.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.weight(1f))
            if (onClick != null) Icon(Symbols.OpenInNew, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() } else content()
}
