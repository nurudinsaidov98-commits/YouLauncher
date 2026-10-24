package app.youlauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.MotionLevel
import app.youlauncher.core.settings.UiDensity
import app.youlauncher.ui.theme.LocalMotionLevel
import kotlinx.coroutines.delay

/** Плотность интерфейса из настроек: отступы экранов и списков. */
val LocalUiDensity = staticCompositionLocalOf { UiDensity.Comfortable }

object Gaps {
    val screen: Dp @Composable get() = if (LocalUiDensity.current == UiDensity.Compact) 20.dp else 32.dp
    val section: Dp @Composable get() = if (LocalUiDensity.current == UiDensity.Compact) 14.dp else 24.dp
    val item: Dp @Composable get() = if (LocalUiDensity.current == UiDensity.Compact) 8.dp else 12.dp
}

/** Заголовок экрана: крупный выразительный текст, подзаголовок и действия справа. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmallEmphasized)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** Ограничение ширины содержимого на очень широких окнах (до 2560 px): читаемость строк. */
@Composable
fun MaxWidth(max: Dp = 1600.dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxWidth()) { content() }
    }
}

/** Ширина области содержимого для адаптивной раскладки. */
enum class WidthClass { Compact, Medium, Expanded, Large }

@Composable
fun AdaptiveWidth(content: @Composable (WidthClass) -> Unit) {
    BoxWithConstraints {
        val wc = when {
            maxWidth < 760.dp -> WidthClass.Compact
            maxWidth < 1100.dp -> WidthClass.Medium
            maxWidth < 1700.dp -> WidthClass.Expanded
            else -> WidthClass.Large
        }
        content(wc)
    }
}

/**
 * Каскадное (stagger) появление элементов списка: каждый следующий элемент стартует с небольшой
 * задержкой и въезжает по пространственной пружине. При уменьшенных анимациях не применяется.
 */
fun Modifier.staggered(index: Int, key: Any? = Unit): Modifier = composed {
    val motion = LocalMotionLevel.current
    if (motion != MotionLevel.Full) return@composed this
    val progress = remember(key) { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(key) {
        delay((index.coerceAtMost(12) * 35).toLong())
        progress.animateTo(1f, spatial)
    }
    graphicsLayer {
        // Пружина темы проскакивает 1 и возвращается: элемент слегка «подпрыгивает» на месте.
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 56f
        scaleX = 0.9f + 0.1f * p
        scaleY = scaleX
    }
}

fun Modifier.screenPadding(horizontal: Dp, top: Dp = horizontal): Modifier = padding(start = horizontal, end = horizontal, top = top)
