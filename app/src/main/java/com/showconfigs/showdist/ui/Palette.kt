package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color

/**
 * Fixed app palette (SPEC.md §5): black screens, white and light-grey text, and one accent,
 * sandstone. Every text/background pair clears WCAG AA. Over the camera there is one accent
 * too, cyan; the unit system is shown only in words.
 */
object Palette {
    val Black = Color(0xFF000000)

    /** Dialogs, sheets and menus: a hair off black, so they read as raised. */
    val Surface = Color(0xFF121212)

    /** Filled controls and the selected-row tint on black, slightly warm. */
    val SurfaceHigh = Color(0xFF1E1C19)

    /** Quiet instructions, a step below [TextMuted]: 5.8:1 on black. */
    val Hint = Color(0xFF8A8A8A)

    /** Primary text: 21:1 on black. */
    val Text = Color(0xFFFFFFFF)

    /** Secondary text: 10.6:1 on black, 9.4:1 on [Surface]. */
    val TextMuted = Color(0xFFB8B8B8)
    val White = Color(0xFFFFFFFF)

    /** The accent: selected states, highlights, icons. 9.4:1 on black; black text on it is 9.4:1 too. */
    val Sandstone = Color(0xFFC8A97E)

    /** Dividers and quiet sandstone rules. */
    val Divider = Color(0x59C8A97E)

    /** Neutral outlines that should read, but stay quiet (vial, bullseye, buttons). */
    val Line = Color(0x4DFFFFFF)

    /** The Leveler's bubble once level, and nothing else: 11.8:1 on black. */
    val Level = Color(0xFF3DDC84)

    /** The tracking pill's dot while tracking is lost or too dark: soft coral, apart from [Crimson]. */
    val Attention = Color(0xFFFF8A65)

    /** The capture button in Measure's bottom row, and nothing else: peach red. */
    val Capture = Color(0xFFFB5628)

    /**
     * The one accent over the camera (SPEC.md §3): the traced measurement line and the
     * reticle's locked ring, whatever the unit system.
     */
    val Cyan = Color(0xFF00E5FF)

    /** The live (dashed) line and its label's border: neutral, so cyan stays the only accent. */
    val LiveLine = Color(0xFFFFFFFF)

    /** A saved measurement projected into the view (SPEC.md §2.11): neutral grey, apart from the user's own line. */
    val ProjectionLine = Color(0xFFB8B8B8)

    /** The thin, crisp near-black edge behind the reticle and every mark over the camera; never blurred. */
    val Backing = Color(0xD9000000)

    /** Behind every piece of live text over the camera feed (SPEC.md §3.3). */
    val Scrim = Color(0xB8000000)

    /** The dark halo behind the reticle and other marks over the camera (SPEC.md §3.1). */
    val Halo = Color(0x99000000)
}
