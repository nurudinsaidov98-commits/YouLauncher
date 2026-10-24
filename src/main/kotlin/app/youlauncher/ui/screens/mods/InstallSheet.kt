package app.youlauncher.ui.screens.mods

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.core.repo.DependencyInfo
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_cancel
import app.youlauncher.resources.dep_conflict
import app.youlauncher.resources.dep_in_list
import app.youlauncher.resources.dep_installed
import app.youlauncher.resources.dep_missing
import app.youlauncher.resources.dep_none
import app.youlauncher.resources.entry_failed
import app.youlauncher.resources.install_all
import app.youlauncher.resources.install_done
import app.youlauncher.resources.install_preparing
import app.youlauncher.resources.install_sheet_body_batch
import app.youlauncher.resources.install_sheet_body_single
import app.youlauncher.resources.install_sheet_title_batch
import app.youlauncher.resources.install_sheet_title_single
import app.youlauncher.resources.install_summary
import app.youlauncher.resources.install_with_deps
import app.youlauncher.resources.install_without_deps
import app.youlauncher.resources.selection_clear
import app.youlauncher.resources.selection_count
import app.youlauncher.resources.selection_install
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource

/** Окно установки: моды и под каждым его обязательные зависимости, затем ход установки. */
@Composable
fun InstallSheetHost(vm: ModsViewModel) {
    val sheet by vm.sheet.collectAsState()
    val s = sheet ?: return
    val motion = MaterialTheme.motionScheme
    AlertDialog(
        onDismissRequest = vm::dismissSheet,
        icon = { Icon(if (s.batch) Symbols.DoneAll else Symbols.Extension, null) },
        title = {
            Text(
                if (s.batch) stringResource(Res.string.install_sheet_title_batch)
                else stringResource(Res.string.install_sheet_title_single, s.entries.first().project.title),
            )
        },
        text = {
            Column(Modifier.widthIn(min = 440.dp, max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(if (s.batch) Res.string.install_sheet_body_batch else Res.string.install_sheet_body_single),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Общая зависимость нескольких модов показывается полностью только под первым из них.
                val seen = buildMap {
                    s.entries.forEachIndexed { i, e -> e.preview?.dependencies?.forEach { d -> if (d.projectId !in this) put(d.projectId, i) } }
                }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(s.entries, key = { _, e -> e.project.id }) { index, e ->
                        EntryCard(e, firstOwner = { id -> seen[id] == index }, inList = { id -> s.entries.any { it.project.id == id } })
                    }
                }
                AnimatedVisibility(
                    visible = !s.loading,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Text(
                        stringResource(Res.string.install_summary, s.installable.size, s.dependenciesToDownload),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AnimatedVisibility(
                    visible = s.running || s.finished,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                ) {
                    LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when {
                s.finished -> Button(onClick = vm::dismissSheet, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Check, stringResource(Res.string.install_done)) }
                else -> Button(
                    onClick = { vm.confirmInstall(withDependencies = true) },
                    enabled = !s.loading && !s.running && s.installable.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    IconLabel(Symbols.Download, stringResource(if (s.batch) Res.string.install_all else Res.string.install_with_deps))
                }
            }
        },
        dismissButton = {
            if (!s.finished) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::dismissSheet, enabled = !s.running, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) }
                    if (s.dependenciesToDownload > 0) {
                        OutlinedButton(
                            onClick = { vm.confirmInstall(withDependencies = false) },
                            enabled = !s.loading && !s.running,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.install_without_deps)) }
                    }
                }
            }
        },
    )
}

@Composable
private fun EntryCard(e: InstallEntry, firstOwner: (String) -> Boolean, inList: (String) -> Boolean) {
    val motion = MaterialTheme.motionScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (e.status == EntryStatus.Done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().animateContentSize(motion.defaultSpatialSpec()),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProjectIcon(e.project.title, e.project.iconUrl, size = 40.dp)
                Column(Modifier.weight(1f)) {
                    Text(e.project.title, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val v = e.preview?.version?.versionNumber ?: e.version?.versionNumber
                    if (v != null) Text(v, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                StatusMark(e.status)
            }
            val p = e.preview
            when {
                e.status == EntryStatus.Loading -> Text(stringResource(Res.string.install_preparing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                e.error != null -> Problem(stringResource(Res.string.entry_failed, e.error))
                p?.error != null -> Problem(p.error)
                else -> {
                    p?.conflicts?.takeIf { it.isNotEmpty() }?.let { Problem(stringResource(Res.string.dep_conflict, it.joinToString())) }
                    p?.missing?.takeIf { it.isNotEmpty() }?.let { Problem(stringResource(Res.string.dep_missing, it.joinToString())) }
                    val deps = p?.dependencies.orEmpty()
                    if (deps.isEmpty()) {
                        Text(stringResource(Res.string.dep_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        DependencyTree(deps, firstOwner, inList)
                    }
                }
            }
        }
    }
}

/** Зависимости под модом с линией-веткой слева, как дерево. */
@Composable
private fun DependencyTree(deps: List<DependencyInfo>, firstOwner: (String) -> Boolean, inList: (String) -> Boolean) {
    Row(Modifier.height(IntrinsicSize.Min).padding(start = 18.dp)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall))
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            deps.forEach { d ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProjectIcon(d.title, d.iconUrl, size = 28.dp)
                    Text(d.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    d.versionNumber?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                    val badge = when {
                        d.installed -> stringResource(Res.string.dep_installed)
                        inList(d.projectId) || !firstOwner(d.projectId) -> stringResource(Res.string.dep_in_list)
                        else -> null
                    }
                    if (badge != null) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(badge, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Problem(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Symbols.ErrorFilled, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusMark(status: EntryStatus) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        status,
        transitionSpec = { (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec())) togetherWith (scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())) },
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp),
    ) { st ->
        Box(contentAlignment = Alignment.Center) {
            when (st) {
                EntryStatus.Loading, EntryStatus.Installing -> LoadingIndicator(Modifier.size(32.dp))
                EntryStatus.Done -> Icon(Symbols.CheckCircleFilled, null, tint = MaterialTheme.colorScheme.primary)
                EntryStatus.Failed -> Icon(Symbols.ErrorFilled, null, tint = MaterialTheme.colorScheme.error)
                EntryStatus.Ready -> Unit
            }
        }
    }
}

/** Плавающая панель выбранных модов: выезжает снизу на пружине, когда что-то выбрано. */
@Composable
fun SelectionBar(vm: ModsViewModel, modifier: Modifier = Modifier) {
    val selected by vm.selected.collectAsState()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = selected.isNotEmpty(),
        enter = slideInVertically(motion.slowSpatialSpec()) { it * 2 } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.defaultSpatialSpec()) { it * 2 } + fadeOut(motion.fastEffectsSpec()),
        modifier = modifier,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        ) {
            AnimatedContent(
                selected.size,
                transitionSpec = { (slideInVertically(motion.fastSpatialSpec()) { -it } + fadeIn()) togetherWith (slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut()) },
                modifier = Modifier.padding(horizontal = 12.dp).align(Alignment.CenterVertically),
            ) { n -> Text(stringResource(Res.string.selection_count, n), style = MaterialTheme.typography.titleSmallEmphasized) }
            TIconButton(Symbols.Close, stringResource(Res.string.selection_clear), vm::clearSelection)
            Button(
                onClick = vm::installSelected,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 4.dp),
            ) { IconLabel(Symbols.Download, stringResource(Res.string.selection_install)) }
        }
    }
}
