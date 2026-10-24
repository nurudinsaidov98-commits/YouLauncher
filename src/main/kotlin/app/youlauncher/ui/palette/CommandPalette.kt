package app.youlauncher.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.youlauncher.resources.Res
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.resources.action_clear
import app.youlauncher.resources.palette_empty
import app.youlauncher.resources.palette_hint
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource

data class PaletteCommand(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector,
    val keywords: String = "",
    val shortcut: String? = null,
    val action: () -> Unit,
)

/** Простое нечёткое сопоставление: все символы запроса встречаются по порядку. */
fun fuzzyScore(query: String, text: String): Int? {
    if (query.isBlank()) return 0
    val q = query.lowercase().trim()
    val t = text.lowercase()
    val direct = t.indexOf(q)
    if (direct >= 0) return 1000 - direct
    var ti = 0
    var score = 0
    for (c in q) {
        val found = t.indexOf(c, ti)
        if (found < 0) return null
        score += if (found == ti) 10 else 1
        ti = found + 1
    }
    return score
}

/** Командная палитра (Ctrl+K): переход по разделам, действия, поиск экземпляров. */
@Composable
fun CommandPalette(onDismiss: () -> Unit) {
    val commands = rememberPaletteCommands(onDismiss)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(0) }
    val results = remember(query, commands) {
        commands.mapNotNull { cmd -> fuzzyScore(query, cmd.title + " " + cmd.keywords)?.let { cmd to it } }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    LaunchedEffect(results.size) { selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0)) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { if (results.isNotEmpty()) listState.animateScrollToItem(selected) }

    fun run(cmd: PaletteCommand) {
        onDismiss()
        cmd.action()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier.widthIn(min = 560.dp, max = 640.dp),
        ) {
            Column {
                TextField(
                    value = query,
                    onValueChange = { query = it; selected = 0 },
                    placeholder = { Text(stringResource(Res.string.palette_hint)) },
                    leadingIcon = { Icon(Symbols.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) ({ TIconButton(Symbols.Close, stringResource(Res.string.action_clear), { query = ""; selected = 0 }) }) else null,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> { if (results.isNotEmpty()) selected = (selected + 1) % results.size; true }
                                Key.DirectionUp -> { if (results.isNotEmpty()) selected = (selected - 1 + results.size) % results.size; true }
                                Key.Enter, Key.NumPadEnter -> { results.getOrNull(selected)?.let(::run); true }
                                Key.Escape -> { onDismiss(); true }
                                else -> false
                            }
                        },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.palette_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 420.dp).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(results, key = { _, c -> c.id }) { index, cmd ->
                            val isSelected = index == selected
                            ListItem(
                                headlineContent = { Text(cmd.title) },
                                supportingContent = cmd.subtitle?.let { { Text(it) } },
                                leadingContent = { Icon(cmd.icon, contentDescription = null) },
                                trailingContent = cmd.shortcut?.let { { app.youlauncher.ui.window.KeyHint(it) } },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                ),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.large)
                                    .background(Color.Transparent)
                                    .clickable { run(cmd) },
                            )
                        }
                    }
                }
            }
        }
    }
}
