package app.youlauncher.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
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
import app.youlauncher.core.settings.ThemeSettings
import app.youlauncher.resources.*
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.animatedMorphShape
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.ColorOverrides
import app.youlauncher.ui.theme.SeedColors
import app.youlauncher.ui.theme.seedColor
import app.youlauncher.ui.theme.toSeedLong
import org.jetbrains.compose.resources.stringResource

/** Цвет в HSV: так удобнее палитре «квадрат насыщенности и яркости + полоса оттенка». */
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color = Color(java.awt.Color.HSBtoRGB(hue / 360f, saturation, value) or (0xFF shl 24))

    companion object {
        fun of(color: Color): Hsv {
            val argb = color.toArgb()
            val hsb = java.awt.Color.RGBtoHSB((argb shr 16) and 255, (argb shr 8) and 255, argb and 255, null)
            return Hsv(hsb[0] * 360f, hsb[1], hsb[2])
        }

        /** Второй цвет по умолчанию: оттенок на треть круга дальше, чтобы переливание было заметным. */
        fun companion(color: Color): Color = of(color).let { it.copy(hue = (it.hue + 120f) % 360f) }.toColor()
    }
}

/**
 * Последняя кнопка в ряду акцентов: свой цвет из палитры и переливание с другим цветом. Пока свой цвет
 * не выбран, это кнопка-мольберт; после выбора кнопка окрашивается в него (или в оба цвета переливания).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomAccentButton(theme: ThemeSettings, onApply: (seed: Long, shimmer: Long?) -> Unit) {
    val seed = theme.seedColor()
    val custom = theme.seed != null && SeedColors.none { it.color.toSeedLong() == theme.seed } || theme.shimmer != null
    var open by remember { mutableStateOf(false) }
    var grow by remember { mutableStateOf(TransformOrigin(0.08f, 0f)) }
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = open
    val shape = animatedMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, custom, rotation = 30f)
    val name = stringResource(Res.string.accent_custom)
    val fill = when {
        theme.shimmer != null -> Brush.sweepGradient(listOf(seed, Color(theme.shimmer.toInt()).copy(alpha = 1f), seed))
        custom -> Brush.linearGradient(listOf(seed, seed))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHighest))
    }
    Box {
        Box(
            Modifier
                .size(52.dp)
                .clip(shape)
                .background(fill)
                .then(if (custom) Modifier else Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape))
                .selectable(selected = custom, role = Role.RadioButton, onClick = { open = !open })
                .semantics { contentDescription = name },
            contentAlignment = Alignment.Center,
        ) {
            if (custom) {
                Surface(shape = MaterialShapes.Circle.toShape(), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(24.dp)) {
                    Icon(Symbols.PaletteFilled, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(4.dp))
                }
            } else {
                Icon(Symbols.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
        }
        if (visible.currentState || visible.targetState) {
            Popup(
                popupPositionProvider = remember { BesideAnchor(gap = 12) { if (it != grow) grow = it } },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                // Окошко вырастает из кнопки: точка роста — левый верхний угол, где кнопка.
                AnimatedVisibility(
                    visibleState = visible,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec(), initialScale = 0.6f, transformOrigin = grow) +
                        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.8f, transformOrigin = grow) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    AccentPickerCard(
                        initial = seed,
                        initialShimmer = theme.shimmer?.let { Color(it.toInt()).copy(alpha = 1f) },
                        onApply = { a, b -> onApply(a.toSeedLong(), b?.toSeedLong()); open = false },
                    )
                }
            }
        }
    }
}

/**
 * Окошко под кнопкой, если не помещается — над ней, а в невысоком окне — сбоку, чтобы не закрывать кнопку.
 * [origin] получает точку, из которой окошко вырастает: сторону, где осталась кнопка.
 */
private class BesideAnchor(private val gap: Int, private val origin: (TransformOrigin) -> Unit) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        fun clampX(x: Int) = x.coerceAtMost(windowSize.width - w - gap).coerceAtLeast(gap)
        fun clampY(y: Int) = y.coerceAtMost(windowSize.height - h - gap).coerceAtLeast(gap)
        fun fx(x: Int) = ((anchorBounds.center.x - x).toFloat() / w).coerceIn(0f, 1f)
        fun fy(y: Int) = ((anchorBounds.center.y - y).toFloat() / h).coerceIn(0f, 1f)
        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - gap - h
        val (x, y) = when {
            below + h <= windowSize.height -> clampX(anchorBounds.left) to below
            above >= gap -> clampX(anchorBounds.left) to above
            anchorBounds.right + gap + w <= windowSize.width -> anchorBounds.right + gap to clampY(anchorBounds.center.y - h / 3)
            else -> (anchorBounds.left - gap - w).coerceAtLeast(gap) to clampY(anchorBounds.center.y - h / 3)
        }
        origin(TransformOrigin(fx(x), fy(y)))
        return IntOffset(x, y)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccentPickerCard(initial: Color, initialShimmer: Color?, onApply: (Color, Color?) -> Unit) {
    var first by remember { mutableStateOf(Hsv.of(initial)) }
    var second by remember { mutableStateOf(Hsv.of(initialShimmer ?: Hsv.companion(initial))) }
    var shimmer by remember { mutableStateOf(initialShimmer != null) }
    var editing by remember { mutableStateOf(0) }
    val current = if (editing == 0 || !shimmer) first else second
    fun set(hsv: Hsv) { if (editing == 0 || !shimmer) first = hsv else second = hsv }
    val a by animateColorAsState(first.toColor(), MaterialTheme.motionScheme.fastEffectsSpec())
    val b by animateColorAsState(second.toColor(), MaterialTheme.motionScheme.fastEffectsSpec())

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = Modifier.width(340.dp).shadow(16.dp, MaterialTheme.shapes.extraLarge),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Предпросмотр: один цвет или оба цвета переливания.
                Box(
                    Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(if (shimmer) Brush.sweepGradient(listOf(a, b, a)) else Brush.linearGradient(listOf(a, a))),
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.accent_custom), style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(ColorPickerLogic.hex(current.toColor()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(shimmer, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                    listOf(Res.string.accent_color_first to a, Res.string.accent_color_second to b).forEachIndexed { i, (label, color) ->
                        ToggleButton(
                            checked = editing == i,
                            onCheckedChange = { editing = i },
                            shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(color))
                            Text(stringResource(label), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            SaturationValueField(current, onChange = ::set)
            HueBar(current.hue) { set(current.copy(hue = it)) }
            // Код в поле не перезаписывается, пока задаёт тот же цвет: иначе при вводе прыгал бы курсор, а пересчёт
            // через HSV мог бы подменить введённый код соседним. Ползунки и «Цвет 1 / 2» меняют его как обычно.
            var hexText by remember { mutableStateOf(ColorPickerLogic.hex(current.toColor())) }
            androidx.compose.runtime.LaunchedEffect(current) { if (ColorPickerLogic.parseHex(hexText)?.let { Hsv.of(it) } != current) hexText = ColorPickerLogic.hex(current.toColor()) }
            OutlinedTextField(
                value = hexText,
                onValueChange = { v -> hexText = v.take(7); ColorPickerLogic.parseHex(v)?.let { set(Hsv.of(it)) } },
                label = { Text(stringResource(Res.string.colors_hex)) },
                isError = ColorPickerLogic.parseHex(hexText) == null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(Res.string.accent_shimmer), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(Res.string.accent_shimmer_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = shimmer, onCheckedChange = {
                    // Второй цвет по умолчанию — от выбранного сейчас, а не от исходного.
                    if (it && initialShimmer == null) second = Hsv.of(Hsv.companion(first.toColor()))
                    shimmer = it; editing = if (it) 1 else 0
                })
            }
            Button(
                onClick = { onApply(first.toColor(), if (shimmer) second.toColor() else null) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) { IconLabel(Symbols.Check, stringResource(Res.string.colors_apply)) }
        }
    }
}

/** Квадрат: по горизонтали насыщенность, по вертикали яркость; оттенок задаёт полоса под ним. */
@Composable
private fun SaturationValueField(hsv: Hsv, onChange: (Hsv) -> Unit) {
    val pure = Hsv(hsv.hue, 1f, 1f).toColor()
    val latest by androidx.compose.runtime.rememberUpdatedState(hsv)
    fun pick(p: Offset, w: Float, h: Float) =
        onChange(latest.copy(saturation = (p.x / w).coerceIn(0f, 1f), value = 1f - (p.y / h).coerceIn(0f, 1f)))
    val ring = ColorOverrides.contentFor(hsv.toColor())
    Canvas(
        Modifier.fillMaxWidth().height(170.dp).clip(MaterialTheme.shapes.large)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { pick(it, size.width.toFloat(), size.height.toFloat()) }) { c, _ -> pick(c.position, size.width.toFloat(), size.height.toFloat()) } },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Hsv(hsv.hue, 0f, 1f).toColor(), pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Hsv(0f, 0f, 0f).toColor())))
        val c = Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height)
        drawCircle(hsv.toColor(), 11.dp.toPx(), c)
        drawCircle(ring, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
    }
}

/** Полоса оттенка по всему кругу с ручкой-кружком. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val hues = remember { (0..12).map { Hsv(it * 30f, 1f, 1f).toColor() } }
    fun pick(x: Float, w: Float) = onChange((x / w).coerceIn(0f, 1f) * 359.9f)
    Canvas(
        Modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures(onDragStart = { pick(it.x, size.width.toFloat()) }) { c, _ -> pick(c.position.x, size.width.toFloat()) } },
    ) {
        val r = size.height / 2
        drawRoundRect(Brush.horizontalGradient(hues), topLeft = Offset(0f, r / 2), size = androidx.compose.ui.geometry.Size(size.width, r), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r / 2))
        val c = Offset((hue / 360f) * size.width, r)
        drawCircle(Hsv(hue, 1f, 1f).toColor(), r - 2.dp.toPx(), c)
        drawCircle(Hsv(0f, 0f, 1f).toColor(), r - 2.dp.toPx(), c, style = Stroke(3.dp.toPx()))
    }
}
