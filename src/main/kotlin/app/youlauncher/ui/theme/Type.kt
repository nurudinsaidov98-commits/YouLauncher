package app.youlauncher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.youlauncher.core.settings.FontChoice
import app.youlauncher.resources.Res
import app.youlauncher.resources.google_sans_code
import app.youlauncher.resources.google_sans_flex
import org.jetbrains.compose.resources.Font

private val FlexWeights = listOf(
    FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
)

/**
 * Google Sans Flex (OFL) как вариативный шрифт. Ось ROND (округлость) используется
 * для выразительных (emphasized) стилей M3 Expressive.
 */
@Composable
private fun flexFamily(roundness: Float): FontFamily {
    FontFiles.flex?.let { file ->
        return remember(file, roundness) {
            FontFamily(FlexWeights.map { weight -> fileFont(file, weight, FontVariation.Setting("ROND", roundness)) })
        }
    }
    val fonts = FlexWeights.map { weight ->
        Font(
            resource = Res.font.google_sans_flex,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight.weight),
                FontVariation.Setting("ROND", roundness),
            ),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}

/** Моноширинный Google Sans Code (OFL) для консоли и журналов. */
@Composable
fun monoFamily(): FontFamily {
    val weights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold)
    FontFiles.code?.let { file -> return remember(file) { FontFamily(weights.map { weight -> fileFont(file, weight) }) } }
    val fonts = weights.map { weight ->
        Font(
            resource = Res.font.google_sans_code,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
    return remember(fonts) { FontFamily(fonts) }
}

/** Начертание вариативного шрифта из файла: Skia отображает файл в память один раз на все начертания. */
private fun fileFont(file: java.io.File, weight: FontWeight, vararg extra: FontVariation.Setting) = androidx.compose.ui.text.platform.Font(
    file = file,
    weight = weight,
    style = androidx.compose.ui.text.font.FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), *extra),
)

private fun TextStyle.emphasized(family: FontFamily, weight: FontWeight): TextStyle =
    copy(fontFamily = family, fontWeight = weight)

@Composable
fun youlauncherTypography(choice: FontChoice): Typography {
    val regular = if (choice == FontChoice.GoogleSansFlex) flexFamily(roundness = 0f) else FontFamily.Default
    val rounded = if (choice == FontChoice.GoogleSansFlex) flexFamily(roundness = 100f) else FontFamily.Default
    return remember(regular, rounded) {
        val base = Typography(fontFamily = regular)
        base.copy(
            displayLargeEmphasized = base.displayLarge.emphasized(rounded, FontWeight.SemiBold),
            displayMediumEmphasized = base.displayMedium.emphasized(rounded, FontWeight.SemiBold),
            displaySmallEmphasized = base.displaySmall.emphasized(rounded, FontWeight.SemiBold),
            headlineLargeEmphasized = base.headlineLarge.emphasized(rounded, FontWeight.SemiBold),
            headlineMediumEmphasized = base.headlineMedium.emphasized(rounded, FontWeight.SemiBold),
            headlineSmallEmphasized = base.headlineSmall.emphasized(rounded, FontWeight.SemiBold),
            titleLargeEmphasized = base.titleLarge.emphasized(rounded, FontWeight.Bold),
            titleMediumEmphasized = base.titleMedium.emphasized(rounded, FontWeight.Bold),
            titleSmallEmphasized = base.titleSmall.emphasized(rounded, FontWeight.Bold),
            bodyLargeEmphasized = base.bodyLarge.emphasized(rounded, FontWeight.Medium),
            bodyMediumEmphasized = base.bodyMedium.emphasized(rounded, FontWeight.Medium),
            bodySmallEmphasized = base.bodySmall.emphasized(rounded, FontWeight.Medium),
            labelLargeEmphasized = base.labelLarge.emphasized(rounded, FontWeight.Bold),
            labelMediumEmphasized = base.labelMedium.emphasized(rounded, FontWeight.Bold),
            labelSmallEmphasized = base.labelSmall.emphasized(rounded, FontWeight.Bold),
        )
    }
}

/** Формы M3 Expressive со множителем скругления из настроек (0.5 строже, 1.5 мягче). */
fun youlauncherShapes(scale: Float): Shapes {
    fun r(base: Float) = RoundedCornerShape((base * scale).coerceAtLeast(0f).dp)
    if (scale == 1f) {
        return Shapes(
            extraSmall = ShapeDefaults.ExtraSmall,
            small = ShapeDefaults.Small,
            medium = ShapeDefaults.Medium,
            large = ShapeDefaults.Large,
            extraLarge = ShapeDefaults.ExtraLarge,
            largeIncreased = ShapeDefaults.LargeIncreased,
            extraLargeIncreased = ShapeDefaults.ExtraLargeIncreased,
            extraExtraLarge = ShapeDefaults.ExtraExtraLarge,
        )
    }
    return Shapes(
        extraSmall = r(4f),
        small = r(8f),
        medium = r(12f),
        large = r(16f),
        extraLarge = r(28f),
        largeIncreased = r(20f),
        extraLargeIncreased = r(32f),
        extraExtraLarge = r(48f),
    )
}
