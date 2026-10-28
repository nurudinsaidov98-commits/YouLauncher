package app.youlauncher.ui.screens.instance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.data.horizon.HorizonInstalledMod
import app.youlauncher.data.horizon.HorizonModIndex
import app.youlauncher.data.horizon.HorizonModKind
import app.youlauncher.data.horizon.HorizonModProject
import app.youlauncher.data.horizon.HorizonModRules
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.UiMessage
import app.youlauncher.ui.components.ExpressiveScrollbar
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.compactNumber
import app.youlauncher.ui.icons.Symbols
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Моды для экземпляра Horizon из его реестра: моды .hmod, ресурспаки и шейдеры Horizon. Java-моды в Horizon
 * не работают, поэтому здесь вместо каталога Modrinth и CurseForge его собственный магазин.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HorizonModsTab(inst: Instance) {
    val graph = LocalAppGraph.current
    val store = graph.horizonMods
    val gameDir = remember(inst.id) { graph.instances.gameDir(inst.id) }
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf<HorizonModIndex?>(store.cached()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    // Список установленного и включённых модов читается с диска; после каждого действия перечитывается.
    var installedVersion by remember { mutableIntStateOf(0) }
    val installed = remember(installedVersion, inst.id) { store.installed(gameDir).associateBy { it.id } }
    val enabled = remember(installedVersion, inst.id) { installed.keys.filter { store.isEnabled(gameDir, it) }.toSet() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf<HorizonModKind?>(null) }

    LaunchedEffect(reload) {
        error = null
        runCatching { store.index() }.onSuccess { index = it }.onFailure { if (index == null) error = it.message ?: it::class.simpleName }
    }

    val projects = remember(index, query, kind, installed) {
        index?.projects.orEmpty()
            .filter { p -> HorizonModRules.kindOf(p) != null && (kind == null || HorizonModRules.kindOf(p) == kind) }
            .filter { p -> query.isBlank() || listOf(p.name, p.summary, p.id).any { it.contains(query.trim(), ignoreCase = true) } || p.authors.any { it.contains(query.trim(), ignoreCase = true) } }
            .sortedWith(compareByDescending<HorizonModProject> { it.id in installed }.thenByDescending { it.downloads ?: 0L })
    }

    fun act(p: HorizonModProject, block: suspend () -> Unit) {
        if (busy[p.id] == true) return
        busy[p.id] = true
        scope.launch {
            runCatching { block() }.onFailure { e ->
                graph.messenger.show(UiMessage(Res.string.horizon_failed_msg, listOf(p.name, e.message ?: e::class.simpleName.orEmpty()), error = true))
            }
            busy.remove(p.id)
            installedVersion++
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Symbols.Bolt, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.horizon_mods_title), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(stringResource(Res.string.horizon_mods_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(Res.string.horizon_mods_search)) },
            leadingIcon = { Icon(Symbols.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Symbols.Close, stringResource(Res.string.action_clear)) } }) else null,
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text(stringResource(Res.string.horizon_kind_all)) })
            HorizonModKind.entries.forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(stringResource(kindLabel(k))) })
            }
        }
        when {
            index == null && error == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            index == null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.horizon_load_failed, error.orEmpty()), color = MaterialTheme.colorScheme.error)
                FilledTonalButton(onClick = { reload++ }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Refresh, stringResource(Res.string.horizon_retry)) }
            }
            projects.isEmpty() -> Text(stringResource(Res.string.horizon_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            else -> {
                val list = rememberLazyListState()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(end = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(projects, key = { it.id }) { p ->
                            HorizonModRow(
                                p = p,
                                index = index!!,
                                gameVersion = inst.gameVersion,
                                installed = installed[p.id],
                                enabled = p.id in enabled,
                                busy = busy[p.id] == true,
                                onInstall = {
                                    act(p) {
                                        store.install(gameDir, p, inst.gameVersion)
                                        graph.messenger.show(Res.string.horizon_installed_msg, p.name)
                                    }
                                },
                                onRemove = {
                                    act(p) {
                                        store.remove(gameDir, p.id)
                                        graph.messenger.show(Res.string.horizon_removed_msg, p.name)
                                    }
                                },
                                onToggle = { on -> act(p) { store.setEnabled(gameDir, p.id, on) } },
                            )
                        }
                        item { Text(stringResource(Res.string.horizon_restart_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    }
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                        ExpressiveScrollbar(list, label = { projects.getOrNull(it)?.name })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HorizonModRow(
    p: HorizonModProject,
    index: HorizonModIndex,
    gameVersion: String,
    installed: HorizonInstalledMod?,
    enabled: Boolean,
    busy: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val newest = remember(p, index, gameVersion) { HorizonModRules.newest(index, p, gameVersion) }
    val revoked = installed?.let { HorizonModRules.revoked(index, p.id, it.sha256) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ProjectIcon(p.name, p.icon, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.official) Icon(Symbols.Verified, stringResource(Res.string.horizon_official), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                Text(p.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    if (p.official) stringResource(Res.string.horizon_official) else p.authors.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.horizon_by, it.joinToString(", ")) },
                    installed?.let { stringResource(Res.string.horizon_installed, it.version) } ?: newest?.version,
                    p.license,
                    p.downloads?.let { "↓ " + compactNumber(it) },
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val caps = (newest ?: p.versions.firstOrNull())?.capabilities.orEmpty()
                if (caps.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        caps.forEach { c ->
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(capabilityLabel(c), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
                revoked?.let { Text(stringResource(Res.string.horizon_revoked, it.reason), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            }
            when {
                busy -> LoadingIndicator(Modifier.size(40.dp))
                installed != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (newest != null && newest.version != installed.version) {
                        FilledTonalButton(onClick = onInstall, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.horizon_update, newest.version)) }
                    }
                    if (installed.kind == HorizonModKind.Mod.key) Switch(checked = enabled, onCheckedChange = onToggle, enabled = revoked == null)
                    IconButton(onClick = onRemove) { Icon(Symbols.Delete, stringResource(Res.string.horizon_remove)) }
                }
                newest == null -> Text(stringResource(Res.string.horizon_no_version, gameVersion), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Button(onClick = onInstall, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Download, stringResource(Res.string.horizon_install)) }
            }
        }
    }
}

private fun kindLabel(k: HorizonModKind): StringResource = when (k) {
    HorizonModKind.Mod -> Res.string.horizon_kind_mod
    HorizonModKind.ResourcePack -> Res.string.horizon_kind_resourcepack
    HorizonModKind.Shader -> Res.string.horizon_kind_shader
}

/** Что мод может делать, словами игры; незнакомое разрешение показывается как есть. */
@Composable
private fun capabilityLabel(c: String): String = when (c) {
    "game-read" -> stringResource(Res.string.horizon_cap_game_read)
    "hud" -> stringResource(Res.string.horizon_cap_hud)
    "chat" -> stringResource(Res.string.horizon_cap_chat)
    "render" -> stringResource(Res.string.horizon_cap_render)
    "recipes" -> stringResource(Res.string.horizon_cap_recipes)
    "ui" -> stringResource(Res.string.horizon_cap_ui)
    "storage" -> stringResource(Res.string.horizon_cap_storage)
    "input" -> stringResource(Res.string.horizon_cap_input)
    else -> c
}
