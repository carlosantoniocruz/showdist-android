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
import androidx.compose.ui.unit.em
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

/** How much larger than Material's defaults the type scale is set. */
private const val TYPE_SCALE = 1.2f

/** Slashed zero plus tabular figures, for live numbers. */
const val FEATURES_TABULAR = "'zero', 'tnum'"

/** Base for text drawn with BasicText outside the Material type scale. */
val BaseText = TextStyle(fontFamily = JetBrainsMono, fontFeatureSettings = FEATURES, color = Palette.Text)

/**
 * The app's named text styles (SPEC.md §6.3), about 20% larger than Material's defaults
 * so everything reads at arm's length. Screens use these rather than raw sizes.
 */
object Type {
    /** SHOWDIST on the sun. */
    val Wordmark = BaseText.copy(color = Palette.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)

    /** Section and app-bar captions: spaced capitals in grey. App bars override to white. */
    val Caption = BaseText.copy(
        color = Palette.TextMuted,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 3.sp,
        textAlign = TextAlign.Center,
    )

    /** Headings: a tool's name, a license's name, a dialog title. */
    val Title = BaseText.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 1.3.em)

    /** List-row titles and button labels. */
    val Label = BaseText.copy(fontSize = 17.sp)

    /** Running text. */
    val Body = BaseText.copy(fontSize = 16.sp, lineHeight = 1.5.em)

    /** Secondary and informational text, in grey. */
    val Secondary = BaseText.copy(color = Palette.TextMuted, fontSize = 15.sp, lineHeight = 1.45.em)

    /** A saved measurement's total in a list. */
    val Value = BaseText.copy(fontSize = 22.sp, fontWeight = FontWeight.Light, fontFeatureSettings = FEATURES_TABULAR)

    /** A measurement's total on its sheet. */
    val ValueLarge = BaseText.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = FEATURES_TABULAR)

    /** Leveler readouts, readable from a foot away; the bullseye's pair is set smaller. */
    val Readout = BaseText.copy(fontSize = 96.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = FEATURES_TABULAR)
}

/**
 * One fixed scheme (SPEC.md §0, §5): black screens, near-black raised surfaces, accent
 * as the only accent, with no tint. Every role is set so no Material baseline colour can
 * appear. Errors use accent too, beside an icon.
 */
private val ShowdistColors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Black,
    primaryContainer = Palette.SurfaceHigh,
    onPrimaryContainer = Palette.Text,
    inversePrimary = Palette.Accent,
    secondary = Palette.Accent,
    onSecondary = Palette.Black,
    secondaryContainer = Palette.SurfaceHigh,
    onSecondaryContainer = Palette.Text,
    tertiary = Palette.Accent,
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
    outlineVariant = Palette.Divider,
    scrim = Palette.Scrim,
    error = Palette.Accent,
    onError = Palette.Black,
    errorContainer = Palette.SurfaceHigh,
    onErrorContainer = Palette.Text,
)

/** Minimal rounding (SPEC.md §5): just enough to soften a corner, on menus, sheets and dialogs alike. */
val CornerSmall = RoundedCornerShape(2.dp)
val Corner = RoundedCornerShape(4.dp)
private val ShowdistShapes = Shapes(
    extraSmall = CornerSmall,
    small = CornerSmall,
    medium = Corner,
    large = Corner,
    extraLarge = Corner,
)

/**
 * Material 3's type scale, 20% larger, set in JetBrains Mono with slashed zero (SPEC.md
 * §6.1), so dialogs, menus, snackbars and tooltips match the app's own styles.
 */
private val ShowdistTypography = Typography().run {
    fun TextStyle.mono() = copy(
        fontFamily = JetBrainsMono,
        fontFeatureSettings = FEATURES,
        fontSize = fontSize * TYPE_SCALE,
        lineHeight = lineHeight * TYPE_SCALE,
    )
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
