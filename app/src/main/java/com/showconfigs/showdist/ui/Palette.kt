package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color
import com.showconfigs.showdist.measure.UnitSystem

/**
 * Fixed app palette (SPEC.md §5): black screens, white and light-grey text, and one accent,
 * sandstone. Every text/background pair clears WCAG AA. Amber and cyan are reserved for
 * unit state in Measure. The marks drawn into the camera view use [ArScheme].
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
}

/**
 * The colours of the marks drawn into the AR camera view (SPEC.md §3.2, §5), picked in
 * Settings: placed points (dots), segment lines (solid), the live line (dashed) and a
 * projected recording. Each set is bright enough to read on sunlit scenes over its halo,
 * and none uses the reticle's amber or cyan. [key] is what's persisted.
 */
enum class ArScheme(
    val label: String,
    val key: String,
    val point: Color,
    val segment: Color,
    val live: Color,
    val projection: Color,
) {
    Aurora("Aurora", "aurora", Color(0xFFFF6E9C), Color(0xFF3DDC84), Color(0xFFFFFFFF), Color(0xFFB388FF)),
    Nebula("Nebula", "nebula", Color(0xFFFFF176), Color(0xFFFF4FD8), Color(0xFFB9F6CA), Color(0xFF82B1FF)),
    Supernova("Supernova", "supernova", Color(0xFFFFFFFF), Color(0xFFFF5252), Color(0xFFFFF59D), Color(0xFFEA80FC)),
}
