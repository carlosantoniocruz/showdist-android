package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color
import com.showconfigs.showdist.measure.UnitSystem

/**
 * Fixed app palette (SPEC.md §5): black screens, white and light-grey text, and one accent,
 * sandstone. Every text/background pair clears WCAG AA. Amber and cyan are reserved for
 * unit state in Measure; [Ar] holds the marks drawn into the camera view, which keep their
 * outdoor-readability colours.
 */
object Palette {
    val Black = Color(0xFF000000)

    /** Dialogs, sheets and menus: a hair off black, so they read as raised. */
    val Surface = Color(0xFF121212)

    /** Filled controls and the selected-row tint on black, slightly warm. */
    val SurfaceHigh = Color(0xFF1E1C19)

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

    /** Unit state only: imperial (ft, in). */
    val Amber = Color(0xFFFFB300)

    /** Unit state only: metric (m, cm). */
    val Cyan = Color(0xFF00E5FF)

    /** Behind every piece of live text over the camera feed (SPEC.md §3.3). */
    val Scrim = Color(0xB8000000)

    /** The dark halo behind the reticle and other marks over the camera (SPEC.md §3.1). */
    val Halo = Color(0x99000000)

    fun unit(units: UnitSystem) = if (units == UnitSystem.Metric) Cyan else Amber

    /** Marks drawn into the AR camera view (SPEC.md §3.2), chosen to read on bright scenes. */
    object Ar {
        val Segment = Color(0xFF3DDC84)
        val Point = Color(0xFFFF6E9C)
        val Projection = Color(0xFFB388FF)
    }
}
