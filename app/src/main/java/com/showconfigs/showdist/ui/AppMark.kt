package com.showconfigs.showdist.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The app's mark (SPEC.md §8.1), the same picture and proportions as the launcher icon: a
 * thin cyan horizontal line with an upright tick at each end, and a white dot on each
 * intersection, where the line meets its tick. The line is [length] long about [centre].
 * [close] (0..1) slides the two dots toward each other along the line and swells them a
 * little, for the About page's hold (SPEC.md §1.6).
 */
fun DrawScope.drawAppMark(centre: Offset, length: Float, close: Float = 0f) {
    val half = length / 2f
    val stroke = length * 0.0625f
    val tick = length * 0.275f
    drawLine(Palette.Accent, Offset(centre.x - half, centre.y), Offset(centre.x + half, centre.y), stroke, StrokeCap.Round)
    for (sign in intArrayOf(-1, 1)) {
        val x = centre.x + sign * half
        drawLine(Palette.Accent, Offset(x, centre.y - tick), Offset(x, centre.y + tick), stroke, StrokeCap.Round)
    }
    val reach = half * (1f - close)
    val radius = length * 0.15f * (1f + 0.25f * close)
    drawCircle(Palette.White, radius, Offset(centre.x - reach, centre.y))
    drawCircle(Palette.White, radius, Offset(centre.x + reach, centre.y))
}
