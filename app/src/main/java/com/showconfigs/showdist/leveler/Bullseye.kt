package com.showconfigs.showdist.leveler

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import kotlin.math.hypot

private val Margin = 28.dp
private val BubbleDiameter = 56.dp
private val TargetGap = 5.dp // between the bubble and the centre ring when centred
private val WallInset = 2.dp // the outline's inner edge, plus a hairline
private val MajorTick = 8.dp // half-length, across the crosshair at the rim
private val Directions = arrayOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))

/**
 * The bullseye (SPEC.md §4.3), flat on black: a thin outer circle, rings at 5° and 10° of
 * tilt, crosshairs with a tick at the rim, and a centre ring. The white bubble moves toward
 * the higher side, both axes at once; it turns green and the ring sandstone when level.
 * [x] and [y] are the displayed tilts in degrees (see
 * [LevelSensor.tiltX], [LevelSensor.tiltY]); they feed the bubble's spring (SPEC.md §4.4),
 * stepped once per display frame.
 */
@Composable
fun Bullseye(x: () -> Float, y: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val outline = remember(density) { Stroke(with(density) { 1.5.dp.toPx() }) }
    val hairline = remember(density) { Stroke(with(density) { 1.dp.toPx() }) }
    val ring = remember(density) { Stroke(with(density) { 1.5.dp.toPx() }) }
    val ringLevel = remember(density) { Stroke(with(density) { 3.dp.toPx() }) }
    val physics = remember { BubblePhysics() }
    val bubbleOutline = remember { BubbleOutline() }
    val currentX by rememberUpdatedState(x)
    val currentY by rememberUpdatedState(y)
    LaunchedEffect(physics) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // Screen y runs down, so a positive Y tilt (top edge higher) targets up the screen.
                if (last != 0L) {
                    physics.step((now - last) / 1e9f, currentX() / FULL_SCALE_DEG, -currentY() / FULL_SCALE_DEG)
                }
                last = now
            }
        }
    }

    Canvas(modifier) {
        val dx = x()
        val dy = y()
        val level = hypot(dx, dy) <= LEVEL_THRESHOLD_DEG
        val radius = minOf(size.width, size.height) / 2f - Margin.toPx()
        val bubbleR = BubbleDiameter.toPx() / 2f
        if (radius <= bubbleR) return@Canvas
        val c = center
        val inner = radius - WallInset.toPx()
        val travel = inner - bubbleR

        drawCircle(Palette.White.copy(alpha = 0.04f), radius, c)
        drawCircle(Palette.Line, radius, c, style = outline)

        // Rings where the bubble's centre sits at 5° and 10°, and crosshairs stopping short
        // of the centre ring, each with a tick across it at the rim.
        val ringR = bubbleR + TargetGap.toPx()
        val r5 = 5f / FULL_SCALE_DEG * travel
        val r10 = 10f / FULL_SCALE_DEG * travel
        drawCircle(Palette.Line, r5, c, style = hairline)
        drawCircle(Palette.Line, r10, c, style = hairline)
        val hair = 1.dp.toPx()
        val tick = MajorTick.toPx()
        for (u in Directions) {
            drawLine(Palette.Line, c + u * (ringR + 8.dp.toPx()), c + u * radius, hair)
            val across = Offset(-u.y, u.x) * tick
            drawLine(Palette.White.copy(alpha = 0.85f), c + u * radius - across, c + u * radius + across, 2.dp.toPx())
        }

        val ringColor = if (level) Palette.Sandstone else Palette.Text.copy(alpha = 0.7f)
        drawCircle(ringColor, ringR, c, style = if (level) ringLevel else ring)

        // The bubble floats to the high side: right for +x, up the screen for +y. It travels
        // until it touches the rim, and may press on by 30% of its radius. It stretches
        // along its direction of travel.
        physics.pressLimit = PRESS_FRACTION * bubbleR / travel
        val vx = physics.vx
        val vy = physics.vy
        val speed = hypot(vx, vy)
        val ux = if (speed > 1e-3f) vx / speed else 1f
        val uy = if (speed > 1e-3f) vy / speed else 0f
        bubbleOutline.ellipse(c.x + physics.x * travel, c.y + physics.y * travel, bubbleR, ux, uy, speed * travel)
        bubbleOutline.keepInsideCircle(c.x, c.y, inner)
        val bubble = bubbleOutline.toPath()
        drawPath(bubble, if (level) Palette.Level else Palette.White)
    }
}
