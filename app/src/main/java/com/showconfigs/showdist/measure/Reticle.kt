package com.showconfigs.showdist.measure

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
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

/** A normal blur with the given Gaussian sigma. Skia maps radius to sigma = 0.57735 * radius + 0.5. */
fun blurForSigma(sigmaPx: Float) =
    BlurMaskFilter(((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)

/**
 * Draws the reticle (SPEC.md §3.1) at a fixed point, normally the centre of the camera area.
 * All paints are created once per density.
 */
class ReticlePainter(private val density: Float) {
    private val seafoam = Palette.Seafoam.toArgb()
    private val strokeWidth = HOLLOW_STROKE_DP * density
    private val shadowBlur = blurForSigma(SHADOW_SIGMA_DP * density)

    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = seafoam
    }
    private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ReticlePainter.strokeWidth
        color = seafoam
        alpha = SEARCHING_ALPHA
    }
    private val centre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.White.toArgb()
    }
    private val shadowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }
    private val shadowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@ReticlePainter.strokeWidth
        color = SHADOW_COLOR
        maskFilter = shadowBlur
    }

    fun draw(c: Canvas, cx: Float, cy: Float, hit: Boolean, spinSeconds: Float) {
        val baseDeg = -90f + 360f * spinSeconds / RETICLE_PERIOD_S
        val ring = RING_RADIUS_DP * density
        val outerR = OUTER_DOT_RADIUS_DP * density
        val centreR = CENTRE_DOT_RADIUS_DP * density
        val shadowDy = SHADOW_OFFSET_Y_DP * density

        val dot = if (hit) dotFill else dotStroke
        val dotShadow = if (hit) shadowFill else shadowStroke
        centre.alpha = if (hit) 255 else SEARCHING_ALPHA

        // Shadows first, under every dot.
        for (k in 0..2) {
            val a = (baseDeg + k * 120f) * DEG_TO_RAD
            c.drawCircle(cx + ring * cos(a), cy + ring * sin(a) + shadowDy, outerR, dotShadow)
        }
        c.drawCircle(cx, cy + shadowDy, centreR, shadowFill)

        for (k in 0..2) {
            val a = (baseDeg + k * 120f) * DEG_TO_RAD
            c.drawCircle(cx + ring * cos(a), cy + ring * sin(a), outerR, dot)
        }
        c.drawCircle(cx, cy, centreR, centre)
    }
}
