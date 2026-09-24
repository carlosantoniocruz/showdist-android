package com.showconfigs.showdist.leveler

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
private val WallInset = 2.dp // the outline's inner edge, plus a hairline

/** How far a pegged bubble may press into the wall, as a fraction of its radius. */
const val PRESS_FRACTION = 0.3f

/**
 * The bubble vial (SPEC.md §4.2): an inkSurface stadium with a citrine outline along the long
 * axis of the space it's given, with two citrine centre marks and a white bubble; the marks
 * and bubble turn peach when level. [displayed] feeds the bubble's spring (SPEC.md §4.4),
 * stepped once per display frame; the vial redraws as it moves, without recomposing.
 */
@Composable
fun Vial(displayed: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val outline = remember(density) { Stroke(with(density) { 2.dp.toPx() }) }
    val physics = remember { BubblePhysics() }
    val bubbleOutline = remember { BubbleOutline() }
    val currentDisplayed by rememberUpdatedState(displayed)
    LaunchedEffect(physics) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) physics.step((now - last) / 1e9f, currentDisplayed() / FULL_SCALE_DEG, 0f)
                last = now
            }
        }
    }

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

        // A positive reading moves the bubble down in portrait and right in landscape. It
        // travels until it touches the inner wall, and may press on by 30% of its radius.
        val bubbleR = BubbleDiameter.toPx() / 2f
        val inset = WallInset.toPx()
        val halfLength = length / 2f - inset
        val halfThickness = thickness / 2f - inset
        val travel = halfLength - bubbleR
        physics.pressLimit = PRESS_FRACTION * bubbleR / travel
        val shift = physics.x * travel
        val bx = if (vertical) cx else cx + shift
        val by = if (vertical) cy + shift else cy
        val ux = if (vertical) 0f else 1f
        val uy = if (vertical) 1f else 0f
        bubbleOutline.ellipse(bx, by, bubbleR, ux, uy, abs(physics.vx) * travel)
        bubbleOutline.keepInsideStadium(cx, cy, halfLength, halfThickness, vertical)
        drawPath(bubbleOutline.toPath(), if (level) Palette.Peach else Palette.White)
    }
}
