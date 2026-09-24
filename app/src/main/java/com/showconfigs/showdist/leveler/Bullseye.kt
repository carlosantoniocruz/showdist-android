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
private val TickLength = 14.dp
private val WallInset = 2.dp // the outline's inner edge, plus a hairline
private val Directions = arrayOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f))

/**
 * The bullseye (SPEC.md §4.3): an inkSurface disc with a citrine outline, faint crosshairs
 * and a centre ring. The white bubble moves toward the higher side, both axes at once; it
 * and the ring turn peach when level. [x] and [y] are the displayed tilts in degrees (see
 * [LevelSensor.tiltX], [LevelSensor.tiltY]); they feed the bubble's spring (SPEC.md §4.4),
 * stepped once per display frame.
 */
@Composable
fun Bullseye(x: () -> Float, y: () -> Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val outline = remember(density) { Stroke(with(density) { 2.dp.toPx() }) }
    val ring = remember(density) { Stroke(with(density) { 2.5.dp.toPx() }) }
    val ringLevel = remember(density) { Stroke(with(density) { 3.5.dp.toPx() }) }
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

        drawCircle(Palette.InkSurface, radius, c)
        drawCircle(Palette.Citrine, radius, c, style = outline)

        // Crosshairs, faint, stopping short of the centre ring; a short full-strength tick
        // where each meets the rim.
        val ringR = bubbleR + TargetGap.toPx()
        val stroke = 1.5.dp.toPx()
        val faint = Palette.Citrine.copy(alpha = 0.35f)
        val tick = TickLength.toPx()
        for (u in Directions) {
            drawLine(faint, c + u * (ringR + 8.dp.toPx()), c + u * radius, stroke)
            drawLine(Palette.Citrine, c + u * (radius - tick), c + u * radius, 2.5.dp.toPx())
        }

        val ringColor = if (level) Palette.Peach else Palette.Citrine
        drawCircle(ringColor, ringR, c, style = if (level) ringLevel else ring)

        // The bubble floats to the high side: right for +x, up the screen for +y. It travels
        // until it touches the rim, and may press on by 30% of its radius. It stretches
        // along its direction of travel.
        val inner = radius - WallInset.toPx()
        val travel = inner - bubbleR
        physics.pressLimit = PRESS_FRACTION * bubbleR / travel
        val vx = physics.vx
        val vy = physics.vy
        val speed = hypot(vx, vy)
        val ux = if (speed > 1e-3f) vx / speed else 1f
        val uy = if (speed > 1e-3f) vy / speed else 0f
        bubbleOutline.ellipse(c.x + physics.x * travel, c.y + physics.y * travel, bubbleR, ux, uy, speed * travel)
        bubbleOutline.keepInsideCircle(c.x, c.y, inner)
        drawPath(bubbleOutline.toPath(), if (level) Palette.Peach else Palette.White)
    }
}
