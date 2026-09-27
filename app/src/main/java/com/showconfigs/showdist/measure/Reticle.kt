package com.showconfigs.showdist.measure

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import com.showconfigs.showdist.ui.Palette
import kotlin.math.cos
import kotlin.math.sin

private const val RING_RADIUS_DP = 16f
private const val OUTER_DOT_RADIUS_DP = 3.5f
private const val CENTRE_DOT_RADIUS_DP = 2f
private const val HOLLOW_STROKE_DP = 1.4f
private const val HALO_WIDTH_DP = 1.5f
private const val HALO_SIGMA_DP = 2f
private const val GLOW_RADIUS_DP = 6f
private const val GLOW_SIGMA_DP = 3f
private const val GLOW_ALPHA = (0.55f * 255).toInt()
private const val SEARCHING_ALPHA = (0.6f * 255).toInt()
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

/** A normal blur with the given Gaussian sigma. Skia maps radius to sigma = 0.57735 * radius + 0.5. */
fun blurForSigma(sigmaPx: Float) =
    BlurMaskFilter(((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)

/**
 * Draws the reticle (SPEC.md §3.1) at a fixed point, normally the centre of the camera area:
 * a small white centre dot and three smaller dots orbiting it, amber for imperial and cyan
 * for metric, with a soft glow. A dark halo sits behind everything so it reads on bright
 * surfaces. All paints are created once per density; only their colour changes per frame.
 */
class ReticlePainter(private val density: Float) {
    private val amber = Palette.Amber.toArgb()
    private val cyan = Palette.Cyan.toArgb()

    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = HOLLOW_STROKE_DP * density
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = blurForSigma(GLOW_SIGMA_DP * density)
    }
    private val centre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.White.toArgb()
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.Halo.toArgb()
        maskFilter = blurForSigma(HALO_SIGMA_DP * density)
    }

    fun draw(c: Canvas, cx: Float, cy: Float, hit: Boolean, metric: Boolean, spinSeconds: Float) {
        val baseDeg = -90f + 360f * spinSeconds / RETICLE_PERIOD_S
        val ring = RING_RADIUS_DP * density
        val outerR = OUTER_DOT_RADIUS_DP * density
        val centreR = CENTRE_DOT_RADIUS_DP * density
        val haloR = HALO_WIDTH_DP * density
        val unit = if (metric) cyan else amber

        dotFill.color = unit
        dotStroke.color = unit
        dotStroke.alpha = SEARCHING_ALPHA
        glow.color = unit
        glow.alpha = GLOW_ALPHA
        val dot = if (hit) dotFill else dotStroke
        centre.alpha = if (hit) 255 else SEARCHING_ALPHA

        // The halo under everything, then the glow (only once there's a surface), then the dots.
        for (k in 0..2) {
            val a = (baseDeg + k * 120f) * DEG_TO_RAD
            c.drawCircle(cx + ring * cos(a), cy + ring * sin(a), outerR + haloR, halo)
        }
        c.drawCircle(cx, cy, centreR + haloR, halo)
        if (hit) {
            for (k in 0..2) {
                val a = (baseDeg + k * 120f) * DEG_TO_RAD
                c.drawCircle(cx + ring * cos(a), cy + ring * sin(a), GLOW_RADIUS_DP * density, glow)
            }
        }
        for (k in 0..2) {
            val a = (baseDeg + k * 120f) * DEG_TO_RAD
            c.drawCircle(cx + ring * cos(a), cy + ring * sin(a), outerR, dot)
        }
        c.drawCircle(cx, cy, centreR, centre)
    }
}
