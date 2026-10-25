package app.youlauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Панель вкладок-таблеток M3 Expressive: вкладки лежат в контейнере-таблетке, выбранную подсвечивает
 * пилюля, которая переезжает между вкладками и меняет ширину на пространственной пружине темы.
 * Если вкладки не помещаются, панель прокручивается и сама показывает выбранную.
 */
@Composable
fun <T> PillTabs(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    icon: (T) -> ImageVector,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    // Положение и ширина каждой вкладки внутри ряда: по ним едет индикатор.
    val bounds = remember { mutableStateMapOf<Int, Pair<Dp, Dp>>() }
    val index = items.indexOf(selected).coerceAtLeast(0)
    val target = bounds[index]
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val x by animateDpAsState(target?.first ?: 0.dp, spatial, label = "pillX")
    val w by animateDpAsState(target?.second ?: 0.dp, spatial, label = "pillW")
    LaunchedEffect(index, target) {
        // Выбранная вкладка у края: панель доезжает, чтобы её было видно целиком.
        target ?: return@LaunchedEffect
        val left = with(density) { target.first.roundToPx() }
        val right = left + with(density) { target.second.roundToPx() }
        if (left < scroll.value) scroll.animateScrollTo(left - with(density) { 16.dp.roundToPx() })
        else if (right > scroll.value + scroll.viewportSize) scroll.animateScrollTo(right - scroll.viewportSize + with(density) { 16.dp.roundToPx() })
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.height(56.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(50)).horizontalScroll(scroll).padding(5.dp)) {
            if (target != null) {
                Box(
                    Modifier
                        .offset(x = x)
                        .width(w)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)),
                )
            }
            Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    PillTab(
                        label = label(item),
                        icon = icon(item),
                        selected = i == index,
                        onClick = { onSelect(item) },
                        modifier = Modifier.onPlaced { c ->
                            bounds[i] = with(density) { c.positionInParent().x.toDp() to c.size.width.toDp() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillTab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        effects, label = "pillContent",
    )
    val hover by animateColorAsState(
        if (hovered && !selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent,
        effects, label = "pillHover",
    )
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(hover)
            .hoverable(interaction)
            .selectable(selected, interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}
