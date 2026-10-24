package app.youlauncher.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.ui.theme.LocalMotionLevel

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Общий элемент между экранами (карточка экземпляра и шапка его страницы): границы перетекают
 * по пространственной пружине выразительной схемы. При выключенных анимациях не применяется.
 */
fun Modifier.sharedElement(key: String): Modifier = composed {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (shared == null || animated == null || LocalMotionLevel.current == MotionLevel.Off) return@composed this
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    with(shared) {
        this@composed.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = BoundsTransform { _, _ -> spec },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
