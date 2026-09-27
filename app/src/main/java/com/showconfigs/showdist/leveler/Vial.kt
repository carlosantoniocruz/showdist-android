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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.drawDiamond
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

/** Scale stars every degree; every 5th is larger and labelled. */
private val ScaleGap = 16.dp // from the vial's outline to the row of stars
private val MinorStar = 1.5.dp
private val MajorStar = 3.5.dp
private val LabelGap = 10.dp

/**
 * The bubble vial (SPEC.md §4.2), drawn as a constellation on black: a thin stadium outline
 * along the long axis of the space it's given, two centre marks, and a row of diamond stars
 * down each side, one per degree, joined by a faint line, with 5°, 10° and 15° labelled on
 * one side. The violet bubble carries a soft halo; it and the marks turn green when level.
 * [displayed] feeds the bubble's spring (SPEC.md §4.4), stepped once per display frame; the
 * vial redraws as it moves, without recomposing.
 */
@Composable
fun Vial(displayed: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val outline = remember(density) { Stroke(with(density) { 1.5.dp.toPx() }) }
    val glow = remember(density) { Stroke(with(density) { 8.dp.toPx() }) }
    val star = remember { Path() }
    val measurer = rememberTextMeasurer()
    val labels = remember(measurer, density) {
        val style = BaseText.copy(color = Palette.TextMuted, fontSize = 11.sp, fontFeatureSettings = FEATURES_TABULAR)
        arrayOf("5°", "10°", "15°").map { measurer.measure(it, style) }
    }
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
        val accent = if (level) Palette.Green else Palette.Violet
        val vertical = size.height >= size.width
        val thickness = Thickness.toPx()
        val length = (if (vertical) size.height else size.width) - 2 * EndMargin.toPx()
        if (length <= thickness) return@Canvas
        val radius = CornerRadius(thickness / 2f)
        val cx = center.x
        val cy = center.y

        // Along the vial axis maps to screen y in portrait, x in landscape; across it to the other.
        fun at(along: Float, across: Float) =
            if (vertical) Offset(cx + across, cy + along) else Offset(cx + along, cy + across)

        val vialSize = if (vertical) Size(thickness, length) else Size(length, thickness)
        val topLeft = Offset(cx - vialSize.width / 2f, cy - vialSize.height / 2f)
        drawRoundRect(Palette.White.copy(alpha = 0.04f), topLeft, vialSize, radius)
        drawRoundRect(Palette.Line, topLeft, vialSize, radius, style = outline)

        val bubbleR = BubbleDiameter.toPx() / 2f
        val inset = WallInset.toPx()
        val halfLength = length / 2f - inset
        val halfThickness = thickness / 2f - inset
        val travel = halfLength - bubbleR

        // The scale: a star per degree down both sides, where the bubble's centre sits at
        // that reading, joined by a faint line; 5°, 10° and 15° are larger and labelled.
        val side = thickness / 2f + ScaleGap.toPx()
        val hair = 1.dp.toPx()
        for (s in floatArrayOf(-side, side)) {
            drawLine(Palette.Grid, at(-travel, s), at(travel, s), hair)
            for (deg in -FULL_SCALE_DEG.toInt()..FULL_SCALE_DEG.toInt()) {
                val major = deg % 5 == 0
                val r = (if (major) MajorStar else MinorStar).toPx()
                val color = if (deg == 0) accent else Palette.White.copy(alpha = if (major) 0.85f else 0.4f)
                drawDiamond(star, color, at(deg / FULL_SCALE_DEG * travel, s), r)
            }
        }
        for (k in labels.indices) {
            val label = labels[k]
            val deg = (k + 1) * 5f
            for (sign in floatArrayOf(-1f, 1f)) {
                val p = at(sign * deg / FULL_SCALE_DEG * travel, side + LabelGap.toPx())
                val w = label.size.width
                val h = label.size.height
                // Beside the star in portrait, under it in landscape.
                val origin = if (vertical) Offset(p.x, p.y - h / 2f) else Offset(p.x - w / 2f, p.y)
                drawText(label, topLeft = origin)
            }
        }

        val markColor = if (level) Palette.Green else Palette.Text.copy(alpha = 0.7f)
        val markWidth = (if (level) 3.dp else 1.5.dp).toPx()
        val mark = MarkOffset.toPx()
        val span = MarkHalfSpan.toPx()
        for (m in floatArrayOf(-mark, mark)) drawLine(markColor, at(m, -span), at(m, span), markWidth)

        // A positive reading moves the bubble down in portrait and right in landscape. It
        // travels until it touches the inner wall, and may press on by 30% of its radius.
        physics.pressLimit = PRESS_FRACTION * bubbleR / travel
        val shift = physics.x * travel
        val bx = if (vertical) cx else cx + shift
        val by = if (vertical) cy + shift else cy
        val ux = if (vertical) 0f else 1f
        val uy = if (vertical) 1f else 0f
        bubbleOutline.ellipse(bx, by, bubbleR, ux, uy, abs(physics.vx) * travel)
        bubbleOutline.keepInsideStadium(cx, cy, halfLength, halfThickness, vertical)
        val bubble = bubbleOutline.toPath()
        drawPath(bubble, accent.copy(alpha = 0.25f), style = glow)
        drawPath(bubble, accent)
    }
}
