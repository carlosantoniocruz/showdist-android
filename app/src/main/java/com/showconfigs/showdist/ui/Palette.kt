package com.showconfigs.showdist.ui

import androidx.compose.ui.graphics.Color

/** Fixed app palette (SPEC.md §5). */
object Palette {
    val Ink = Color(0xFF0D313D)
    val InkSurface = Color(0xFF1A3E4A)
    val OlympicBlue = Color(0xFF4D84C7)
    val Citrine = Color(0xFFA29553)

    /** A light citrine that stays bright over the camera feed: placed projections (§2.11). */
    val CitrineLight = Color(0xFFEDDD91)
    val Peach = Color(0xFFFB5628)
    val Seafoam = Color(0xFF0EB997)
    val SeafoamMuted = Color(0xFF53AC9A)
    val WarmGrey = Color(0xFFC0B6A5)
    val White = Color(0xFFFFFFFF)

    /** Behind distance labels and the running total. */
    val Scrim = Color(0x80000000)

    /** The reticle's drop shadow (SPEC.md §3.1). */
    val ReticleShadow = Color(0x66000000)
}
