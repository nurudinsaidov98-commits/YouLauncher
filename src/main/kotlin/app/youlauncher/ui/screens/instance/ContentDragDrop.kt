package app.youlauncher.ui.screens.instance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.youlauncher.resources.Res
import app.youlauncher.resources.drop_install
import app.youlauncher.resources.drop_install_hint
import app.youlauncher.resources.drop_remove
import app.youlauncher.resources.drop_remove_hint
import app.youlauncher.ui.components.ProjectIcon
import app.youlauncher.ui.icons.Symbols
import org.jetbrains.compose.resources.stringResource

/** Что тянут: установленный файл (бросить в каталог — удалить) или проект каталога (бросить в установленное — поставить). */
sealed class DragPayload(val key: String, val title: String, val iconUrl: String?, val action: () -> Unit) {
    class Installed(key: String, title: String, remove: () -> Unit) : DragPayload(key, title, null, remove)
    class Catalog(key: String, title: String, iconUrl: String?, install: () -> Unit) : DragPayload(key, title, iconUrl, install)
}

/**
 * Перетаскивание между панелями «Установлено» и «Каталог». Координаты — в системе окна: панели сообщают
 * свои границы, источник — точку под курсором. Отпустили над другой панелью — выполняется действие.
 */
@Stable
class ContentDrag {
    var payload by mutableStateOf<DragPayload?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    /** Левый верхний угол области панелей в окне: «призрак» рисуется относительно неё. */
    var origin by mutableStateOf(Offset.Zero)
    var installedBounds by mutableStateOf(Rect.Zero)
    var catalogBounds by mutableStateOf(Rect.Zero)

    /** Курсор над панелью, куда этот предмет можно бросить. */
    val overTarget: Boolean get() = when (payload) {
        is DragPayload.Installed -> catalogBounds.contains(pointer)
        is DragPayload.Catalog -> installedBounds.contains(pointer)
        null -> false
    }

    fun start(p: DragPayload, at: Offset) { payload = p; pointer = at }
    fun move(delta: Offset) { pointer += delta }
    fun cancel() { payload = null }
    fun drop() {
        val p = payload ?: return
        val hit = overTarget
        payload = null
        if (hit) p.action()
    }
}

/** null — перетаскивания нет (узкое окно, панели не видны вместе). */
val LocalContentDrag = staticCompositionLocalOf<ContentDrag?> { null }

/** Строку можно взять мышью и перетащить в другую панель; пока её тянут, на месте остаётся бледный след. */
@Composable
fun Modifier.dragSource(payload: () -> DragPayload, key: String): Modifier {
    val drag = LocalContentDrag.current ?: return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    val current by rememberUpdatedState(payload)
    val dragged = drag.payload?.key == key
    val alpha by animateFloatAsState(if (dragged) 0.35f else 1f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "dragSource")
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .graphicsLayer { this.alpha = alpha }
        .pointerInput(key) {
            detectDragGestures(
                onDragStart = { at -> drag.start(current(), origin + at) },
                onDragEnd = { drag.drop() },
                onDragCancel = { drag.cancel() },
            ) { change, amount -> change.consume(); drag.move(amount) }
        }
}

/** То, что тянут: таблетка со значком и названием под курсором; над целью увеличивается и подсказывает действие. */
@Composable
fun DragGhost(drag: ContentDrag) {
    val p = drag.payload ?: return
    val remove = p is DragPayload.Installed
    val over = drag.overTarget
    val scale by animateFloatAsState(if (over) 1.08f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "ghostScale")
    val container by animateColorAsState(
        when {
            !over -> MaterialTheme.colorScheme.surfaceContainerHighest
            remove -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "ghostColor",
    )
    val local = drag.pointer - drag.origin
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        shadowElevation = 12.dp,
        modifier = Modifier
            .offset { IntOffset((local.x - 30.dp.toPx()).toInt(), (local.y - 30.dp.toPx()).toInt()) }
            .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = -3f },
    ) {
        Row(Modifier.padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProjectIcon(p.title, p.iconUrl, size = 40.dp)
            Column(Modifier.widthIn(max = 260.dp)) {
                Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (over) {
                    Text(
                        stringResource(if (remove) Res.string.drop_remove else Res.string.drop_install),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remove) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Зона сброса поверх панели: проявляется, пока тянут подходящее, и откликается, когда курсор над ней —
 * форма M3 в центре вырастает на пружине, фон становится насыщеннее.
 */
@Composable
fun DropZone(visible: Boolean, over: Boolean, remove: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.96f),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.98f),
    ) {
        val accent = if (remove) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val container = if (remove) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
        val onContainer = if (remove) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        val background by animateColorAsState(container.copy(alpha = if (over) 0.92f else 0.7f), MaterialTheme.motionScheme.fastEffectsSpec(), label = "zoneBg")
        val shapeSize by animateDpAsState(if (over) 132.dp else 104.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "zoneShape")
        val spin by animateFloatAsState(if (over) 30f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "zoneSpin")
        Box(
            Modifier.fillMaxSize().padding(4.dp)
                .background(background, RoundedCornerShape(32.dp))
                .border(BorderStroke(if (over) 3.dp else 2.dp, accent.copy(alpha = if (over) 1f else 0.6f)), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = (if (remove) MaterialShapes.Cookie12Sided else MaterialShapes.Sunny).toShape(),
                    color = accent,
                    modifier = Modifier.size(shapeSize).graphicsLayer { rotationZ = spin },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (remove) Symbols.Delete else Symbols.Download, null, tint = if (remove) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp).graphicsLayer { rotationZ = -spin })
                    }
                }
                Text(
                    stringResource(
                        when {
                            remove && over -> Res.string.drop_remove
                            remove -> Res.string.drop_remove_hint
                            over -> Res.string.drop_install
                            else -> Res.string.drop_install_hint
                        },
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = onContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
