package com.showconfigs.showdist.measure

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import com.showconfigs.showdist.ui.Palette

private const val POINT_RADIUS_DP = 6f
private const val LINE_WIDTH_DP = 1.3f
private const val SHADOW_OFFSET_Y_DP = 1.2f
private const val SHADOW_SIGMA_DP = 1.4f
private const val SHADOW_COLOR = 0xB312354E.toInt()

/**
 * The transparent canvas over the camera feed (SPEC.md §3): segment lines, point dots,
 * then the reticle at the centre. Reads [tracker] only inside the draw lambda, so it redraws
 * once per new camera image (spinSeconds changes every image) and never recomposes.
 */
@Composable
fun MeasureOverlay(tracker: MeasureTracker, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val points = remember(density) { PointsPainter(density) }
    val reticle = remember(density) { ReticlePainter(density) }

    Canvas(modifier) {
        val hit = tracker.hasHit
        val spin = tracker.spinSeconds
        val count = tracker.pointCount
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            points.draw(c, size.width, size.height, count, tracker.screenXY, tracker.visible)
            reticle.draw(c, size.width / 2f, size.height / 2f, hit, spin)
        }
    }
}

/** Placed points and segment lines (SPEC.md §3.2). All paints are created once per density. */
private class PointsPainter(private val density: Float) {
    private val peachRed = Palette.PeachRed.toArgb()
    private val lineWidth = LINE_WIDTH_DP * density
    private val shadowBlur = blurForSigma(SHADOW_SIGMA_DP * density)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
        color = peachRed
    }
    private val lineShadow = Paint(line).apply {
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = peachRed
    }
    private val dotShadow = Paint(dot).apply {
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }

    fun draw(c: Canvas, w: Float, h: Float, count: Int, xy: FloatArray, visible: BooleanArray) {
        val dy = SHADOW_OFFSET_Y_DP * density
        val r = POINT_RADIUS_DP * density

        // Segments are drawn only when both endpoints are visible; shadow copy first.
        for (pass in 0..1) {
            val paint = if (pass == 0) lineShadow else line
            val offset = if (pass == 0) dy else 0f
            for (i in 1 until count) {
                if (!visible[i - 1] || !visible[i]) continue
                c.drawLine(
                    xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h + offset,
                    xy[i * 2] * w, xy[i * 2 + 1] * h + offset,
                    paint,
                )
            }
        }
        for (pass in 0..1) {
            val paint = if (pass == 0) dotShadow else dot
            val offset = if (pass == 0) dy else 0f
            for (i in 0 until count) {
                if (!visible[i]) continue
                c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h + offset, r, paint)
            }
        }
    }
}
