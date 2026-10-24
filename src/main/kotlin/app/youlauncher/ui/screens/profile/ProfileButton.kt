package app.youlauncher.ui.screens.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.youlauncher.resources.Res
import app.youlauncher.resources.profile_open
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.LocalAppUi
import app.youlauncher.ui.components.ProfileAvatar
import app.youlauncher.ui.components.WithTooltip
import app.youlauncher.ui.images.rememberRemoteImage
import app.youlauncher.ui.navigation.LocalNavigator
import org.jetbrains.compose.resources.stringResource

/** Попап на всё окно: затемнение и панель профиля под аватаром. */
private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}

/**
 * Аватар профиля в заголовке окна. По нажатию из него «вырастает» панель профиля:
 * масштаб и раскрытие от угла аватара на пружинах темы, остальное окно затемняется.
 */
@Composable
fun ProfileButton() {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val profile = graph.profile.profile.collectAsState().value
    val label = stringResource(Res.string.profile_open)
    val visible = remember { MutableTransitionState(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    // Команда «Профиль» из палитры открывает ту же панель.
    val ui = LocalAppUi.current
    LaunchedEffect(ui.pendingAction) { if (ui.consume("profile")) visible.targetState = true }
    // При открытии панели скин активного аккаунта Ely.by обновляется: новый скин с сайта виден без перезапуска.
    LaunchedEffect(visible.targetState) {
        if (visible.targetState) graph.accounts.activeId.value?.let { id -> runCatching { graph.accounts.refresh(id) } }
    }

    WithTooltip(label) {
        IconButton(
            onClick = { visible.targetState = !visible.targetState },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier
                .semantics { contentDescription = label }
                .onGloballyPositioned { anchor = it.boundsInWindow() },
        ) {
            ProfileAvatar(profile, 28.dp, Modifier.clip(CircleShape), pictureMaxSize = 96)
        }
    }

    if (visible.currentState || visible.targetState) {
        val close = { visible.targetState = false }
        val motion = MaterialTheme.motionScheme
        val density = LocalDensity.current
        Popup(
            popupPositionProvider = WindowOrigin,
            onDismissRequest = close,
            properties = PopupProperties(focusable = true),
            onKeyEvent = { e -> if (e.key == Key.Escape) { close(); true } else false },
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(motion.defaultEffectsSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()),
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
                    )
                }
                val x = with(density) { anchor.left.toDp() }
                val y = with(density) { anchor.bottom.toDp() + 6.dp }
                val origin = TransformOrigin(0f, 0f)
                AnimatedVisibility(
                    visibleState = visible,
                    enter = scaleIn(motion.slowSpatialSpec(), initialScale = 0.4f, transformOrigin = origin) +
                        expandIn(motion.defaultSpatialSpec(), expandFrom = Alignment.TopStart) +
                        fadeIn(motion.defaultEffectsSpec()),
                    exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f, transformOrigin = origin) +
                        shrinkOut(motion.fastSpatialSpec(), shrinkTowards = Alignment.TopStart) +
                        fadeOut(motion.fastEffectsSpec()),
                    modifier = Modifier.offset(x, y),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.graphicsLayer { clip = false },
                    ) {
                        ProfilePanel(onNavigate = { top -> close(); navigator.selectTop(top) })
                    }
                }
            }
        }
    }
}
