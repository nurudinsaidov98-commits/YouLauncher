package app.youlauncher.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.youlauncher.core.model.Instance
import app.youlauncher.core.repo.LaunchState
import app.youlauncher.resources.Res
import app.youlauncher.resources.action_play
import app.youlauncher.resources.action_stop
import app.youlauncher.resources.action_more
import app.youlauncher.resources.home_not_played
import app.youlauncher.resources.menu_delete
import app.youlauncher.resources.menu_duplicate
import app.youlauncher.resources.menu_favorite_add
import app.youlauncher.resources.menu_favorite_remove
import app.youlauncher.resources.menu_open
import app.youlauncher.resources.menu_open_folder
import app.youlauncher.resources.menu_play
import app.youlauncher.resources.menu_rename
import app.youlauncher.resources.menu_shortcut
import app.youlauncher.ui.components.InstanceBadge
import app.youlauncher.ui.components.InstanceCover
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.components.VersionCover
import app.youlauncher.ui.components.duration
import app.youlauncher.ui.components.isBusy
import app.youlauncher.ui.components.timeAgo
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.navigation.sharedElement
import org.jetbrains.compose.resources.stringResource

enum class InstanceAction { Play, Open, Favorite, Rename, Duplicate, Folder, Shortcut, Delete }

/**
 * Карточка экземпляра: обложка-сцена версии, значок в форме M3, мета-строка.
 * При наведении карточка приподнимается на пружине и появляется кнопка запуска.
 * Правый клик открывает контекстное меню M3.
 */
@Composable
fun InstanceCard(
    instance: Instance,
    launch: LaunchState,
    onClick: () -> Unit,
    onAction: (InstanceAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lift by animateFloatAsState(if (hovered || focused) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "lift")
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    Box(modifier) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, hoveredElevation = 6.dp, focusedElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .sharedElement("instance-${instance.id}")
                .graphicsLayer { translationY = -4.dp.toPx() * lift }
                .onPointerEvent(PointerEventType.Press) { e ->
                    if (e.buttons.isSecondaryPressed) menuAt = e.changes.first().position
                },
        ) {
            Box(Modifier.fillMaxWidth().height(136.dp)) {
                InstanceCover(instance, Modifier.matchParentSize(), blur = 16.dp)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to MaterialTheme.colorScheme.surfaceContainerLow)))
                Row(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (instance.favorite) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)) {
                            Icon(Symbols.StarFilled, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(6.dp).size(18.dp))
                        }
                    }
                }
                HoverPlayButton(
                    visible = hovered || focused || launch.isBusy,
                    busy = launch.isBusy,
                    onClick = { onAction(InstanceAction.Play) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
            Row(Modifier.padding(start = 16.dp, end = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                InstanceBadge(instance.icon, instance.gameVersion, size = 44.dp, modifier = Modifier.offset(y = (-10).dp), accent = instance.accent)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(instance.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${instance.gameVersion} · ${instance.loader.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    val played = if (instance.lastPlayedAt != null) timeAgo(instance.lastPlayedAt) else stringResource(Res.string.home_not_played)
                    Text(
                        if (instance.totalPlayTimeMs > 0) "$played · ${duration(instance.totalPlayTimeMs)}" else played,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                TIconButton(
                    Symbols.MoreVert,
                    stringResource(Res.string.action_more),
                    { menuAt = Offset(with(density) { 200.dp.toPx() }, with(density) { 190.dp.toPx() }) },
                    colors = IconButtonDefaults.iconButtonColors(),
                )
            }
        }
        val at = menuAt
        if (at != null) {
            InstanceMenu(
                instance = instance,
                busy = launch.isBusy,
                offset = with(density) { DpOffset(at.x.toDp(), at.y.toDp() - 280.dp) },
                onDismiss = { menuAt = null },
                onAction = { menuAt = null; onAction(it) },
            )
        }
    }
}

@Composable
fun InstanceMenu(instance: Instance, busy: Boolean, offset: DpOffset, onDismiss: () -> Unit, onAction: (InstanceAction) -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss, offset = offset, shape = MaterialTheme.shapes.large) {
        DropdownMenuItem(
            text = { Text(stringResource(if (busy) Res.string.action_stop else Res.string.menu_play)) },
            leadingIcon = { Icon(if (busy) Symbols.Stop else Symbols.PlayArrow, null) },
            onClick = { onAction(InstanceAction.Play) },
        )
        DropdownMenuItem(text = { Text(stringResource(Res.string.menu_open)) }, leadingIcon = { Icon(Symbols.OpenInFull, null) }, onClick = { onAction(InstanceAction.Open) })
        DropdownMenuItem(
            text = { Text(stringResource(if (instance.favorite) Res.string.menu_favorite_remove else Res.string.menu_favorite_add)) },
            leadingIcon = { Icon(if (instance.favorite) Symbols.StarFilled else Symbols.Star, null) },
            onClick = { onAction(InstanceAction.Favorite) },
        )
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(Res.string.menu_rename)) }, leadingIcon = { Icon(Symbols.Edit, null) }, onClick = { onAction(InstanceAction.Rename) })
        DropdownMenuItem(text = { Text(stringResource(Res.string.menu_duplicate)) }, leadingIcon = { Icon(Symbols.ContentCopy, null) }, onClick = { onAction(InstanceAction.Duplicate) })
        DropdownMenuItem(text = { Text(stringResource(Res.string.menu_open_folder)) }, leadingIcon = { Icon(Symbols.FolderOpen, null) }, onClick = { onAction(InstanceAction.Folder) })
        DropdownMenuItem(text = { Text(stringResource(Res.string.menu_shortcut)) }, leadingIcon = { Icon(Symbols.SwitchAccessShortcut, null) }, onClick = { onAction(InstanceAction.Shortcut) })
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(Res.string.menu_delete), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Symbols.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onAction(InstanceAction.Delete) },
        )
    }
}


@Composable
private fun HoverPlayButton(visible: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = if (busy) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            elevation = FloatingActionButtonDefaults.loweredElevation(),
        ) {
            Icon(
                if (busy) Symbols.StopFilled else Symbols.PlayArrowFilled,
                contentDescription = stringResource(if (busy) Res.string.action_stop else Res.string.action_play),
            )
        }
    }
}