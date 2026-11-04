package com.showconfigs.showdist.measure

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.showconfigs.showdist.R
import com.showconfigs.showdist.ui.ArScheme
import com.showconfigs.showdist.ui.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val POINT_RADIUS_DP = 6f
private const val PROJ_POINT_RADIUS_DP = 4.5f
private const val LINE_WIDTH_DP = 2.5f
private const val LIVE_LINE_WIDTH_DP = 3f
private const val DASH_DP = 6f

/** The dark halo around every line and dot, each side, so they read on bright surfaces. */
private const val HALO_DP = 1.5f
private const val HALO_SIGMA_DP = 1.2f

private const val LABEL_TEXT_SP = 14f
private const val LABEL_PAD_H_DP = 10f
private const val LABEL_PAD_V_DP = 5f
private const val LABEL_EDGE_DP = 8f
private const val LABEL_BORDER_DP = 1.5f

/**
 * The transparent canvas over the camera feed (SPEC.md §3). Draw order (§3.2): a projected
 * recording (§2.11), segment lines, the dashed live line, point dots, then labels
 * (projected ones first); the [Reticle] is its own layer on top. Reads [tracker] only
 * inside the draw lambda, so it never recomposes, and redraws only when
 * [MeasureTracker.drawSerial] moves: on new camera images while there are marks to draw.
 */
@Composable
fun MeasureOverlay(tracker: MeasureTracker, scheme: ArScheme, rotation: () -> Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val points = remember(density.density, scheme) { PointsPainter(density.density, scheme) }
    val labels = remember(density.density, density.fontScale, scheme) {
        LabelPainter(context, density.density, density.fontScale, scheme)
    }

    Canvas(modifier) {
        tracker.drawSerial // subscribes the draw to new camera images with marks
        val count = tracker.pointCount
        val w = size.width
        val h = size.height
        val projStage = tracker.projectionStage
        val projCount = tracker.projectionCount
        val projPlaced = projStage == ProjectionStage.Placed
        // Labels turn with the bar controls so they read upright (SPEC.md §2.9).
        labels.setRotation(rotation())
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            if (projCount > 0) points.drawProjection(c, w, h, projCount, projPlaced, tracker)
            points.drawSegments(c, w, h, count, tracker)
            if (tracker.liveVisible) points.drawLiveLine(c, w, h, count, tracker)
            points.drawDots(c, w, h, count, tracker)

            // One label per drawn projected segment, one per drawn segment, plus the live label.
            for (i in 1 until projCount) {
                if (!tracker.projVisible[i - 1] || !tracker.projVisible[i] || !tracker.projMidInFront[i]) continue
                labels.draw(
                    c, w, h,
                    tracker.projMidXY[i * 2], tracker.projMidXY[i * 2 + 1],
                    tracker.projText[i], tracker.projTextLength[i],
                    labels.projection,
                )
            }
            for (i in 1 until count) {
                if (!tracker.visible[i - 1] || !tracker.visible[i] || !tracker.segmentMidInFront[i]) continue
                labels.draw(
                    c, w, h,
                    tracker.segmentMidXY[i * 2], tracker.segmentMidXY[i * 2 + 1],
                    tracker.segmentText[i], tracker.segmentTextLength[i], labels.segment,
                )
            }
            if (tracker.liveVisible && tracker.liveMidInFront) {
                labels.draw(
                    c, w, h,
                    tracker.liveMidXY[0], tracker.liveMidXY[1],
                    tracker.liveText, tracker.liveTextLength, labels.live,
                )
            }
        }
    }
}

/**
 * Placed points, segment lines and the live line (SPEC.md §3.2), in the [scheme]'s colours
 * (white points; red or cyan segments; a dashed pale live line),
 * each over a dark halo drawn first. Paints are created once per scheme.
 */
private class PointsPainter(private val density: Float, scheme: ArScheme) {
    private val haloColor = Palette.Halo.toArgb()
    private val lineWidth = LINE_WIDTH_DP * density
    private val halo = 2 * HALO_DP * density
    private val haloBlur = blurForSigma(HALO_SIGMA_DP * density)
    private val liveLineWidth = LIVE_LINE_WIDTH_DP * density
    private val dash = DashPathEffect(floatArrayOf(DASH_DP * density, DASH_DP * density), 0f)

    // Round caps add half the width to each end, so the live line's intervals are adjusted
    // to keep its visible dashes and gaps at 6 dp.
    private val liveDash = DashPathEffect(
        floatArrayOf((DASH_DP - LIVE_LINE_WIDTH_DP) * density, (DASH_DP + LIVE_LINE_WIDTH_DP) * density),
        0f,
    )

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
        color = scheme.segment.toArgb()
    }
    private val lineShadow = Paint(line).apply {
        strokeWidth = lineWidth + halo
        color = haloColor
        maskFilter = haloBlur
    }
    private val liveLine = Paint(line).apply {
        strokeWidth = liveLineWidth
        color = scheme.live.toArgb()
        pathEffect = liveDash
    }
    private val liveLineShadow = Paint(lineShadow).apply {
        strokeWidth = liveLineWidth + halo
        pathEffect = liveDash
    }
    private val dashedShadow = Paint(lineShadow).apply { pathEffect = dash }

    // A projected recording (SPEC.md §2.11): dashed while being placed, solid once placed,
    // with smaller dots than the user's own points.
    private val projLine = Paint(line).apply { color = scheme.projection.toArgb() }
    private val projPreviewLine = Paint(projLine).apply { pathEffect = dash }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = scheme.point.toArgb()
    }
    private val projDot = Paint(dot).apply { color = scheme.projection.toArgb() }
    private val dotShadow = Paint(dot).apply {
        color = haloColor
        maskFilter = haloBlur
    }

    /** The projected shape: lines between visible points, then its dots; shadows first. */
    fun drawProjection(c: Canvas, w: Float, h: Float, count: Int, placed: Boolean, t: MeasureTracker) {
        val xy = t.projScreenXY
        val v = t.projVisible
        val r = PROJ_POINT_RADIUS_DP * density
        val haloR = HALO_DP * density
        for (pass in 0..1) {
            val paint = when {
                pass == 0 -> if (placed) lineShadow else dashedShadow
                placed -> projLine
                else -> projPreviewLine
            }
            for (i in 1 until count) {
                if (!v[i - 1] || !v[i]) continue
                c.drawLine(xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h, xy[i * 2] * w, xy[i * 2 + 1] * h, paint)
            }
        }
        for (pass in 0..1) {
            val paint = if (pass == 0) dotShadow else projDot
            val radius = if (pass == 0) r + haloR else r
            for (i in 0 until count) {
                if (!v[i]) continue
                c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, radius, paint)
            }
        }
    }

    /** Segments are drawn only when both endpoints are visible; halos first. */
    fun drawSegments(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val xy = t.screenXY
        for (pass in 0..1) {
            val paint = if (pass == 0) lineShadow else line
            for (i in 1 until count) {
                if (!t.visible[i - 1] || !t.visible[i]) continue
                c.drawLine(xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h, xy[i * 2] * w, xy[i * 2 + 1] * h, paint)
            }
        }
    }

    /** Last point to the reticle hit's projected position (not the screen centre). */
    fun drawLiveLine(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val last = count - 1
        if (last < 0) return
        val x0 = t.screenXY[last * 2] * w
        val y0 = t.screenXY[last * 2 + 1] * h
        val x1 = t.hitXY[0] * w
        val y1 = t.hitXY[1] * h
        c.drawLine(x0, y0, x1, y1, liveLineShadow)
        c.drawLine(x0, y0, x1, y1, liveLine)
    }

    fun drawDots(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val xy = t.screenXY
        val r = POINT_RADIUS_DP * density
        for (pass in 0..1) {
            val paint = if (pass == 0) dotShadow else dot
            val radius = if (pass == 0) r + HALO_DP * density else r
            for (i in 0 until count) {
                if (!t.visible[i]) continue
                c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, radius, paint)
            }
        }
    }
}

/**
 * Distance label pills (SPEC.md §3.3): JetBrains Mono Bold 14 sp, tabular figures and
 * slashed zero, on the dark Palette.Scrim, so they stay legible outdoors.
 * Text is drawn straight from the tracker's char buffers, so nothing is allocated.
 */
private class LabelPainter(context: Context, private val density: Float, fontScale: Float, scheme: ArScheme) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold)
        textSize = LABEL_TEXT_SP * density * fontScale
        color = Palette.White.toArgb()
        fontFeatureSettings = "'tnum', 'zero'"
    }
    private val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.Scrim.toArgb()
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LABEL_BORDER_DP * density
    }
    // Border colours: the scheme's segment, live-line and projection colours.
    val segment = scheme.segment.toArgb()
    val live = scheme.live.toArgb()
    val projection = scheme.projection.toArgb()
    private val metrics = text.fontMetrics
    private val pillHeight = (metrics.descent - metrics.ascent) + 2 * LABEL_PAD_V_DP * density

    private var rotationDeg = 0f
    private var cosAbs = 1f
    private var sinAbs = 0f

    /** Degrees clockwise to turn every pill about its centre. */
    fun setRotation(degrees: Float) {
        if (degrees == rotationDeg) return
        rotationDeg = degrees
        val rad = Math.toRadians(degrees.toDouble())
        cosAbs = abs(cos(rad)).toFloat()
        sinAbs = abs(sin(rad)).toFloat()
    }

    /** [nx], [ny] are the normalised midpoint; hidden if it's off-canvas. */
    fun draw(c: Canvas, w: Float, h: Float, nx: Float, ny: Float, chars: CharArray, length: Int, borderColor: Int) {
        if (length == 0 || nx < 0f || nx > 1f || ny < 0f || ny > 1f) return
        val pillWidth = text.measureText(chars, 0, length) + 2 * LABEL_PAD_H_DP * density
        val edge = LABEL_EDGE_DP * density
        // Clamp the centre so the whole (turned) pill stays at least 8 dp inside the canvas.
        val boundsW = cosAbs * pillWidth + sinAbs * pillHeight
        val boundsH = sinAbs * pillWidth + cosAbs * pillHeight
        val cx = clamp(nx * w, edge + boundsW / 2f, w - edge - boundsW / 2f)
        val cy = clamp(ny * h, edge + boundsH / 2f, h - edge - boundsH / 2f)
        val left = cx - pillWidth / 2f
        val top = cy - pillHeight / 2f
        val radius = pillHeight / 2f

        val turned = rotationDeg != 0f
        if (turned) {
            c.save()
            c.rotate(rotationDeg, cx, cy)
        }
        c.drawRoundRect(left, top, left + pillWidth, top + pillHeight, radius, radius, scrim)
        border.color = borderColor
        val inset = border.strokeWidth / 2f
        c.drawRoundRect(
            left + inset, top + inset, left + pillWidth - inset, top + pillHeight - inset,
            radius - inset, radius - inset, border,
        )
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        c.drawText(chars, 0, length, left + LABEL_PAD_H_DP * density, baseline, text)
        if (turned) c.restore()
    }

    /** Like coerceIn, but keeps [lo] when the range is inverted (pill wider than the canvas). */
    private fun clamp(v: Float, lo: Float, hi: Float) = if (hi < lo) lo else v.coerceIn(lo, hi)
}
