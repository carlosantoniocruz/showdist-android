package com.showconfigs.showdist.measure

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
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
import com.showconfigs.showdist.ui.Palette
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val POINT_RADIUS_DP = 6f
private const val PROJ_POINT_RADIUS_DP = 4.5f
private const val LINE_WIDTH_DP = 2.5f
private const val LIVE_LINE_WIDTH_DP = 3f
private const val DASH_DP = 6f

/** The crisp near-black backing round every line and dot, each side, so they read on bright surfaces. */
private const val BACKING_DP = 1.25f

private const val LABEL_TEXT_SP = 14f
private const val LABEL_PAD_H_DP = 10f
private const val LABEL_PAD_V_DP = 5f
private const val LABEL_EDGE_DP = 8f
private const val LABEL_BORDER_DP = 1.5f

/** The same 4 dp corner as every other box in the app. */
private const val LABEL_CORNER_DP = 4f

/**
 * The transparent canvas over the camera feed (SPEC.md §3). Draw order (§3.2): a projected
 * recording (§2.11), segment lines, the dashed live line, point dots, then labels
 * (projected ones first); the [Reticle] is its own layer on top. Reads [tracker] only
 * inside the draw lambda, so it never recomposes, and redraws only when
 * [MeasureTracker.drawSerial] moves: when a mark has actually moved on screen or a label changed.
 */
@Composable
fun MeasureOverlay(tracker: MeasureTracker, rotation: () -> Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val points = remember(density.density) { PointsPainter(density.density) }
    val labels = remember(density.density, density.fontScale) {
        LabelPainter(context, density.density, density.fontScale)
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
                if (tracker.isLineStart(i) || !tracker.visible[i - 1] || !tracker.visible[i] || !tracker.segmentMidInFront[i]) continue
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
 * Placed points, segment lines and the live line (SPEC.md §3.2): white points, the traced
 * line in the one cyan accent, a dashed white live line, each over a crisp near-black
 * backing drawn first (no blur). The traced line is one path stroked once (twice with its
 * backing) however many segments it has, and likewise a projection. Paints and paths are
 * created once and reused.
 */
private class PointsPainter(private val density: Float) {
    private val backingColor = Palette.Backing.toArgb()
    private val lineWidth = LINE_WIDTH_DP * density
    private val backing = 2 * BACKING_DP * density
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
        strokeJoin = Paint.Join.ROUND
        color = Palette.Accent.toArgb()
    }
    private val lineBacking = Paint(line).apply {
        strokeWidth = lineWidth + backing
        color = backingColor
    }
    private val liveLine = Paint(line).apply {
        strokeWidth = liveLineWidth
        color = Palette.LiveLine.toArgb()
        pathEffect = liveDash
    }
    private val liveLineBacking = Paint(lineBacking).apply {
        strokeWidth = liveLineWidth + backing
        pathEffect = liveDash
    }
    private val dashedBacking = Paint(lineBacking).apply { pathEffect = dash }

    // A projected recording (SPEC.md §2.11): dashed while being placed, solid once placed,
    // with smaller dots than the user's own points.
    private val projLine = Paint(line).apply { color = Palette.ProjectionLine.toArgb() }
    private val projPreviewLine = Paint(projLine).apply { pathEffect = dash }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.White.toArgb()
    }
    private val projDot = Paint(dot).apply { color = Palette.ProjectionLine.toArgb() }
    private val dotBacking = Paint(dot).apply { color = backingColor }

    private val path = Path()

    /**
     * Collects the polyline through [xy] into [path]: a segment joins consecutive points when
     * both are visible and the second doesn't start a new line. Returns whether there's anything to draw.
     */
    private fun trace(xy: FloatArray, visible: BooleanArray, count: Int, w: Float, h: Float, t: MeasureTracker?): Boolean {
        path.rewind()
        var penAt = -1
        for (i in 1 until count) {
            if ((t != null && t.isLineStart(i)) || !visible[i - 1] || !visible[i]) continue
            if (penAt != i - 1) path.moveTo(xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h)
            path.lineTo(xy[i * 2] * w, xy[i * 2 + 1] * h)
            penAt = i
        }
        return penAt >= 0
    }

    /** The projected shape: its lines as one path, then its dots; backings first. */
    fun drawProjection(c: Canvas, w: Float, h: Float, count: Int, placed: Boolean, t: MeasureTracker) {
        val xy = t.projScreenXY
        val v = t.projVisible
        if (trace(xy, v, count, w, h, null)) {
            c.drawPath(path, if (placed) lineBacking else dashedBacking)
            c.drawPath(path, if (placed) projLine else projPreviewLine)
        }
        val r = PROJ_POINT_RADIUS_DP * density
        for (i in 0 until count) {
            if (!v[i]) continue
            c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, r + BACKING_DP * density, dotBacking)
            c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, r, projDot)
        }
    }

    /** The traced line: every drawable segment in one path, stroked once over its backing. */
    fun drawSegments(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        if (!trace(t.screenXY, t.visible, count, w, h, t)) return
        c.drawPath(path, lineBacking)
        c.drawPath(path, line)
    }

    /** Last point to the reticle hit's projected position (not the screen centre). */
    fun drawLiveLine(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val last = count - 1
        if (last < 0) return
        val x0 = t.screenXY[last * 2] * w
        val y0 = t.screenXY[last * 2 + 1] * h
        val x1 = t.hitXY[0] * w
        val y1 = t.hitXY[1] * h
        c.drawLine(x0, y0, x1, y1, liveLineBacking)
        c.drawLine(x0, y0, x1, y1, liveLine)
    }

    fun drawDots(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val xy = t.screenXY
        val r = POINT_RADIUS_DP * density
        for (i in 0 until count) {
            if (!t.visible[i]) continue
            c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, r + BACKING_DP * density, dotBacking)
            c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h, r, dot)
        }
    }
}

/**
 * Distance label pills (SPEC.md §3.3): JetBrains Mono Bold 14 sp, tabular figures and
 * slashed zero, on the dark Palette.Scrim, so they stay legible outdoors.
 * Text is drawn straight from the tracker's char buffers, so nothing is allocated.
 */
private class LabelPainter(context: Context, private val density: Float, fontScale: Float) {
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
    // Border colours: cyan on the traced line's labels, neutral on the live and projected ones.
    val segment = Palette.Accent.toArgb()
    val live = Palette.LiveLine.toArgb()
    val projection = Palette.ProjectionLine.toArgb()
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
        val radius = LABEL_CORNER_DP * density

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
            (radius - inset).coerceAtLeast(0f), (radius - inset).coerceAtLeast(0f), border,
        )
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        c.drawText(chars, 0, length, left + LABEL_PAD_H_DP * density, baseline, text)
        if (turned) c.restore()
    }

    /** Like coerceIn, but keeps [lo] when the range is inverted (pill wider than the canvas). */
    private fun clamp(v: Float, lo: Float, hi: Float) = if (hi < lo) lo else v.coerceIn(lo, hi)
}
