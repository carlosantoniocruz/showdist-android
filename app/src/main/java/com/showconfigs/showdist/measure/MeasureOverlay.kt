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
import com.showconfigs.showdist.ui.Palette

private const val POINT_RADIUS_DP = 6f
private const val PROJ_POINT_RADIUS_DP = 4.5f
private const val LINE_WIDTH_DP = 1.3f
private const val DASH_DP = 6f
private const val SHADOW_OFFSET_Y_DP = 1.2f
private const val SHADOW_SIGMA_DP = 1.4f

private const val LABEL_TEXT_SP = 12.5f
private const val LABEL_PAD_H_DP = 8f
private const val LABEL_PAD_V_DP = 4f
private const val LABEL_EDGE_DP = 8f
private const val LABEL_BORDER_DP = 1f

/**
 * The transparent canvas over the camera feed (SPEC.md §3). Draw order (§3.2): a projected
 * recording (§2.11), segment lines, the dashed live line, point dots, the reticle, then
 * labels (projected ones first). Reads [tracker] only
 * inside the draw lambda, so it redraws once per new camera image (spinSeconds changes
 * every image) and never recomposes.
 */
@Composable
fun MeasureOverlay(tracker: MeasureTracker, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val points = remember(density.density) { PointsPainter(density.density) }
    val reticle = remember(density.density) { ReticlePainter(density.density) }
    val labels = remember(density.density, density.fontScale) {
        LabelPainter(context, density.density, density.fontScale)
    }

    Canvas(modifier) {
        val hit = tracker.hasHit
        val spin = tracker.spinSeconds
        val count = tracker.pointCount
        val w = size.width
        val h = size.height
        val projStage = tracker.projectionStage
        val projCount = tracker.projectionCount
        val projPlaced = projStage == ProjectionStage.Placed
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            if (projCount > 0) points.drawProjection(c, w, h, projCount, projPlaced, tracker)
            points.drawSegments(c, w, h, count, tracker)
            if (tracker.liveVisible) points.drawLiveLine(c, w, h, count, tracker)
            points.drawDots(c, w, h, count, tracker)
            reticle.draw(c, w / 2f, h / 2f, hit, spin)

            // One label per drawn projected segment, one per drawn segment, plus the live label.
            for (i in 1 until projCount) {
                if (!tracker.projVisible[i - 1] || !tracker.projVisible[i] || !tracker.projMidInFront[i]) continue
                labels.draw(
                    c, w, h,
                    tracker.projMidXY[i * 2], tracker.projMidXY[i * 2 + 1],
                    tracker.projText[i], tracker.projTextLength[i],
                    if (projPlaced) labels.citrineLight else labels.seafoamMuted,
                )
            }
            for (i in 1 until count) {
                if (!tracker.visible[i - 1] || !tracker.visible[i] || !tracker.segmentMidInFront[i]) continue
                labels.draw(
                    c, w, h,
                    tracker.segmentMidXY[i * 2], tracker.segmentMidXY[i * 2 + 1],
                    tracker.segmentText[i], tracker.segmentTextLength[i], labels.peach,
                )
            }
            if (tracker.liveVisible && tracker.liveMidInFront) {
                labels.draw(
                    c, w, h,
                    tracker.liveMidXY[0], tracker.liveMidXY[1],
                    tracker.liveText, tracker.liveTextLength, labels.seafoamMuted,
                )
            }
        }
    }
}

/** Placed points, segment lines and the live line (SPEC.md §3.2). Paints are created once. */
private class PointsPainter(private val density: Float) {
    private val peach = Palette.Peach.toArgb()
    private val shadowColor = Palette.Ink.copy(alpha = 0.7f).toArgb()
    private val lineWidth = LINE_WIDTH_DP * density
    private val shadowBlur = blurForSigma(SHADOW_SIGMA_DP * density)
    private val dash = DashPathEffect(floatArrayOf(DASH_DP * density, DASH_DP * density), 0f)

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
        color = peach
    }
    private val lineShadow = Paint(line).apply {
        color = shadowColor
        maskFilter = shadowBlur
    }
    private val liveLine = Paint(line).apply {
        color = Palette.SeafoamMuted.toArgb()
        pathEffect = dash
    }
    private val liveLineShadow = Paint(lineShadow).apply { pathEffect = dash }

    // A projected recording (SPEC.md §2.11): dashed seafoamMuted while being placed, solid
    // citrineLight once placed, with smaller dots than the user's own points.
    private val projLine = Paint(line).apply { color = Palette.CitrineLight.toArgb() }
    private val projPreviewLine = Paint(liveLine)
    private val projDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Palette.CitrineLight.toArgb()
    }
    private val projPreviewDot = Paint(projDot).apply { color = Palette.SeafoamMuted.toArgb() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = peach
    }
    private val dotShadow = Paint(dot).apply {
        color = shadowColor
        maskFilter = shadowBlur
    }

    /** The projected shape: lines between visible points, then its dots; shadows first. */
    fun drawProjection(c: Canvas, w: Float, h: Float, count: Int, placed: Boolean, t: MeasureTracker) {
        val xy = t.projScreenXY
        val v = t.projVisible
        val dy = SHADOW_OFFSET_Y_DP * density
        val r = PROJ_POINT_RADIUS_DP * density
        for (pass in 0..1) {
            val paint = when {
                pass == 0 -> if (placed) lineShadow else liveLineShadow
                placed -> projLine
                else -> projPreviewLine
            }
            val offset = if (pass == 0) dy else 0f
            for (i in 1 until count) {
                if (!v[i - 1] || !v[i]) continue
                c.drawLine(
                    xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h + offset,
                    xy[i * 2] * w, xy[i * 2 + 1] * h + offset,
                    paint,
                )
            }
        }
        for (pass in 0..1) {
            val paint = if (pass == 0) dotShadow else if (placed) projDot else projPreviewDot
            val offset = if (pass == 0) dy else 0f
            for (i in 0 until count) {
                if (!v[i]) continue
                c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h + offset, r, paint)
            }
        }
    }

    /** Segments are drawn only when both endpoints are visible; shadow copy first. */
    fun drawSegments(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val xy = t.screenXY
        for (pass in 0..1) {
            val paint = if (pass == 0) lineShadow else line
            val offset = if (pass == 0) SHADOW_OFFSET_Y_DP * density else 0f
            for (i in 1 until count) {
                if (!t.visible[i - 1] || !t.visible[i]) continue
                c.drawLine(
                    xy[(i - 1) * 2] * w, xy[(i - 1) * 2 + 1] * h + offset,
                    xy[i * 2] * w, xy[i * 2 + 1] * h + offset,
                    paint,
                )
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
        val dy = SHADOW_OFFSET_Y_DP * density
        c.drawLine(x0, y0 + dy, x1, y1 + dy, liveLineShadow)
        c.drawLine(x0, y0, x1, y1, liveLine)
    }

    fun drawDots(c: Canvas, w: Float, h: Float, count: Int, t: MeasureTracker) {
        val xy = t.screenXY
        val r = POINT_RADIUS_DP * density
        for (pass in 0..1) {
            val paint = if (pass == 0) dotShadow else dot
            val offset = if (pass == 0) SHADOW_OFFSET_Y_DP * density else 0f
            for (i in 0 until count) {
                if (!t.visible[i]) continue
                c.drawCircle(xy[i * 2] * w, xy[i * 2 + 1] * h + offset, r, paint)
            }
        }
    }
}

/**
 * Distance label pills (SPEC.md §3.3): JetBrains Mono 12.5 sp, tabular figures and slashed
 * zero, on Palette.Scrim. Weight 500 renders with the bundled Regular face (SPEC.md §6.1).
 * Text is drawn straight from the tracker's char buffers, so nothing is allocated.
 */
private class LabelPainter(context: Context, private val density: Float, fontScale: Float) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)
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
    // Border colours: peach for a placed segment, seafoamMuted for the live label (and a
    // projection being placed), citrineLight for a placed projection.
    val peach = Palette.Peach.toArgb()
    val seafoamMuted = Palette.SeafoamMuted.toArgb()
    val citrineLight = Palette.CitrineLight.toArgb()
    private val metrics = text.fontMetrics
    private val pillHeight = (metrics.descent - metrics.ascent) + 2 * LABEL_PAD_V_DP * density

    /** [nx], [ny] are the normalised midpoint; hidden if it's off-canvas. */
    fun draw(c: Canvas, w: Float, h: Float, nx: Float, ny: Float, chars: CharArray, length: Int, borderColor: Int) {
        if (length == 0 || nx < 0f || nx > 1f || ny < 0f || ny > 1f) return
        val pillWidth = text.measureText(chars, 0, length) + 2 * LABEL_PAD_H_DP * density
        val edge = LABEL_EDGE_DP * density
        // Clamp the centre so the whole pill stays at least 8 dp inside the canvas.
        val cx = clamp(nx * w, edge + pillWidth / 2f, w - edge - pillWidth / 2f)
        val cy = clamp(ny * h, edge + pillHeight / 2f, h - edge - pillHeight / 2f)
        val left = cx - pillWidth / 2f
        val top = cy - pillHeight / 2f
        val radius = pillHeight / 2f

        c.drawRoundRect(left, top, left + pillWidth, top + pillHeight, radius, radius, scrim)
        border.color = borderColor
        val inset = border.strokeWidth / 2f
        c.drawRoundRect(
            left + inset, top + inset, left + pillWidth - inset, top + pillHeight - inset,
            radius - inset, radius - inset, border,
        )
        val baseline = cy - (metrics.ascent + metrics.descent) / 2f
        c.drawText(chars, 0, length, left + LABEL_PAD_H_DP * density, baseline, text)
    }

    /** Like coerceIn, but keeps [lo] when the range is inverted (pill wider than the canvas). */
    private fun clamp(v: Float, lo: Float, hi: Float) = if (hi < lo) lo else v.coerceIn(lo, hi)
}
