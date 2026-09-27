package com.showconfigs.showdist.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.drawDiamond

private val PictureSize = 44.dp

/**
 * The app icon's mark (SPEC.md §8.1): two points joined by a rule with a tick cap at each
 * end, in the icon's colours. Also stretched under the wordmark.
 */
@Composable
fun DimensionMark(modifier: Modifier = Modifier.size(PictureSize)) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val r = 4.dp.toPx()
        val cy = size.height / 2f
        val x0 = r
        val x1 = size.width - r
        val cap = minOf(size.height / 2f, 9.dp.toPx())
        drawLine(Palette.Green, Offset(x0, cy), Offset(x1, cy), stroke, StrokeCap.Round)
        for (x in floatArrayOf(x0, x1)) {
            drawLine(Palette.Green, Offset(x, cy - cap), Offset(x, cy + cap), stroke, StrokeCap.Round)
            drawCircle(Palette.Pink, r, Offset(x, cy))
        }
    }
}

/** A small tube vial, as drawn on the tube Leveler (SPEC.md §4.2). */
@Composable
fun TubePicture() {
    val star = remember { Path() }
    Canvas(Modifier.size(PictureSize)) {
        val stroke = 1.5.dp.toPx()
        val thickness = 16.dp.toPx()
        val vial = Size(size.width, thickness)
        val topLeft = Offset(0f, (size.height - thickness) / 2f)
        drawRoundRect(Palette.Line, topLeft, vial, CornerRadius(thickness / 2f), style = Stroke(stroke))
        val bubbleR = 5.dp.toPx()
        val mark = bubbleR + 3.dp.toPx()
        for (x in floatArrayOf(center.x - mark, center.x + mark)) {
            drawDiamond(star, Palette.White, Offset(x, center.y), 2.5.dp.toPx())
        }
        drawCircle(Palette.Violet, bubbleR, center)
    }
}

/** A small bullseye, as drawn on the bullseye Leveler (SPEC.md §4.3). */
@Composable
fun BullseyePicture() {
    val star = remember { Path() }
    Canvas(Modifier.size(PictureSize)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        drawCircle(Palette.Line, radius, center, style = Stroke(stroke))
        drawCircle(Palette.Line, radius * 0.55f, center, style = Stroke(1.dp.toPx()))
        for (u in arrayOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))) {
            drawDiamond(star, Palette.White, center + u * radius, 2.5.dp.toPx())
        }
        drawCircle(Palette.Violet, 5.dp.toPx(), center)
    }
}
