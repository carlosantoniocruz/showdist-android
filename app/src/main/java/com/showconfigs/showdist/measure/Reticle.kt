package com.showconfigs.showdist.measure

import android.graphics.BlurMaskFilter
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
import kotlin.math.cos
import kotlin.math.sin

private const val RING_RADIUS_DP = 22f
private const val OUTER_DOT_RADIUS_DP = 5f
private const val CENTRE_DOT_RADIUS_DP = 2.6f
private const val HOLLOW_STROKE_DP = 1.6f
private const val SHADOW_OFFSET_Y_DP = 1.5f
private const val SHADOW_SIGMA_DP = 2.5f
private const val SHADOW_COLOR = 0x66000000
private const val SEARCHING_ALPHA = (0.6f * 255).toInt()
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

/**
 * The reticle (SPEC.md §3.1), drawn fixed at the centre of the camera area.
 * Reads [tracker] only inside the draw lambda, so it redraws once per new camera image
 * and never recomposes. All paints are created once per density.
 */
@Composable
fun Reticle(tracker: ReticleTracker, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val paints = remember(density) { ReticlePaints(density) }

    Canvas(modifier) {
        val hit = tracker.hasHit
        val baseDeg = -90f + 360f * tracker.spinSeconds / RETICLE_PERIOD_S
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ring = RING_RADIUS_DP * density
        val outerR = OUTER_DOT_RADIUS_DP * density
        val centreR = CENTRE_DOT_RADIUS_DP * density
        val shadowDy = SHADOW_OFFSET_Y_DP * density

        val dot = if (hit) paints.dotFill else paints.dotStroke
        val dotShadow = if (hit) paints.shadowFill else paints.shadowStroke
        paints.centre.alpha = if (hit) 255 else SEARCHING_ALPHA

        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            // Shadows first, under every dot.
            for (k in 0..2) {
                val a = (baseDeg + k * 120f) * DEG_TO_RAD
                c.drawCircle(cx + ring * cos(a), cy + ring * sin(a) + shadowDy, outerR, dotShadow)
            }
            c.drawCircle(cx, cy + shadowDy, centreR, paints.shadowFill)

            for (k in 0..2) {
                val a = (baseDeg + k * 120f) * DEG_TO_RAD
                c.drawCircle(cx + ring * cos(a), cy + ring * sin(a), outerR, dot)
            }
            c.drawCircle(cx, cy, centreR, paints.centre)
        }
    }
}

private class ReticlePaints(density: Float) {
    private val seaGreen = Palette.SeaGreen.toArgb()
    private val strokeWidth = HOLLOW_STROKE_DP * density

    // BlurMaskFilter takes a radius; Skia maps it to sigma = 0.57735 * radius + 0.5.
    private val shadowBlur = BlurMaskFilter(
        ((SHADOW_SIGMA_DP * density - 0.5f) / 0.57735f).coerceAtLeast(0.1f),
        BlurMaskFilter.Blur.NORMAL,
    )

    val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = seaGreen
    }
    val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ReticlePaints.strokeWidth
        color = seaGreen
        alpha = SEARCHING_ALPHA
    }
    val centre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.White.toArgb()
    }
    val shadowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }
    val shadowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ReticlePaints.strokeWidth
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }
}
