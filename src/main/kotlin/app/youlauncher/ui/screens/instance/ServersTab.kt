package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.AppGraph
import app.youlauncher.data.servers.ServerEntry
import app.youlauncher.data.servers.ServerPing
import app.youlauncher.data.servers.ServerStatus
import app.youlauncher.data.servers.ServersFile
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.ViewModel
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.staggered
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.rememberViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

data class ServerRow(val entry: ServerEntry, val status: ServerStatus? = null, val pinging: Boolean = false, val failed: Boolean = false)

/** Серверы экземпляра: читаются из servers.dat игры, опрашиваются параллельно. */
class ServersViewModel(
    private val graph: AppGraph,
    private val instanceId: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ping: suspend (String) -> ServerStatus = { ServerPing.ping(it) },
) : ViewModel(dispatcher) {
    private val _rows = MutableStateFlow<List<ServerRow>?>(null)
    val rows: StateFlow<List<ServerRow>?> = _rows.asStateFlow()
    private val gameDir get() = graph.instances.gameDir(instanceId)

    init { load() }

    fun load() = scope.launch {
        val list = withContext(Dispatchers.IO) { runCatching { ServersFile.read(gameDir) }.getOrDefault(emptyList()) }
        _rows.value = list.map { ServerRow(it) }
        refresh()
    }

    fun refresh() {
        _rows.value.orEmpty().forEach { row -> pingOne(row.entry) }
    }

    private fun pingOne(entry: ServerEntry) {
        update(entry) { it.copy(pinging = true, failed = false) }
        scope.launch {
            val result = runCatching { ping(entry.address) }
            update(entry) { it.copy(pinging = false, status = result.getOrNull(), failed = result.isFailure) }
        }
    }

    private fun update(entry: ServerEntry, transform: (ServerRow) -> ServerRow) =
        _rows.update { list -> list?.map { if (it.entry.address == entry.address && it.entry.name == entry.name) transform(it) else it } }

    private suspend fun save(list: List<ServerEntry>) = withContext(Dispatchers.IO) { ServersFile.write(gameDir, list) }

    fun add(name: String, address: String) = scope.launch {
        val entry = ServerEntry(name.trim().ifBlank { address.trim() }, address.trim())
        val list = _rows.value.orEmpty().map { it.entry } + entry
        runCatching { save(list) }.onSuccess {
            _rows.update { (it.orEmpty()) + ServerRow(entry) }
            pingOne(entry)
            graph.messenger.show(Res.string.server_added, entry.name)
        }.onFailure { graph.messenger.show(Res.string.server_save_failed, it.message ?: "", error = true) }
    }

    fun remove(entry: ServerEntry) = scope.launch {
        val list = _rows.value.orEmpty().map { it.entry }.filterNot { it == entry }
        runCatching { save(list) }.onSuccess {
            _rows.update { it?.filterNot { r -> r.entry == entry } }
            graph.messenger.show(Res.string.msg_removed, entry.name)
        }.onFailure { graph.messenger.show(Res.string.server_save_failed, it.message ?: "", error = true) }
    }

    /** Запуск сразу с заходом на сервер (аргументы игры --server/--quickPlayMultiplayer). */
    fun play(entry: ServerEntry) {
        graph.settings.update { it.copy(lastInstanceId = instanceId) }
        graph.launcher.launch(instanceId, entry.address)
    }
}

@Composable
fun ServersTab(instanceId: String) {
    val graph = LocalAppGraph.current
    val vm = rememberViewModel(instanceId) { ServersViewModel(graph, instanceId) }
    val rows by vm.rows.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.servers_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TIconButton(Symbols.Refresh, stringResource(Res.string.servers_refresh), { vm.refresh() })
            Button(onClick = { adding = true }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Add, stringResource(Res.string.server_add)) }
        }
        val list = rows
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(56.dp)) }
            list.isEmpty() -> EmptyState(Illustration.Worlds, stringResource(Res.string.servers_empty_title), stringResource(Res.string.servers_empty_body)) {
                Button(onClick = { adding = true }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Add, stringResource(Res.string.server_add)) }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(list, key = { _, r -> r.entry.name + "|" + r.entry.address }) { i, row ->
                    ServerCard(row, onPlay = { vm.play(row.entry) }, onRemove = { vm.remove(row.entry) }, modifier = Modifier.staggered(i, row.entry.address))
                }
            }
        }
    }
    if (adding) AddServerDialog(onAdd = { n, a -> vm.add(n, a); adding = false }, onDismiss = { adding = false })
}

private fun favicon(bytes: ByteArray?): ImageBitmap? = bytes?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }

@Composable
private fun ServerCard(row: ServerRow, onPlay: () -> Unit, onRemove: () -> Unit, modifier: Modifier) {
    val status = row.status
    val icon = remember(status?.favicon) { favicon(status?.favicon) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                if (icon != null) Image(icon, null, Modifier.size(56.dp), filterQuality = FilterQuality.None)
                else Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Symbols.Dns, null) }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.entry.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.entry.address, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                when {
                    row.pinging -> Text(stringResource(Res.string.server_pinging), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    row.failed -> Text(stringResource(Res.string.server_offline), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    status != null -> Text(status.motd, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.server_players, status.online, status.max), style = MaterialTheme.typography.labelLarge)
                    PingChip(status.latencyMs)
                    Text(status.version, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            } else if (row.pinging) LoadingIndicator(Modifier.size(32.dp))
            FilledTonalButton(onClick = onPlay, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.PlayArrow, stringResource(Res.string.action_play)) }
            TIconButton(Symbols.Delete, stringResource(Res.string.server_remove), onRemove)
        }
    }
}

@Composable
private fun PingChip(ms: Long) {
    val color = when {
        ms < 0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        ms < 100 -> MaterialTheme.colorScheme.primaryContainer
        ms < 250 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Symbols.SignalCellularAlt, null, Modifier.size(14.dp))
            Text(if (ms < 0) "—" else stringResource(Res.string.server_ping_ms, ms.toInt()), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddServerDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Dns, null) },
        title = { Text(stringResource(Res.string.server_add)) },
        text = {
            Column(Modifier.widthIn(min = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(48) }, label = { Text(stringResource(Res.string.server_name)) }, singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(address, { address = it.trim().take(255) }, label = { Text(stringResource(Res.string.server_address)) }, placeholder = { Text("play.example.org") }, singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, address) }, enabled = address.isNotBlank(), shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.server_add_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
