package com.showconfigs.showdist.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.R

/**
 * JetBrains Mono, the only typeface (SPEC.md §6.1). Only 400 and 700 are bundled; other
 * weights the spec asks for (200, 300, 500, 600, 800) resolve to the nearest of those.
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

/** Slashed zero on all text (SPEC.md §6.1). */
const val FEATURES = "'zero'"

/** Slashed zero plus tabular figures, for live numbers. */
const val FEATURES_TABULAR = "'zero', 'tnum'"

/** Base for text drawn with BasicText outside the Material type scale. */
val BaseText = TextStyle(fontFamily = JetBrainsMono, fontFeatureSettings = FEATURES, color = Palette.Text)

/** Caption style (SPEC.md §6.3). App-bar titles override the colour to near-white. */
val CaptionText = BaseText.copy(
    color = Palette.TextMuted,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 3.sp,
    textAlign = TextAlign.Center,
)

/**
 * One fixed scheme (SPEC.md §0, §5): true black screens, near-black raised surfaces, green
 * primary, with no tint. Every role is set so no Material baseline colour can appear.
 */
private val ShowdistColors = darkColorScheme(
    primary = Palette.Green,
    onPrimary = Palette.Black,
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.Text,
    inversePrimary = Palette.Green,
    secondary = Palette.Violet,
    onSecondary = Palette.Black,
    secondaryContainer = Palette.SurfaceHigh,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Pink,
    onTertiary = Palette.Black,
    tertiaryContainer = Palette.SurfaceHigh,
    onTertiaryContainer = Palette.Text,
    background = Palette.Black,
    onBackground = Palette.Text,
    surface = Palette.Black,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceTint = Color.Transparent,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Black,
    surfaceBright = Palette.SurfaceHigh,
    surfaceDim = Palette.Black,
    surfaceContainerLowest = Palette.Black,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Line,
    outlineVariant = Palette.Grid,
    scrim = Palette.Scrim,
    error = Palette.Pink,
    onError = Palette.Black,
    errorContainer = Palette.SurfaceHigh,
    onErrorContainer = Palette.Text,
)

/** Material 3 Expressive's rounder corners: pills for small parts, 28 dp for sheets and dialogs. */
private val ShowdistShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Material 3's type scale, set in JetBrains Mono with slashed zero (SPEC.md §6.1). */
private val ShowdistTypography = Typography().run {
    fun TextStyle.mono() = copy(fontFamily = JetBrainsMono, fontFeatureSettings = FEATURES)
    Typography(
        displayLarge = displayLarge.mono(),
        displayMedium = displayMedium.mono(),
        displaySmall = displaySmall.mono(),
        headlineLarge = headlineLarge.mono(),
        headlineMedium = headlineMedium.mono(),
        headlineSmall = headlineSmall.mono(),
        titleLarge = titleLarge.mono(),
        titleMedium = titleMedium.mono(),
        titleSmall = titleSmall.mono(),
        bodyLarge = bodyLarge.mono(),
        bodyMedium = bodyMedium.mono(),
        bodySmall = bodySmall.mono(),
        labelLarge = labelLarge.mono(),
        labelMedium = labelMedium.mono(),
        labelSmall = labelSmall.mono(),
    )
}

@Composable
fun ShowdistTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ShowdistColors,
        typography = ShowdistTypography,
        shapes = ShowdistShapes,
        content = content,
    )
}
