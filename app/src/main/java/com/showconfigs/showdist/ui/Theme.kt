package com.showconfigs.showdist.ui

import androidx.compose.material3.MaterialTheme
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
val BaseText = TextStyle(fontFamily = JetBrainsMono, fontFeatureSettings = FEATURES, color = Palette.White)

/** Caption style (SPEC.md §6.3). App-bar titles override the colour to white. */
val CaptionText = BaseText.copy(
    color = Palette.WarmGrey,
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 3.sp,
    textAlign = TextAlign.Center,
)

/**
 * One fixed dark scheme (SPEC.md §0, §5): it ignores the system light/dark setting.
 * peach is the primary; screens and app bars are ink, and dialogs, sheets and menus are
 * inkSurface, with no tint. Every role is set so no Material baseline colour can appear.
 */
private val ShowdistColors = darkColorScheme(
    primary = Palette.Peach,
    onPrimary = Palette.White,
    primaryContainer = Palette.InkSurface,
    onPrimaryContainer = Palette.White,
    inversePrimary = Palette.Peach,
    secondary = Palette.OlympicBlue,
    onSecondary = Palette.White,
    secondaryContainer = Palette.InkSurface,
    onSecondaryContainer = Palette.White,
    tertiary = Palette.SeafoamMuted,
    onTertiary = Palette.White,
    tertiaryContainer = Palette.InkSurface,
    onTertiaryContainer = Palette.White,
    background = Palette.Ink,
    onBackground = Palette.White,
    surface = Palette.Ink,
    onSurface = Palette.White,
    surfaceVariant = Palette.InkSurface,
    onSurfaceVariant = Palette.WarmGrey,
    surfaceTint = Color.Transparent,
    inverseSurface = Palette.White,
    inverseOnSurface = Palette.Ink,
    surfaceBright = Palette.InkSurface,
    surfaceDim = Palette.Ink,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.InkSurface,
    surfaceContainer = Palette.InkSurface,
    surfaceContainerHigh = Palette.InkSurface,
    surfaceContainerHighest = Palette.InkSurface,
    outline = Palette.WarmGrey,
    outlineVariant = Palette.White.copy(alpha = 0.08f),
    scrim = Palette.Scrim,
    error = Palette.Peach,
    onError = Palette.White,
    errorContainer = Palette.InkSurface,
    onErrorContainer = Palette.White,
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
    MaterialTheme(colorScheme = ShowdistColors, typography = ShowdistTypography, content = content)
}
