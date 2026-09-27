package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color
import com.showconfigs.showdist.measure.UnitSystem

/**
 * Fixed app palette (SPEC.md §5): a night-sky field, true black, with near-white text and
 * bright, saturated accents. Amber and cyan are reserved for unit state and never used as
 * general accents.
 */
object Palette {
    val Black = Color(0xFF000000)

    /** Dialogs, sheets and menus: a hair off black, so they read as raised. */
    val Surface = Color(0xFF121212)

    /** Filled cards and controls on black. */
    val SurfaceHigh = Color(0xFF1C1C1C)

    val Text = Color(0xFFF2F2F2)
    val TextMuted = Color(0xFFB0B0B0)
    val White = Color(0xFFFFFFFF)

    /** Background grid and dividers: white at 10%. */
    val Grid = Color(0x1AFFFFFF)

    /** Constellation lines and outlines that should read, but stay quiet. */
    val Line = Color(0x40FFFFFF)

    val Green = Color(0xFF3DDC84)
    val Violet = Color(0xFFB388FF)
    val Pink = Color(0xFFFF6E9C)

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
