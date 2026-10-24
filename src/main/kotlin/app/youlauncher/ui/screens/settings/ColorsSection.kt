package app.youlauncher.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.LauncherSettings
import app.youlauncher.resources.*
import app.youlauncher.ui.LocalAppGraph
import app.youlauncher.ui.components.Formats
import app.youlauncher.ui.components.IconLabel
import app.youlauncher.ui.components.SettingLabel
import app.youlauncher.ui.icons.Symbols
import app.youlauncher.ui.theme.ColorGroup
import app.youlauncher.ui.theme.ColorOverrides
import app.youlauncher.ui.theme.ColorRole
import app.youlauncher.ui.theme.SeedColors
import app.youlauncher.ui.theme.toSeedLong
import com.materialkolor.hct.Hct
import org.jetbrains.compose.resources.stringResource

/** Логика выбора цвета без интерфейса (проверяется тестами). */
object ColorPickerLogic {
    fun parseHex(text: String): Color? {
        val t = text.trim().removePrefix("#")
        if (t.length != 6 || t.any { it.digitToIntOrNull(16) == null }) return null
        return Color((0xFF000000 or t.toLong(16)).toInt())
    }

    fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

    /** Контраст по WCAG: (L1 + 0,05) / (L2 + 0,05). */
    fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }
}

/** Свои цвета для каждой роли схемы M3, сгруппированные по назначению. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorsSection(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val graph = LocalAppGraph.current
    val overrides = s.theme.colorOverrides
    var editing by remember { mutableStateOf<ColorRole?>(null) }
    Text(stringResource(Res.string.colors_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (overrides.isNotEmpty()) {
        OutlinedButton(
            onClick = {
                update { it.copy(theme = it.theme.copy(colorOverrides = emptyMap())) }
                graph.messenger.show(Res.string.colors_reset_done)
            },
            shapes = ButtonDefaults.shapes(),
        ) { IconLabel(Symbols.RestartAlt, stringResource(Res.string.colors_reset_all)) }
    }
    ColorGroup.entries.forEach { group ->
        SettingLabel(stringResource(group.title), Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            ColorRole.entries.filter { it.group == group }.forEach { role ->
                RoleRow(role, custom = role.key in overrides, onClick = { editing = role }, modifier = Modifier.weight(1f))
            }
        }
    }
    editing?.let { role ->
        ColorPickerDialog(
            role = role,
            initial = role.get(MaterialTheme.colorScheme),
            custom = role.key in overrides,
            onApply = { c ->
                update { it.copy(theme = it.theme.copy(colorOverrides = it.theme.colorOverrides + (role.key to c.toSeedLong()))) }
                editing = null
            },
            onReset = {
                update { it.copy(theme = it.theme.copy(colorOverrides = it.theme.colorOverrides - role.key)) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RoleRow(role: ColorRole, custom: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val color by animateColorAsState(role.get(MaterialTheme.colorScheme), MaterialTheme.motionScheme.defaultEffectsSpec())
    val label = stringResource(role.label)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Swatch(color, 40.dp)
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (custom) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(stringResource(Res.string.colors_custom), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(stringResource(role.usage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Symbols.Edit, stringResource(Res.string.colors_change), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Swatch(color: Color, size: Dp, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val ring = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(size)
            .clip(if (selected) MaterialTheme.shapes.medium else CircleShape)
            .background(color)
            .border(BorderStroke(if (selected) 3.dp else 1.dp, ring), if (selected) MaterialTheme.shapes.medium else CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Symbols.Check, null, Modifier.size(size * 0.5f), tint = ColorOverrides.contentFor(color))
    }
}

/**
 * Выбор цвета: готовые тональные ряды M3 из seed-цветов, цвета текущей темы, ползунки HCT
 * (оттенок, насыщенность, светлота как в Material Color Utilities) и ввод HEX. Пример с контрастом.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerDialog(role: ColorRole, initial: Color, custom: Boolean, onApply: (Color) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { Hct.fromInt(initial.toArgb()) }
    var hue by remember { mutableFloatStateOf(start.hue.toFloat()) }
    var chroma by remember { mutableFloatStateOf(start.chroma.toFloat()) }
    var tone by remember { mutableFloatStateOf(start.tone.toFloat()) }
    var exact by remember { mutableStateOf<Color?>(initial) }
    val color = exact ?: Color(Hct.from(hue.toDouble(), chroma.toDouble(), tone.toDouble()).toInt())
    var hexText by remember(color) { mutableStateOf(ColorPickerLogic.hex(color)) }
    fun pick(c: Color) {
        val h = Hct.fromInt(c.toArgb())
        hue = h.hue.toFloat(); chroma = h.chroma.toFloat(); tone = h.tone.toFloat()
        exact = c
    }
    val scheme = MaterialTheme.colorScheme
    val themeColors = remember(scheme) {
        listOf(scheme.primary, scheme.primaryContainer, scheme.secondary, scheme.secondaryContainer, scheme.tertiary, scheme.tertiaryContainer,
            scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.onSurface, scheme.outline, scheme.error)
    }
    val animated by animateColorAsState(color, MaterialTheme.motionScheme.defaultEffectsSpec())
    val text = ColorOverrides.contentFor(color)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Symbols.Palette, null) },
        title = { Text(stringResource(Res.string.colors_dialog_title, stringResource(role.label))) },
        text = {
            Column(Modifier.widthIn(min = 460.dp, max = 560.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = animated, modifier = Modifier.fillMaxWidth().height(72.dp)) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.colors_preview_text), style = MaterialTheme.typography.titleMediumEmphasized, color = text, modifier = Modifier.weight(1f))
                        Text(stringResource(Res.string.colors_contrast, Formats.decimal(ColorPickerLogic.contrast(color, text), 1)), style = MaterialTheme.typography.labelLarge, color = text)
                    }
                }
                SettingLabel(stringResource(Res.string.colors_from_theme))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    themeColors.forEach { c -> Swatch(c, 30.dp, selected = exact == c) { pick(c) } }
                }
                SettingLabel(stringResource(Res.string.colors_presets))
                SeedColors.forEach { seed ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(seed.name), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ColorOverrides.tonalRow(seed.color).forEach { c -> Swatch(c, 30.dp, selected = exact == c) { pick(c) } }
                    }
                }
                HctSlider(stringResource(Res.string.colors_hue), hue, 0f..360f) { hue = it; exact = null }
                HctSlider(stringResource(Res.string.colors_chroma), chroma, 0f..120f) { chroma = it; exact = null }
                HctSlider(stringResource(Res.string.colors_tone), tone, 0f..100f) { tone = it; exact = null }
                val parsed = ColorPickerLogic.parseHex(hexText)
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { v -> hexText = v.take(7); ColorPickerLogic.parseHex(v)?.let(::pick) },
                    label = { Text(stringResource(Res.string.colors_hex)) },
                    isError = parsed == null,
                    supportingText = if (parsed == null) ({ Text(stringResource(Res.string.colors_hex_invalid)) }) else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.width(200.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(color) }, shapes = ButtonDefaults.shapes()) { IconLabel(Symbols.Check, stringResource(Res.string.colors_apply)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (custom) TextButton(onClick = onReset, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.colors_reset)) }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun HctSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}
