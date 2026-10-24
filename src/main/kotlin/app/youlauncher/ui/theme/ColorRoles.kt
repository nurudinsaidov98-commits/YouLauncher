package app.youlauncher.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.youlauncher.resources.*
import com.materialkolor.hct.Hct
import org.jetbrains.compose.resources.StringResource

enum class ColorGroup(val title: StringResource) {
    Primary(Res.string.color_group_primary),
    Secondary(Res.string.color_group_secondary),
    Tertiary(Res.string.color_group_tertiary),
    Surfaces(Res.string.color_group_surfaces),
    Text(Res.string.color_group_text),
    Errors(Res.string.color_group_errors),
}

/**
 * Роль цвета M3: каждый элемент интерфейса берёт цвет из одной из ролей, поэтому свой цвет роли
 * меняет все элементы, которые её используют. [on] это роль текста и значков поверх этой роли.
 */
enum class ColorRole(
    val key: String,
    val group: ColorGroup,
    val label: StringResource,
    val usage: StringResource,
    val get: (ColorScheme) -> Color,
    val on: String? = null,
) {
    Primary("primary", ColorGroup.Primary, Res.string.role_primary, Res.string.use_primary, { it.primary }, "onPrimary"),
    OnPrimary("onPrimary", ColorGroup.Primary, Res.string.role_on_primary, Res.string.use_on_primary, { it.onPrimary }),
    PrimaryContainer("primaryContainer", ColorGroup.Primary, Res.string.role_primary_container, Res.string.use_primary_container, { it.primaryContainer }, "onPrimaryContainer"),
    OnPrimaryContainer("onPrimaryContainer", ColorGroup.Primary, Res.string.role_on_primary_container, Res.string.use_on_primary_container, { it.onPrimaryContainer }),
    InversePrimary("inversePrimary", ColorGroup.Primary, Res.string.role_inverse_primary, Res.string.use_inverse_primary, { it.inversePrimary }),
    Secondary("secondary", ColorGroup.Secondary, Res.string.role_secondary, Res.string.use_secondary, { it.secondary }, "onSecondary"),
    OnSecondary("onSecondary", ColorGroup.Secondary, Res.string.role_on_secondary, Res.string.use_on_secondary, { it.onSecondary }),
    SecondaryContainer("secondaryContainer", ColorGroup.Secondary, Res.string.role_secondary_container, Res.string.use_secondary_container, { it.secondaryContainer }, "onSecondaryContainer"),
    OnSecondaryContainer("onSecondaryContainer", ColorGroup.Secondary, Res.string.role_on_secondary_container, Res.string.use_on_secondary_container, { it.onSecondaryContainer }),
    Tertiary("tertiary", ColorGroup.Tertiary, Res.string.role_tertiary, Res.string.use_tertiary, { it.tertiary }, "onTertiary"),
    OnTertiary("onTertiary", ColorGroup.Tertiary, Res.string.role_on_tertiary, Res.string.use_on_tertiary, { it.onTertiary }),
    TertiaryContainer("tertiaryContainer", ColorGroup.Tertiary, Res.string.role_tertiary_container, Res.string.use_tertiary_container, { it.tertiaryContainer }, "onTertiaryContainer"),
    OnTertiaryContainer("onTertiaryContainer", ColorGroup.Tertiary, Res.string.role_on_tertiary_container, Res.string.use_on_tertiary_container, { it.onTertiaryContainer }),
    Background("background", ColorGroup.Surfaces, Res.string.role_background, Res.string.use_background, { it.background }, "onBackground"),
    Surface("surface", ColorGroup.Surfaces, Res.string.role_surface, Res.string.use_surface, { it.surface }, "onSurface"),
    SurfaceDim("surfaceDim", ColorGroup.Surfaces, Res.string.role_surface_dim, Res.string.use_surface_dim, { it.surfaceDim }),
    SurfaceBright("surfaceBright", ColorGroup.Surfaces, Res.string.role_surface_bright, Res.string.use_surface_bright, { it.surfaceBright }),
    SurfaceContainerLowest("surfaceContainerLowest", ColorGroup.Surfaces, Res.string.role_surface_container_lowest, Res.string.use_surface_container_lowest, { it.surfaceContainerLowest }),
    SurfaceContainerLow("surfaceContainerLow", ColorGroup.Surfaces, Res.string.role_surface_container_low, Res.string.use_surface_container_low, { it.surfaceContainerLow }),
    SurfaceContainer("surfaceContainer", ColorGroup.Surfaces, Res.string.role_surface_container, Res.string.use_surface_container, { it.surfaceContainer }),
    SurfaceContainerHigh("surfaceContainerHigh", ColorGroup.Surfaces, Res.string.role_surface_container_high, Res.string.use_surface_container_high, { it.surfaceContainerHigh }),
    SurfaceContainerHighest("surfaceContainerHighest", ColorGroup.Surfaces, Res.string.role_surface_container_highest, Res.string.use_surface_container_highest, { it.surfaceContainerHighest }),
    SurfaceVariant("surfaceVariant", ColorGroup.Surfaces, Res.string.role_surface_variant, Res.string.use_surface_variant, { it.surfaceVariant }, "onSurfaceVariant"),
    InverseSurface("inverseSurface", ColorGroup.Surfaces, Res.string.role_inverse_surface, Res.string.use_inverse_surface, { it.inverseSurface }, "inverseOnSurface"),
    OnSurface("onSurface", ColorGroup.Text, Res.string.role_on_surface, Res.string.use_on_surface, { it.onSurface }),
    OnSurfaceVariant("onSurfaceVariant", ColorGroup.Text, Res.string.role_on_surface_variant, Res.string.use_on_surface_variant, { it.onSurfaceVariant }),
    OnBackground("onBackground", ColorGroup.Text, Res.string.role_on_background, Res.string.use_on_background, { it.onBackground }),
    InverseOnSurface("inverseOnSurface", ColorGroup.Text, Res.string.role_inverse_on_surface, Res.string.use_inverse_on_surface, { it.inverseOnSurface }),
    Outline("outline", ColorGroup.Text, Res.string.role_outline, Res.string.use_outline, { it.outline }),
    OutlineVariant("outlineVariant", ColorGroup.Text, Res.string.role_outline_variant, Res.string.use_outline_variant, { it.outlineVariant }),
    Scrim("scrim", ColorGroup.Text, Res.string.role_scrim, Res.string.use_scrim, { it.scrim }),
    Error("error", ColorGroup.Errors, Res.string.role_error, Res.string.use_error, { it.error }, "onError"),
    OnError("onError", ColorGroup.Errors, Res.string.role_on_error, Res.string.use_on_error, { it.onError }),
    ErrorContainer("errorContainer", ColorGroup.Errors, Res.string.role_error_container, Res.string.use_error_container, { it.errorContainer }, "onErrorContainer"),
    OnErrorContainer("onErrorContainer", ColorGroup.Errors, Res.string.role_on_error_container, Res.string.use_on_error_container, { it.onErrorContainer }),
    ;

    companion object {
        fun byKey(key: String): ColorRole? = entries.firstOrNull { it.key == key }
    }
}

object ColorOverrides {
    /**
     * Цвет текста для фона: тот же оттенок, светлота 10 на светлом фоне и 98 на тёмном,
     * чтобы контраст держался выше рекомендованного M3 (4,5:1) при любом выбранном цвете.
     */
    fun contentFor(background: Color): Color {
        val hct = Hct.fromInt(background.toArgb())
        val tone = if (hct.tone >= 60.0) 10.0 else 98.0
        return Color(Hct.from(hct.hue, minOf(hct.chroma, 16.0), tone).toInt())
    }

    /** Схема со своими цветами: заданные роли заменяются, для «on»-ролей без своего цвета подбирается контрастный. */
    fun apply(scheme: ColorScheme, overrides: Map<String, Long>): ColorScheme {
        if (overrides.isEmpty()) return scheme
        val colors = HashMap<String, Color>()
        overrides.forEach { (k, v) -> if (ColorRole.byKey(k) != null) colors[k] = Color(v.toInt()) }
        ColorRole.entries.forEach { r ->
            val onKey = r.on ?: return@forEach
            val bg = colors[r.key] ?: return@forEach
            if (onKey !in overrides) colors[onKey] = contentFor(bg)
        }
        fun c(key: String, default: Color) = colors[key] ?: default
        return scheme.copy(
            primary = c("primary", scheme.primary),
            onPrimary = c("onPrimary", scheme.onPrimary),
            primaryContainer = c("primaryContainer", scheme.primaryContainer),
            onPrimaryContainer = c("onPrimaryContainer", scheme.onPrimaryContainer),
            inversePrimary = c("inversePrimary", scheme.inversePrimary),
            secondary = c("secondary", scheme.secondary),
            onSecondary = c("onSecondary", scheme.onSecondary),
            secondaryContainer = c("secondaryContainer", scheme.secondaryContainer),
            onSecondaryContainer = c("onSecondaryContainer", scheme.onSecondaryContainer),
            tertiary = c("tertiary", scheme.tertiary),
            onTertiary = c("onTertiary", scheme.onTertiary),
            tertiaryContainer = c("tertiaryContainer", scheme.tertiaryContainer),
            onTertiaryContainer = c("onTertiaryContainer", scheme.onTertiaryContainer),
            background = c("background", scheme.background),
            onBackground = c("onBackground", scheme.onBackground),
            surface = c("surface", scheme.surface),
            onSurface = c("onSurface", scheme.onSurface),
            surfaceVariant = c("surfaceVariant", scheme.surfaceVariant),
            onSurfaceVariant = c("onSurfaceVariant", scheme.onSurfaceVariant),
            surfaceTint = c("primary", scheme.surfaceTint),
            inverseSurface = c("inverseSurface", scheme.inverseSurface),
            inverseOnSurface = c("inverseOnSurface", scheme.inverseOnSurface),
            error = c("error", scheme.error),
            onError = c("onError", scheme.onError),
            errorContainer = c("errorContainer", scheme.errorContainer),
            onErrorContainer = c("onErrorContainer", scheme.onErrorContainer),
            outline = c("outline", scheme.outline),
            outlineVariant = c("outlineVariant", scheme.outlineVariant),
            scrim = c("scrim", scheme.scrim),
            surfaceBright = c("surfaceBright", scheme.surfaceBright),
            surfaceDim = c("surfaceDim", scheme.surfaceDim),
            surfaceContainer = c("surfaceContainer", scheme.surfaceContainer),
            surfaceContainerHigh = c("surfaceContainerHigh", scheme.surfaceContainerHigh),
            surfaceContainerHighest = c("surfaceContainerHighest", scheme.surfaceContainerHighest),
            surfaceContainerLow = c("surfaceContainerLow", scheme.surfaceContainerLow),
            surfaceContainerLowest = c("surfaceContainerLowest", scheme.surfaceContainerLowest),
        )
    }

    /** Готовые цвета в духе M3: тональные ряды каждого seed-цвета из Theme.kt (светлота 20–90). */
    val tones = listOf(20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0, 90.0)

    fun tonalRow(seed: Color): List<Color> {
        val hct = Hct.fromInt(seed.toArgb())
        return tones.map { Color(Hct.from(hct.hue, maxOf(hct.chroma, 36.0), it).toInt()) }
    }
}
