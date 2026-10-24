package app.youlauncher.ui.screens.console

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogLine
import app.youlauncher.core.model.LogSource
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.core.writeTextAtomic
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.console_clear
import app.youlauncher.resources.console_copied
import app.youlauncher.resources.console_copy_all
import app.youlauncher.resources.console_empty_body
import app.youlauncher.resources.console_empty_title
import app.youlauncher.resources.console_export
import app.youlauncher.resources.console_exported
import app.youlauncher.resources.console_follow
import app.youlauncher.resources.console_lines
import app.youlauncher.resources.console_no_matches
import app.youlauncher.resources.console_search
import app.youlauncher.resources.console_source_all
import app.youlauncher.resources.console_source_game
import app.youlauncher.resources.console_source_launcher
import app.youlauncher.resources.console_subtitle
import app.youlauncher.resources.crash_body
import app.youlauncher.resources.crash_copy_report
import app.youlauncher.resources.crash_open_report
import app.youlauncher.resources.crash_title
import app.youlauncher.resources.action_dismiss
import app.youlauncher.resources.nav_console
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.ChoiceOption
import app.youlauncher.ui.components.ConnectedChoice
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.Gaps
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.ScreenHeader
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.WithTooltip
import app.youlauncher.ui.icons.Symbols
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.jetbrains.compose.resources.stringResource

/** Фильтры консоли: чистая функция для тестов. */
data class LogFilter(
    val levels: Set<LogLevel> = LogLevel.entries.toSet() - LogLevel.Trace,
    val source: LogSource? = null,
    val query: String = "",
) {
    fun apply(lines: List<LogLine>): List<LogLine> {
        val q = query.trim()
        return lines.filter { l ->
            l.level in levels && (source == null || l.source == source) &&
                (q.isEmpty() || l.text.contains(q, ignoreCase = true) || l.thread?.contains(q, ignoreCase = true) == true)
        }
    }
}

fun formatLog(lines: List<LogLine>): String = lines.joinToString("\n") { l ->
    val t = java.time.Instant.ofEpochMilli(l.time).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)
    "[$t] [${l.thread ?: l.source.name}/${l.level.name.uppercase()}]: ${l.text}"
}

@Composable
fun ConsoleScreen() {
    val graph = LocalAppGraph.current
    val all by graph.log.lines.collectAsState()
    val launches by graph.launcher.states.collectAsState()
    var filter by remember { mutableStateOf(LogFilter()) }
    var follow by remember { mutableStateOf(true) }
    val shown = remember(all, filter) { filter.apply(all) }

    Column(Modifier.fillMaxSize().padding(Gaps.screen), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenHeader(stringResource(Res.string.nav_console), subtitle = stringResource(Res.string.console_subtitle)) {
            val followLabel = stringResource(Res.string.console_follow)
            WithTooltip(followLabel) {
                IconToggleButton(
                    checked = follow,
                    onCheckedChange = { follow = it },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.semantics { contentDescription = followLabel },
                ) { Icon(Symbols.VerticalAlignBottom, null) }
            }
            TIconButton(Symbols.ContentCopy, stringResource(Res.string.console_copy_all), {
                Platform.copy(formatLog(shown))
                graph.messenger.show(Res.string.console_copied, shown.size)
            }, enabled = shown.isNotEmpty())
            TIconButton(Symbols.Save, stringResource(Res.string.console_export), {
                val name = "youlauncher-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log"
                val file = graph.dirs.logsDir.resolve(name)
                file.writeTextAtomic(formatLog(all))
                graph.messenger.show(Res.string.console_exported, file.toString())
                Platform.openFolder(graph.dirs.logsDir)
            }, enabled = all.isNotEmpty())
            TIconButton(Symbols.Delete, stringResource(Res.string.console_clear), { graph.log.clear() }, enabled = all.isNotEmpty())
        }

        val crashed = launches.entries.firstOrNull { it.value is LaunchState.Crashed }
        AnimatedVisibility(
            crashed != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            val (id, state) = crashed ?: return@AnimatedVisibility
            CrashCard(state as LaunchState.Crashed, onDismiss = { graph.launcher.dismiss(id) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { filter = filter.copy(query = it) },
                placeholder = { Text(stringResource(Res.string.console_search)) },
                leadingIcon = { Icon(Symbols.Search, null) },
                trailingIcon = if (filter.query.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { filter = filter.copy(query = "") }) }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.weight(1f),
            )
            ConnectedChoice(
                options = listOf(
                    ChoiceOption<LogSource?>(null, stringResource(Res.string.console_source_all)),
                    ChoiceOption(LogSource.Launcher, stringResource(Res.string.console_source_launcher)),
                    ChoiceOption(LogSource.Game, stringResource(Res.string.console_source_game)),
                ),
                selected = filter.source,
                onSelect = { filter = filter.copy(source = it) },
                showIcons = false,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(LogLevel.entries) { level ->
                    val selected = level in filter.levels
                    FilterChip(
                        selected = selected,
                        onClick = { filter = filter.copy(levels = if (selected) filter.levels - level else filter.levels + level) },
                        label = { Text(level.name) },
                        leadingIcon = {
                            Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.levelColor(level), modifier = Modifier.size(10.dp)) {}
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
            Text(
                stringResource(Res.string.console_lines, shown.size, all.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            all.isEmpty() -> EmptyState(Illustration.Console, stringResource(Res.string.console_empty_title), stringResource(Res.string.console_empty_body))
            shown.isEmpty() -> EmptyState(Illustration.Search, stringResource(Res.string.console_no_matches), "") {
                TextButton(onClick = { filter = LogFilter() }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.FilterAlt, stringResource(Res.string.action_clear)) }
            }
            else -> LogView(shown, filter.query, follow, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CrashCard(state: LaunchState.Crashed, onDismiss: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Symbols.BugReportFilled, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(32.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.crash_title), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(stringResource(Res.string.crash_body, state.exitCode), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                if (state.summary.isNotBlank()) Text(state.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            state.reportPath?.let { path ->
                FilledTonalButton(onClick = { Platform.openFolder(java.nio.file.Path.of(path).parent) }, shapes = ButtonDefaults.shapes()) {
                    IconLabel(Symbols.Description, stringResource(Res.string.crash_open_report))
                }
                TextButton(onClick = { Platform.copy(runCatching { java.nio.file.Files.readString(java.nio.file.Path.of(path)) }.getOrDefault(path)) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.crash_copy_report))
                }
            }
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}
