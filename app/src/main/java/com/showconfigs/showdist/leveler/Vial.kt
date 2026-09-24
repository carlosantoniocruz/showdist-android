package com.showconfigs.showdist.leveler

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import kotlin.math.abs

/** Full scale: ±15° pushes the bubble to the end of the vial (SPEC.md §4.1). */
const val FULL_SCALE_DEG = 15f

/** |displayed| at or under this counts as level (SPEC.md §4.1). */
const val LEVEL_THRESHOLD_DEG = 0.3f

private val Thickness = 84.dp
private val EndMargin = 28.dp
private val BubbleDiameter = 56.dp
private val MarkOffset = 33.dp // 56/2 + 5 from centre
private val MarkHalfSpan = 32.dp // 84/2 − 10

/**
 * The bubble vial (SPEC.md §4.2): an inkSurface stadium with a citrine outline along the long
 * axis of the space it's given, with two citrine centre marks and a white bubble; the marks
 * and bubble turn peach when level. [displayed] is read only while drawing,
 * so sensor updates redraw the vial without recomposing.
 */
@Composable
fun Vial(displayed: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val outline = remember(density) { Stroke(with(density) { 2.dp.toPx() }) }

    Canvas(modifier) {
        val d = displayed()
        val level = abs(d) <= LEVEL_THRESHOLD_DEG
        val vertical = size.height >= size.width
        val thickness = Thickness.toPx()
        val length = (if (vertical) size.height else size.width) - 2 * EndMargin.toPx()
        if (length <= thickness) return@Canvas
        val radius = CornerRadius(thickness / 2f)
        val cx = center.x
        val cy = center.y

        val vialSize = if (vertical) Size(thickness, length) else Size(length, thickness)
        val topLeft = Offset(cx - vialSize.width / 2f, cy - vialSize.height / 2f)
        drawRoundRect(Palette.InkSurface, topLeft, vialSize, radius)
        drawRoundRect(Palette.Citrine, topLeft, vialSize, radius, style = outline)

        // Positions along the vial axis map to screen y in portrait, x in landscape.
        val markColor = if (level) Palette.Peach else Palette.Citrine
        val markWidth = (if (level) 3.5.dp else 2.5.dp).toPx()
        val mark = MarkOffset.toPx()
        val span = MarkHalfSpan.toPx()
        if (vertical) {
            drawLine(markColor, Offset(cx - span, cy - mark), Offset(cx + span, cy - mark), markWidth)
            drawLine(markColor, Offset(cx - span, cy + mark), Offset(cx + span, cy + mark), markWidth)
        } else {
            drawLine(markColor, Offset(cx - mark, cy - span), Offset(cx - mark, cy + span), markWidth)
            drawLine(markColor, Offset(cx + mark, cy - span), Offset(cx + mark, cy + span), markWidth)
        }

        // A positive reading moves the bubble down in portrait and right in landscape.
        val bubbleR = BubbleDiameter.toPx() / 2f
        val travel = length / 2f - bubbleR - 6.dp.toPx()
        val shift = (d / FULL_SCALE_DEG).coerceIn(-1f, 1f) * travel
        val bubble = if (vertical) Offset(cx, cy + shift) else Offset(cx + shift, cy)
        drawCircle(if (level) Palette.Peach else Palette.White, bubbleR, bubble)
    }
}
