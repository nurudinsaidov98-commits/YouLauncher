package app.youlauncher.ui.window

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.youlauncher.resources.Res
import app.youlauncher.resources.app_name
import app.youlauncher.resources.window_close
import app.youlauncher.resources.window_maximize
import app.youlauncher.resources.window_minimize
import app.youlauncher.resources.window_restore
import app.youlauncher.resources.window_search_hint
import app.youlauncher.ui.brand.YouLauncherLogo
import app.youlauncher.ui.components.TIconButton
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor

/** Перетаскивание окна за область и разворачивание двойным щелчком. */
fun Modifier.windowDragArea(controller: WindowController): Modifier = this
    .pointerInput(controller) {
        detectTapGestures(onDoubleTap = { controller.toggleMaximize() })
    }
    .pointerInput(controller) {
        detectDragGestures(
            onDragStart = { controller.startDrag() },
            onDragEnd = { controller.endDrag() },
            onDragCancel = { controller.endDrag() },
        ) { change, _ ->
            change.consume()
            controller.dragMove()
        }
    }

/**
 * Заголовочная панель M3: логотип, поле командной палитры (Ctrl+K) и кнопки окна.
 * Вся пустая область панели перетаскивает окно.
 */
@Composable
fun TitleBar(
    onOpenPalette: () -> Unit,
    shortcutLabel: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
) {
    val controller = LocalWindowController.current
    val contentAlpha = if (controller.isFocused) 1f else 0.6f
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .windowDragArea(controller),
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            YouLauncherLogo(size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(Res.string.app_name),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Spacer(Modifier.width(12.dp))
            leading()
            Spacer(Modifier.weight(1f))
            WindowButtons(controller)
        }
        CommandField(
            onClick = onOpenPalette,
            shortcutLabel = shortcutLabel,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CommandField(onClick: () -> Unit, shortcutLabel: String, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.motionScheme.fastEffectsSpec(),
    )
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        interactionSource = interaction,
        modifier = modifier.width(420.dp).height(36.dp).pointerHoverIcon(PointerIcon.Hand),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Symbols.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                stringResource(Res.string.window_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            KeyHint(shortcutLabel)
        }
    }
}

@Composable
fun KeyHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun WindowButtons(controller: WindowController) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        TIconButton(
            icon = Symbols.Minimize,
            contentDescription = stringResource(Res.string.window_minimize),
            onClick = controller::minimize,
        )
        TIconButton(
            icon = if (controller.isMaximized) Symbols.FilterNone else Symbols.CropSquare,
            contentDescription = stringResource(if (controller.isMaximized) Res.string.window_restore else Res.string.window_maximize),
            onClick = controller::toggleMaximize,
        )
        val closeInteraction = remember { MutableInteractionSource() }
        val hovered by closeInteraction.collectIsHoveredAsState()
        val container by animateColorAsState(
            if (hovered) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
            MaterialTheme.motionScheme.fastEffectsSpec(),
        )
        val content by animateColorAsState(
            if (hovered) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.motionScheme.fastEffectsSpec(),
        )
        TIconButton(
            icon = Symbols.Close,
            contentDescription = stringResource(Res.string.window_close),
            onClick = controller::close,
            colors = IconButtonDefaults.iconButtonColors(containerColor = container, contentColor = content),
            interactionSource = closeInteraction,
            modifier = Modifier.semantics { role = Role.Button },
        )
    }
}

/** Невидимые зоны по краям окна для изменения размера. */
@Composable
fun ResizeHandles(controller: WindowController) {
    if (controller.isMaximized) return
    val thickness = 6.dp
    val corner = 12.dp
    Box(Modifier.fillMaxSize()) {
        ResizeZone(ResizeEdge.Left, Cursor.W_RESIZE_CURSOR, controller, Modifier.align(Alignment.CenterStart).width(thickness).fillMaxHeightWithin())
        ResizeZone(ResizeEdge.Right, Cursor.E_RESIZE_CURSOR, controller, Modifier.align(Alignment.CenterEnd).width(thickness).fillMaxHeightWithin())
        ResizeZone(ResizeEdge.Top, Cursor.N_RESIZE_CURSOR, controller, Modifier.align(Alignment.TopCenter).height(thickness).fillMaxWidth())
        ResizeZone(ResizeEdge.Bottom, Cursor.S_RESIZE_CURSOR, controller, Modifier.align(Alignment.BottomCenter).height(thickness).fillMaxWidth())
        ResizeZone(ResizeEdge.TopLeft, Cursor.NW_RESIZE_CURSOR, controller, Modifier.align(Alignment.TopStart).size(corner))
        ResizeZone(ResizeEdge.TopRight, Cursor.NE_RESIZE_CURSOR, controller, Modifier.align(Alignment.TopEnd).size(corner))
        ResizeZone(ResizeEdge.BottomLeft, Cursor.SW_RESIZE_CURSOR, controller, Modifier.align(Alignment.BottomStart).size(corner))
        ResizeZone(ResizeEdge.BottomRight, Cursor.SE_RESIZE_CURSOR, controller, Modifier.align(Alignment.BottomEnd).size(corner))
    }
}

private fun Modifier.fillMaxHeightWithin() = fillMaxHeight()

@Composable
private fun ResizeZone(edge: ResizeEdge, cursor: Int, controller: WindowController, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(edge) {
                detectDragGestures(
                    onDragStart = { controller.startResize() },
                    onDragEnd = { controller.endResize() },
                    onDragCancel = { controller.endResize() },
                ) { change, _ ->
                    change.consume()
                    controller.resize(edge)
                }
            },
    )
}
