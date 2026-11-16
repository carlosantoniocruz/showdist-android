package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color

/**
 * The fixed app palette (SPEC.md §5): true black screens, white as the neutral for text,
 * numbers and the reticle dot, and ONE accent, cyan, defined once as [Accent] and used
 * everywhere an accent is wanted. Peach red is the capture button's alone. No gradients, no
 * Material baseline colours; every text/background pair clears WCAG AA.
 */
object Palette {
    val Black = Color(0xFF000000)

    /** Dialogs, sheets and menus: a hair off black, so they read as raised. */
    val Surface = Color(0xFF121212)

    /** Filled controls and the selected-row tint on black. */
    val SurfaceHigh = Color(0xFF1C1C1C)

    /** Quiet instructions, a step below [TextMuted]: 5.8:1 on black. */
    val Hint = Color(0xFF8A8A8A)

    /** Primary text: 21:1 on black. */
    val Text = Color(0xFFFFFFFF)

    /** Secondary text: 10.6:1 on black, 9.4:1 on [Surface]. */
    val TextMuted = Color(0xFFB8B8B8)
    val White = Color(0xFFFFFFFF)

    /**
     * The one accent, cyan: the traced measurement line and the reticle's lock, selected
     * states, the confirming button, switches, the Leveler's level cue. 13.7:1 on black;
     * black text on it reads the same. Bright enough to hold up in sunlight.
     */
    val Accent = Color(0xFF00E5FF)

    /** Hairline dividers: white at 16%. */
    val Divider = Color(0x29FFFFFF)

    /** Neutral outlines that should read, but stay quiet (vial, bullseye, buttons). */
    val Line = Color(0x4DFFFFFF)

    /** The capture button in Measure's bottom row, and nothing else: peach red. */
    val Capture = Color(0xFFFB5628)

    /** The live (dashed) line and its label's border: neutral, so the accent stays the only colour. */
    val LiveLine = Color(0xFFFFFFFF)

    /** A saved measurement projected into the view (SPEC.md §2.11): neutral grey, apart from the user's own line. */
    val ProjectionLine = Color(0xFFB8B8B8)

    /** The thin, crisp near-black edge behind the reticle and every mark over the camera; never blurred. */
    val Backing = Color(0xD9000000)

    /** Behind every piece of live text over the camera feed (SPEC.md §3.3). */
    val Scrim = Color(0xB8000000)

    /** The dark edge under the hold-to-save band (SPEC.md §2.6). */
    val Halo = Color(0x99000000)
}
