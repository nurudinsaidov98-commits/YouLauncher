package app.youlauncher.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Геометрия ползунка по состоянию списка: доля прокрутки и доля видимой части. Размер элементов
 * оценивается по видимым (в списке версий они одинаковые). Чистая функция: проверяется тестом.
 */
internal data class ScrollGeometry(val offsetFraction: Float, val visibleFraction: Float) {
    val scrollable: Boolean get() = visibleFraction < 0.999f

    companion object {
        fun of(total: Int, firstIndex: Int, firstOffset: Int, visibleSizes: List<Int>, viewport: Int, spacing: Int): ScrollGeometry {
            if (total == 0 || viewport <= 0 || visibleSizes.isEmpty()) return ScrollGeometry(0f, 1f)
            val item = visibleSizes.average().toFloat() + spacing
            val content = item * total - spacing
            if (content <= viewport) return ScrollGeometry(0f, 1f)
            val scrolled = firstIndex * item + firstOffset
            return ScrollGeometry((scrolled / (content - viewport)).coerceIn(0f, 1f), (viewport / content).coerceIn(0.05f, 1f))
        }
    }
}

/**
 * Полоса прокрутки в духе Material You: ползунок-таблетка на дорожке, при наведении и перетаскивании
 * расширяется на пружине темы и берёт основной цвет. Пока ползунок тянут, рядом всплывает пузырь
 * с подписью элемента под ним ([label], например номер версии), как быстрая прокрутка в Android.
 * Нажатие на дорожку плавно прокручивает к этому месту. Когда всё помещается, полосы нет.
 */
@Composable
fun ExpressiveScrollbar(state: LazyListState, modifier: Modifier = Modifier, label: ((Int) -> String?)? = null) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    var trackHeight by remember { mutableStateOf(0) }
    val geometry by remember {
        derivedStateOf {
            val info = state.layoutInfo
            ScrollGeometry.of(
                total = info.totalItemsCount,
                firstIndex = state.firstVisibleItemIndex,
                firstOffset = state.firstVisibleItemScrollOffset,
                visibleSizes = info.visibleItemsInfo.map { it.size },
                viewport = info.viewportEndOffset - info.viewportStartOffset,
                spacing = info.mainAxisItemSpacing,
            )
        }
    }
    if (!geometry.scrollable) return

    val active = hovered || dragging
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<androidx.compose.ui.graphics.Color>()
    val thumbWidth by animateDpAsState(if (dragging) 12.dp else if (hovered) 10.dp else 6.dp, spatial, label = "thumbWidth")
    val thumbColor by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), effects, label = "thumbColor")
    val trackAlpha by animateFloatAsState(if (active) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "track")
    val bubble by animateFloatAsState(if (dragging && label != null) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "bubble")

    val minThumb = with(density) { 36.dp.roundToPx() }
    val thumbPx = (trackHeight * geometry.visibleFraction).roundToInt().coerceIn(minThumb.coerceAtMost(trackHeight), trackHeight.coerceAtLeast(1))
    val thumbTop = ((trackHeight - thumbPx) * geometry.offsetFraction).roundToInt()

    /** Сколько пикселей содержимого приходится на пиксель хода ползунка. */
    fun contentPerTrackPixel(): Float {
        val free = (trackHeight - thumbPx).coerceAtLeast(1)
        val info = state.layoutInfo
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        val content = viewport / geometry.visibleFraction
        return (content - viewport) / free
    }

    Box(
        modifier
            .width(24.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeight = it.height }
            .hoverable(interaction)
            .pointerInput(state) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, delta ->
                    change.consume()
                    state.dispatchRawDelta(delta * contentPerTrackPixel())
                }
            }
            .pointerInput(state) {
                detectTapGestures { pos ->
                    // Нажатие выше или ниже ползунка: пружинная прокрутка так, чтобы ползунок встал под палец.
                    val target = pos.y - thumbPx / 2f
                    val delta = (target - thumbTop) * contentPerTrackPixel()
                    scope.launch { state.animateScrollBy(delta) }
                }
            },
    ) {
        // Дорожка проявляется, когда с полосой взаимодействуют.
        Box(
            Modifier.align(Alignment.Center).width(12.dp).fillMaxHeight()
                .graphicsLayer { alpha = trackAlpha }
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbTop) }
                .size(thumbWidth, with(density) { thumbPx.toDp() })
                .background(thumbColor, RoundedCornerShape(50)),
        )
        if (label != null && bubble > 0.01f) {
            val index = remember(geometry, state.layoutInfo.totalItemsCount) {
                ((state.layoutInfo.totalItemsCount - 1) * geometry.offsetFraction).roundToInt()
            }
            val text = label(index)
            if (text != null) {
                Surface(
                    shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        // Пузырь шире полосы: меряется без ограничений и встаёт слева от ползунка, напротив его середины.
                        .layout { measurable, _ ->
                            val p = measurable.measure(Constraints())
                            layout(0, 0) { p.placeRelative(-p.width - 6.dp.roundToPx(), thumbTop + thumbPx / 2 - p.height / 2) }
                        }
                        .graphicsLayer {
                            scaleX = bubble
                            scaleY = bubble
                            alpha = bubble.coerceIn(0f, 1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                        },
                ) {
                    Text(text, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                }
            }
        }
    }
}
