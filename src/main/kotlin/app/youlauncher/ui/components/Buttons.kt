package app.youlauncher.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Подсказка M3 над любым элементом: на десктопе важна для кнопок-иконок. */
@Composable
fun WithTooltip(
    text: String,
    position: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** Кнопка-иконка M3 Expressive: морфинг формы при нажатии, подсказка, описание для доступности. */
@Composable
fun TIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    tooltip: Boolean = true,
    tooltipPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
    interactionSource: MutableInteractionSource? = null,
) {
    val button: @Composable () -> Unit = {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        ) { Icon(icon, contentDescription = contentDescription) }
    }
    if (tooltip) WithTooltip(contentDescription, tooltipPosition, button) else button()
}

@Composable
fun TTonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    WithTooltip(contentDescription) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier,
            enabled = enabled,
        ) { Icon(icon, contentDescription = contentDescription) }
    }
}

/** Содержимое кнопки: иконка и подпись с отступом по спецификации M3. */
@Composable
fun RowScope.IconLabel(icon: ImageVector, label: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(label)
}
