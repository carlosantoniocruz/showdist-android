package com.showconfigs.showdist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

private const val GRID_DP = 32f

/** How far text keeps stars and lines away (SPEC.md §6.4). */
private const val TEXT_CLEARANCE_DP = 6f

/**
 * The stars, in grid cells from the top-left (a negative column counts from the right
 * edge), and which pairs are joined (SPEC.md §6.4). They sit in the top band and the side
 * margins, where the menu has no text; any that still land behind text are skipped.
 */
private val Stars = arrayOf(
    // Top band.
    Star(1.5f, 1f, Palette.White), Star(4f, 1.6f, Palette.Violet), Star(6.5f, 0.8f, Palette.White), Star(9f, 1.4f, Palette.White),
    // Left margin, zigzagging down.
    Star(0.2f, 5f, Palette.White), Star(0.45f, 8f, Palette.Green), Star(0.2f, 11f, Palette.White),
    Star(0.45f, 19f, Palette.White), Star(0.2f, 23f, Palette.White),
    // Right margin.
    Star(-0.2f, 4f, Palette.White), Star(-0.45f, 7f, Palette.White), Star(-0.2f, 13f, Palette.Pink),
    Star(-0.45f, 17f, Palette.White), Star(-0.2f, 22f, Palette.White), Star(-0.45f, 26f, Palette.White),
)
private val Links = arrayOf(
    0 to 1, 1 to 2, 2 to 3,
    4 to 5, 5 to 6, 7 to 8,
    9 to 10, 10 to 11, 12 to 13, 13 to 14,
)

private class Star(val col: Float, val row: Float, val color: Color)

/**
 * The menu's background (SPEC.md §6.4), drawn behind the content and static: a faint grid
 * and a few diamond stars joined by thin lines. No star or line is drawn within 6 dp of
 * [avoid], the bounds of the menu's text in this canvas's coordinates, so none sits
 * behind anything to read.
 */
@Composable
fun NightSky(avoid: Collection<Rect>, modifier: Modifier = Modifier) {
    val star = remember { Path() }

    Canvas(modifier) {
        val cell = GRID_DP.dp.toPx()
        val hair = 1.dp.toPx()
        val clearance = TEXT_CLEARANCE_DP.dp.toPx()

        var x = cell
        while (x < size.width) {
            drawLine(Palette.Grid, Offset(x, 0f), Offset(x, size.height), hair)
            x += cell
        }
        var y = cell
        while (y < size.height) {
            drawLine(Palette.Grid, Offset(0f, y), Offset(size.width, y), hair)
            y += cell
        }

        fun at(s: Star) = Offset(if (s.col < 0) size.width + s.col * cell else s.col * cell, s.row * cell)
        fun clear(p: Offset) = p.x in 0f..size.width && p.y in 0f..size.height &&
            avoid.none { it.inflate(clearance).contains(p) }
        // A line is clear if every point along it, a few dp apart, is.
        fun clear(a: Offset, b: Offset): Boolean {
            val steps = ((b - a).getDistance() / (4.dp.toPx())).toInt().coerceAtLeast(1)
            return (0..steps).all { clear(a + (b - a) * (it / steps.toFloat())) }
        }
        for ((a, b) in Links) {
            val pa = at(Stars[a])
            val pb = at(Stars[b])
            if (clear(pa, pb)) drawLine(Palette.Line.copy(alpha = 0.14f), pa, pb, hair)
        }
        for (s in Stars) {
            val p = at(s)
            if (!clear(p)) continue
            drawDiamond(star, s.color.copy(alpha = if (s.color == Palette.White) 0.45f else 0.8f), p, 3.dp.toPx())
        }
    }
}

/**
 * A four-point diamond star of half-diagonal [r] at [c]: the app's constellation mark.
 * [path] is reused, so drawing one allocates nothing.
 */
fun DrawScope.drawDiamond(path: Path, color: Color, c: Offset, r: Float) {
    path.reset()
    path.moveTo(c.x, c.y - r)
    path.lineTo(c.x + r, c.y)
    path.lineTo(c.x, c.y + r)
    path.lineTo(c.x - r, c.y)
    path.close()
    drawPath(path, color)
}
