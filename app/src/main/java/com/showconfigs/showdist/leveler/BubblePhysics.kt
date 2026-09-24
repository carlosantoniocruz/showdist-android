package com.showconfigs.showdist.leveler

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private const val STIFFNESS = 90f // ω ≈ 9.5 rad/s, about 1.5 Hz
private const val DAMPING_RATIO = 0.5f // under-damped: a slight settle, not a snap
private val DAMPING = 2f * DAMPING_RATIO * sqrt(STIFFNESS)
private const val MAX_SUBSTEP_S = 1f / 120f
private const val MAX_FRAME_S = 0.05f
private const val BOUNCE = 0.25f
private const val PRESS_GAIN = 2f

/**
 * The bubble's motion (SPEC.md §4.4): a spring-damper pulling the bubble toward the target
 * set by the tilt, stepped once per display frame. Positions are normalised so 1 is where
 * the round bubble just touches the wall; the tube uses x only, the bullseye x and y.
 *
 * Beyond full scale the target presses on past the wall by up to [pressLimit] (set by the
 * drawing, which knows the pixel sizes), so a pegged bubble flattens against it. Nothing
 * here allocates.
 */
class BubblePhysics {
    /** Position; snapshot state, so the canvas redraws when it moves. */
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    /** Velocity, normalised units per second. Read while drawing, alongside [x] and [y]. */
    var vx = 0f
        private set
    var vy = 0f
        private set

    /** How far past the wall the bubble may press, in normalised units. */
    var pressLimit = 0f

    private var px = 0f
    private var py = 0f

    /**
     * Advances by [dtSeconds] toward the raw target ([tx], [ty]), where ±1 is full scale.
     * Writes the snapshot position only when it has visibly moved.
     */
    fun step(dtSeconds: Float, tx: Float, ty: Float) {
        // Clamp the target to the disc; past full scale it may press into the wall.
        var gx = tx
        var gy = ty
        val m = hypot(gx, gy)
        if (m > 1f) {
            val allowed = 1f + minOf((m - 1f) * PRESS_GAIN, 1f) * pressLimit
            gx *= allowed / m
            gy *= allowed / m
        }
        val limit = 1f + pressLimit

        var remaining = dtSeconds.coerceIn(0f, MAX_FRAME_S)
        while (remaining > 0f) {
            val h = minOf(remaining, MAX_SUBSTEP_S)
            remaining -= h
            // Semi-implicit Euler: velocity first, then position.
            vx += (STIFFNESS * (gx - px) - DAMPING * vx) * h
            vy += (STIFFNESS * (gy - py) - DAMPING * vy) * h
            px += vx * h
            py += vy * h
            // The wall: stop at the press limit and bounce back a little.
            val r = hypot(px, py)
            if (r > limit) {
                val nx = px / r
                val ny = py / r
                px = nx * limit
                py = ny * limit
                val vn = vx * nx + vy * ny
                if (vn > 0f) {
                    vx -= (1f + BOUNCE) * vn * nx
                    vy -= (1f + BOUNCE) * vn * ny
                }
            }
        }
        if (abs(px - x) > 1e-4f) x = px
        if (abs(py - y) > 1e-4f) y = py
    }
}

private const val OUTLINE_POINTS = 64
private const val MAX_STRETCH = 1.4f

/**
 * The bubble's outline (SPEC.md §4.4), rebuilt each frame into one reused [path]: an
 * ellipse of the bubble's area, stretched along its direction of travel, whose points the
 * caller then pushes back inside the vial's wall so the contact side follows the wall.
 */
class BubbleOutline {
    val xs = FloatArray(OUTLINE_POINTS)
    val ys = FloatArray(OUTLINE_POINTS)
    val path = Path()
    private val cosT = FloatArray(OUTLINE_POINTS) { cos(2.0 * PI * it / OUTLINE_POINTS).toFloat() }
    private val sinT = FloatArray(OUTLINE_POINTS) { sin(2.0 * PI * it / OUTLINE_POINTS).toFloat() }

    /**
     * Fills [xs] and [ys] with an ellipse centred at ([cx], [cy]) with [radius]'s area:
     * semi-axis `radius × s` along the unit direction ([ux], [uy]) and `radius / s` across,
     * where s grows with [speedPx] (pixels per second) and is 1 at rest.
     */
    fun ellipse(cx: Float, cy: Float, radius: Float, ux: Float, uy: Float, speedPx: Float) {
        val s = 1f + minOf(speedPx / (radius * 25f), MAX_STRETCH - 1f)
        val a = radius * s
        val b = radius / s
        for (i in 0 until OUTLINE_POINTS) {
            val lx = a * cosT[i]
            val ly = b * sinT[i]
            xs[i] = cx + lx * ux - ly * uy
            ys[i] = cy + lx * uy + ly * ux
        }
    }

    /** Pushes every point outside the circle ([cx], [cy], [r]) back onto it. */
    fun keepInsideCircle(cx: Float, cy: Float, r: Float) {
        for (i in 0 until OUTLINE_POINTS) {
            val dx = xs[i] - cx
            val dy = ys[i] - cy
            val d = hypot(dx, dy)
            if (d > r) {
                xs[i] = cx + dx * r / d
                ys[i] = cy + dy * r / d
            }
        }
    }

    /**
     * Pushes every point outside a stadium back onto it: centred at ([cx], [cy]), half
     * length [halfLength] along x ([vertical]: along y) and half thickness [halfThickness],
     * with round ends.
     */
    fun keepInsideStadium(cx: Float, cy: Float, halfLength: Float, halfThickness: Float, vertical: Boolean) {
        val straight = halfLength - halfThickness
        for (i in 0 until OUTLINE_POINTS) {
            // Work in (along, across) coordinates.
            var u = if (vertical) ys[i] - cy else xs[i] - cx
            var w = if (vertical) xs[i] - cx else ys[i] - cy
            if (abs(u) > straight) {
                val ec = if (u > 0f) straight else -straight
                val du = u - ec
                val d = hypot(du, w)
                if (d > halfThickness) {
                    u = ec + du * halfThickness / d
                    w *= halfThickness / d
                }
            } else if (abs(w) > halfThickness) {
                w = if (w > 0f) halfThickness else -halfThickness
            }
            if (vertical) {
                ys[i] = cy + u
                xs[i] = cx + w
            } else {
                xs[i] = cx + u
                ys[i] = cy + w
            }
        }
    }

    /** The outline as a closed polygon in [path]. */
    fun toPath(): Path {
        path.reset()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until OUTLINE_POINTS) path.lineTo(xs[i], ys[i])
        path.close()
        return path
    }
}
