package app.youlauncher.ui.screens.console

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.LogLevel
import app.youlauncher.core.model.LogLine
import app.youlauncher.ui.theme.monoFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun ColorScheme.levelColor(level: LogLevel): Color = when (level) {
    LogLevel.Trace, LogLevel.Debug -> outline
    LogLevel.Info -> onSurface
    LogLevel.Warn -> tertiary
    LogLevel.Error, LogLevel.Fatal -> error
}

/** Подсветка совпадений поиска внутри строки (без учёта регистра). */
fun highlight(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    if (query.isBlank()) { append(text); return@buildAnnotatedString }
    var start = 0
    val lower = text.lowercase()
    val q = query.lowercase()
    while (true) {
        val i = lower.indexOf(q, start)
        if (i < 0) { append(text.substring(start)); break }
        append(text.substring(start, i))
        withStyle(style) { append(text.substring(i, i + q.length)) }
        start = i + q.length
    }
}

/** Виртуализированный журнал моноширинным шрифтом: цвет уровня, время, поток, подсветка поиска. */
@Composable
fun LogView(lines: List<LogLine>, query: String, follow: Boolean, modifier: Modifier = Modifier) {
    val mono = monoFamily()
    val state = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val hl = remember(scheme) { SpanStyle(background = scheme.tertiaryContainer, color = scheme.onTertiaryContainer, fontWeight = FontWeight.Bold) }
    LaunchedEffect(lines.size, follow) {
        if (follow && lines.isNotEmpty()) state.scrollToItem(lines.lastIndex)
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)) {
        SelectionContainer {
            LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
                items(lines, key = { it.id }) { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            timeFormat.format(Instant.ofEpochMilli(line.time)),
                            fontFamily = mono,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.outline,
                            modifier = Modifier.width(72.dp),
                        )
                        Text(
                            line.level.name.uppercase().take(5),
                            fontFamily = mono,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.levelColor(line.level),
                            modifier = Modifier.width(56.dp),
                        )
                        Text(
                            highlight(if (line.thread != null) "[${line.thread}] ${line.text}" else line.text, query, hl),
                            fontFamily = mono,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line.level >= LogLevel.Warn) scheme.levelColor(line.level) else scheme.onSurface,
                        )
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp, horizontal = 2.dp))
    }
}
