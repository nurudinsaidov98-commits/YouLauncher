package app.youlauncher.ui.screens.mods

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.ContentSource
import app.youlauncher.core.model.ModProject
import app.youlauncher.core.model.ModVersion
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_retry
import app.youlauncher.resources.mods_by
import app.youlauncher.resources.mods_error_body
import app.youlauncher.resources.mods_error_title
import app.youlauncher.resources.nav_back
import app.youlauncher.resources.project_alpha
import app.youlauncher.resources.project_beta
import app.youlauncher.resources.project_description
import app.youlauncher.resources.project_downloads
import app.youlauncher.resources.project_followers
import app.youlauncher.resources.project_gallery
import app.youlauncher.resources.project_license
import app.youlauncher.resources.project_no_gallery
import app.youlauncher.resources.project_open_site
import app.youlauncher.resources.project_release
import app.youlauncher.resources.project_updated
import app.youlauncher.resources.project_versions
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.Platform
import app.youlauncher.ui.components.EmptyState
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.Illustration
import app.youlauncher.ui.components.MarkdownText
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.compactNumber
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.images.rememberRemoteImage
import app.youlauncher.ui.navigation.LocalNavigator
import app.youlauncher.ui.navigation.Screen
import app.youlauncher.ui.rememberViewModel
import app.youlauncher.ui.screens.library.Tag
import org.jetbrains.compose.resources.stringResource

fun ModProject.webUrl(): String = when (source) {
    ContentSource.Modrinth -> "https://modrinth.com/${type.name.lowercase().let { if (it == "resourcepack") "resourcepack" else it }}/$slug"
    ContentSource.CurseForge -> "https://www.curseforge.com/minecraft/mc-mods/$slug"
    ContentSource.Local -> ""
}

@Composable
fun ProjectScreen(screen: Screen.ModProject) {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val vm = rememberViewModel(screen.instanceId) { ModsViewModel(graph, screen.instanceId) }
    LaunchedEffect(screen.source) { if (screen.source == CatalogSource.CurseForge.name) vm.setSource(CatalogSource.CurseForge) }
    val state by vm.state.collectAsState()
    val progress by vm.progress.collectAsState()
    var project by remember { mutableStateOf<ModProject?>(null) }
    var versions by remember { mutableStateOf<List<ModVersion>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(screen.projectId, reload) {
        failed = false
        runCatching {
            project = vm.catalog.project(screen.projectId)
            val t = state.target
            versions = vm.catalog.versions(screen.projectId, t?.gameVersion, t?.loader?.takeIf { it.name != "Vanilla" })
        }.onFailure { failed = true }
    }

    val p = project
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TIconButton(Symbols.ArrowBack, stringResource(Res.string.nav_back), { navigator.back() })
        }
        when {
            failed -> EmptyState(Illustration.Offline, stringResource(Res.string.mods_error_title), stringResource(Res.string.mods_error_body)) {
                FilledTonalButton(onClick = { reload++ }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Refresh, stringResource(Res.string.action_retry)) }
            }
            p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            else -> {
                Row(Modifier.padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProjectIcon(p.title, p.iconUrl, size = 96.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.title, style = MaterialTheme.typography.displaySmallEmphasized)
                        Text(stringResource(Res.string.mods_by, p.author), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(p.summary, style = MaterialTheme.typography.bodyLarge)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val checking by vm.checking.collectAsState()
                        InstallButton(p.id in state.installed, progress[p.id], p.id in checking) { vm.install(p) }
                        if (p.webUrl().isNotEmpty()) {
                            FilledTonalButton(onClick = { Platform.openUrl(p.webUrl()) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.OpenInNew, stringResource(Res.string.project_open_site)) }
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 32.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Fact(stringResource(Res.string.project_downloads), compactNumber(p.downloads))
                    Fact(stringResource(Res.string.project_followers), compactNumber(p.follows))
                    Fact(stringResource(Res.string.project_updated), timeAgo(p.updatedAt))
                    p.license?.let { Fact(stringResource(Res.string.project_license), it) }
                }
                val tabs = listOf(Res.string.project_description, Res.string.project_gallery, Res.string.project_versions)
                PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, modifier = Modifier.padding(horizontal = 16.dp)) {
                    tabs.forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) }) }
                }
                val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedContent(tab, transitionSpec = { fadeIn(effectsIn) togetherWith fadeOut(effectsOut) }, modifier = Modifier.weight(1f), label = "project-tab") { t ->
                    when (t) {
                        0 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp)) {
                            MarkdownText(p.description.ifBlank { p.summary }, Modifier.widthIn(max = 900.dp))
                        }
                        1 -> Gallery(p.gallery)
                        else -> VersionsList(versions, onInstall = { v -> vm.install(p, v) })
                    }
                }
            }
        }
    }
    InstallSheetHost(vm)
}

@Composable
private fun Fact(label: String, value: String) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Gallery(urls: List<String>) {
    if (urls.isEmpty()) {
        EmptyState(Illustration.Screenshots, stringResource(Res.string.project_no_gallery), "")
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(320.dp), contentPadding = PaddingValues(32.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(urls) { url ->
            Card(onClick = { Platform.openUrl(url) }, shape = MaterialTheme.shapes.large) {
                val image = rememberRemoteImage(url, 720)
                if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                else Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@Composable
private fun VersionsList(versions: List<ModVersion>?, onInstall: (ModVersion) -> Unit) {
    if (versions == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(versions, key = { it.id }) { v ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ListItem(
                    headlineContent = { Text(v.name, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text("${v.gameVersions.take(4).joinToString()} · ${v.loaders.joinToString()} · ${timeAgo(v.publishedAt)}") },
                    leadingContent = {
                        val (label, color) = when (v.type) {
                            "beta" -> stringResource(Res.string.project_beta) to MaterialTheme.colorScheme.tertiaryContainer
                            "alpha" -> stringResource(Res.string.project_alpha) to MaterialTheme.colorScheme.errorContainer
                            else -> stringResource(Res.string.project_release) to MaterialTheme.colorScheme.primaryContainer
                        }
                        Tag(label, color)
                    },
                    trailingContent = { InstallButton(installed = false, progress = null, onInstall = { onInstall(v) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                )
            }
        }
    }
}
