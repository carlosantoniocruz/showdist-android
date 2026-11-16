package com.showconfigs.showdist.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The app's mark (SPEC.md §8.1), the same picture as the launcher icon: a thin cyan
 * horizontal line with a small upright tick at each end, and a white dot near each end.
 * Drawn [length] wide about [centre]. [close] (0..1) slides the two dots toward each other
 * along the line and swells them a little, for the About page's hold (SPEC.md §1.6).
 */
fun DrawScope.drawAppMark(centre: Offset, length: Float, close: Float = 0f) {
    val half = length / 2f
    val stroke = length * 0.03f
    val tick = length * 0.11f
    drawLine(Palette.Accent, Offset(centre.x - half, centre.y), Offset(centre.x + half, centre.y), stroke, StrokeCap.Butt)
    for (sign in intArrayOf(-1, 1)) {
        val x = centre.x + sign * (half - stroke / 2f)
        drawLine(Palette.Accent, Offset(x, centre.y - tick), Offset(x, centre.y + tick), stroke, StrokeCap.Butt)
    }
    val reach = half * APP_MARK_DOT_REACH * (1f - close)
    val radius = length * 0.065f * (1f + 0.35f * close)
    drawCircle(Palette.White, radius, Offset(centre.x - reach, centre.y))
    drawCircle(Palette.White, radius, Offset(centre.x + reach, centre.y))
}

/** How far out from the centre each dot sits, as a share of half the line. */
const val APP_MARK_DOT_REACH = 0.62f
