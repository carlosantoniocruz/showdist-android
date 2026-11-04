package com.showconfigs.showdist.measure

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import kotlin.math.ceil
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

/** Half the bitmap's side: the ring, plus the glow and three blur sigmas of room. */
private const val EXTENT_DP = RING_RADIUS_DP + GLOW_RADIUS_DP + 3 * GLOW_SIGMA_DP + 1f

/** A normal blur with the given Gaussian sigma. Skia maps radius to sigma = 0.57735 * radius + 0.5. */
fun blurForSigma(sigmaPx: Float) =
    BlurMaskFilter(((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)

/**
 * The reticle (SPEC.md §3.1), drawn at the centre of [modifier]'s area: a small white centre
 * dot and three smaller dots orbiting it, crimson for imperial and cyan for metric, with a
 * soft glow once there's a surface, over a dark halo.
 *
 * Each look (unit colour × surface/searching) is pre-rendered once, blur included, into a
 * bitmap. The spin is a single rotation of this composable's own layer, read from
 * [MeasureTracker.spinSeconds] only in the layer block, so turning it redraws nothing: not
 * the bitmap, not the overlay. Spin advances only with new camera images, so it stops
 * whenever the session is paused or the screen is hidden.
 */
@Composable
fun Reticle(tracker: MeasureTracker, metric: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val images = remember(density) { ReticleImages(density) }
    Spacer(
        modifier
            .size((EXTENT_DP * 2).dp)
            .graphicsLayer { rotationZ = 360f * tracker.spinSeconds / RETICLE_PERIOD_S }
            .drawBehind {
                val image = images.get(metric, tracker.hasHit)
                drawImage(image, androidx.compose.ui.geometry.Offset((size.width - image.width) / 2f, (size.height - image.height) / 2f))
            },
    )
}

/** The four pre-rendered looks, each made on first use and kept. */
private class ReticleImages(private val density: Float) {
    private val cache = arrayOfNulls<ImageBitmap>(4)

    fun get(metric: Boolean, hit: Boolean): ImageBitmap {
        val i = (if (metric) 2 else 0) + (if (hit) 1 else 0)
        return cache[i] ?: render(if (metric) Palette.Cyan.toArgb() else Palette.Crimson.toArgb(), hit).also { cache[i] = it }
    }

    /** Draws the reticle exactly as the live painter did, at spin 0, into a bitmap. */
    private fun render(unit: Int, hit: Boolean): ImageBitmap {
        val side = ceil(EXTENT_DP * 2 * density).toInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val cx = side / 2f
        val cy = side / 2f
        val ring = RING_RADIUS_DP * density
        val outerR = OUTER_DOT_RADIUS_DP * density
        val centreR = CENTRE_DOT_RADIUS_DP * density
        val haloR = HALO_WIDTH_DP * density

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = unit
            if (hit) {
                style = Paint.Style.FILL
            } else {
                style = Paint.Style.STROKE
                strokeWidth = HOLLOW_STROKE_DP * density
                alpha = SEARCHING_ALPHA
            }
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = unit
            alpha = GLOW_ALPHA
            maskFilter = blurForSigma(GLOW_SIGMA_DP * density)
        }
        val centre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Palette.White.toArgb()
            alpha = if (hit) 255 else SEARCHING_ALPHA
        }
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Palette.Halo.toArgb()
            maskFilter = blurForSigma(HALO_SIGMA_DP * density)
        }

        // The halo under everything, then the glow (only once there's a surface), then the dots.
        for (k in 0..2) c.drawCircle(cx + ring * cosK(k), cy + ring * sinK(k), outerR + haloR, halo)
        c.drawCircle(cx, cy, centreR + haloR, halo)
        if (hit) for (k in 0..2) c.drawCircle(cx + ring * cosK(k), cy + ring * sinK(k), GLOW_RADIUS_DP * density, glow)
        for (k in 0..2) c.drawCircle(cx + ring * cosK(k), cy + ring * sinK(k), outerR, dot)
        c.drawCircle(cx, cy, centreR, centre)
        return bitmap.asImageBitmap()
    }

    // Dot k sits at −90° + k·120° at spin 0 (the first dot straight up).
    private fun cosK(k: Int) = cos((-90f + k * 120f) * DEG_TO_RAD)
    private fun sinK(k: Int) = sin((-90f + k * 120f) * DEG_TO_RAD)
}
