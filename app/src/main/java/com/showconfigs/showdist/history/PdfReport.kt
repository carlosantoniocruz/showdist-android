package com.showconfigs.showdist.history

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.showconfigs.showdist.BuildConfig
import com.showconfigs.showdist.R
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.LocalSnackbarHostState
import com.showconfigs.showdist.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

// US Letter in points, portrait, with 0.75 in margins (SPEC.md §2.12).
private const val PAGE_W = 612
private const val PAGE_H = 792
private const val MARGIN = 54f
private const val CONTENT_W = PAGE_W - 2 * MARGIN
private const val HEADER_H = 48f
private const val FOOTER_H = 24f
private const val CELL_GAP = 12f
private const val CELL_PAD = 12f
private const val CAPTION_H = 30f
private const val LARGE_PER_PAGE = 12 // 3 × 4
private const val MIN_LABEL_PT = 9f
private const val LIST_GAP = 8f
private const val DOT_CLEARANCE = 3f
private const val SEGMENT_SAMPLES = 24
private const val EXPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000

/** Cells per page and label size for a selection of [count] recordings (SPEC.md §2.12). */
internal data class PdfTier(val columns: Int, val rows: Int, val labelPt: Float)

internal fun pdfTier(count: Int): PdfTier = when {
    count <= 1 -> PdfTier(1, 1, 11f)
    count == 2 -> PdfTier(1, 2, 10f)
    count <= 6 -> PdfTier(2, (count + 1) / 2, MIN_LABEL_PT)
    else -> PdfTier(3, 4, MIN_LABEL_PT)
}

internal fun pdfPageCount(count: Int): Int =
    if (count <= 6) 1 else (count + LARGE_PER_PAGE - 1) / LARGE_PER_PAGE

/** Export PDF for the History selection, reporting failures in the app's snackbar. */
@Composable
fun rememberPdfExport(units: UnitSystem): (List<Recording>) -> Unit {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return { recordings ->
        scope.launch {
            val message = PdfReport.share(context, recordings, units)
            if (message != null) snackbar.showSnackbar(message)
        }
    }
}

/**
 * The measurement report (SPEC.md §2.12): vector-only pages drawn with [PdfDocument], one
 * page open at a time, written to `<cache>/exports/` and shared as `application/pdf`.
 */
object PdfReport {
    /** Writes the report and opens the share sheet. Returns a snackbar message on failure. */
    suspend fun share(context: Context, recordings: List<Recording>, units: UnitSystem): String? = try {
        val now = Date()
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            // Exports older than a day are removed on each new export.
            dir.listFiles()?.forEach { if (now.time - it.lastModified() > EXPORT_MAX_AGE_MS) it.delete() }
            val name = "showdist_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(now)}.pdf"
            File(dir, name).also { write(context, recordings, units, now, it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Showdist — Measurement Report")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
        null
    } catch (e: Exception) {
        "Could not export PDF: ${(e.message ?: e.javaClass.simpleName).lineSequence().first().take(117)}"
    }

    /** Renders every page into [out]. Blocking; call off the main thread. */
    internal fun write(context: Context, recordings: List<Recording>, units: UnitSystem, now: Date, out: File) {
        val regular = ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular)
        val bold = ResourcesCompat.getFont(context, R.font.jetbrains_mono_bold)
        val ink = Palette.Ink.toArgb()
        val peach = Palette.Peach.toArgb()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; color = ink }
        val boldText = Paint(text).apply { typeface = bold }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            strokeCap = Paint.Cap.ROUND
            color = peach
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = peach }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Palette.WarmGrey.toArgb()
        }
        val paper = Paint().apply { style = Paint.Style.FILL; color = Palette.White.toArgb() }
        val paints = Paints(text, boldText, line, dot, paper)

        val tier = pdfTier(recordings.size)
        val perPage = tier.columns * tier.rows
        val pages = pdfPageCount(recordings.size)
        val exported = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(now)
        val footerLeft = "Measured with Showdist v${BuildConfig.VERSION_NAME}"

        val doc = PdfDocument()
        try {
            for (p in 0 until pages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, p + 1).create())
                val c = page.canvas
                c.drawColor(Palette.White.toArgb())

                var top = MARGIN
                if (p == 0) {
                    boldText.textSize = 16f
                    c.drawText("Showdist — Measurement Report", MARGIN, top + 16f, boldText)
                    text.textSize = 10f
                    c.drawText("Exported $exported", MARGIN, top + 32f, text)
                    top += HEADER_H
                }
                val bottom = PAGE_H - MARGIN - FOOTER_H
                val cellW = (CONTENT_W - (tier.columns - 1) * CELL_GAP) / tier.columns
                val cellH = (bottom - top - (tier.rows - 1) * CELL_GAP) / tier.rows
                val first = p * perPage
                for (k in 0 until minOf(perPage, recordings.size - first)) {
                    val left = MARGIN + (k % tier.columns) * (cellW + CELL_GAP)
                    val cellTop = top + (k / tier.columns) * (cellH + CELL_GAP)
                    c.drawRect(left, cellTop, left + cellW, cellTop + cellH, border)
                    drawCell(c, recordings[first + k], first + k + 1, units, tier.labelPt, left, cellTop, cellW, cellH, paints)
                }

                text.textSize = MIN_LABEL_PT
                val footerY = PAGE_H - MARGIN
                c.drawText(footerLeft, MARGIN, footerY, text)
                val pageText = "Page ${p + 1} of $pages"
                c.drawText(pageText, PAGE_W - MARGIN - text.measureText(pageText), footerY, text)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private class Paints(val text: Paint, val bold: Paint, val line: Paint, val dot: Paint, val paper: Paint) {
        /** The current cell's label area; labels are kept inside it. */
        var minX = 0f
        var maxX = 0f
        var minY = 0f
        var maxY = 0f
    }

    /** One cell: the diagram at a uniform scale, centred, above a two-line caption. */
    private fun drawCell(
        c: Canvas,
        r: Recording,
        number: Int,
        units: UnitSystem,
        labelPt: Float,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        paints: Paints,
    ) {
        // Caption: number and total, then the date.
        paints.bold.textSize = 10f
        c.drawText("#$number · ${DistanceFormat.format(r.total, units)}", left + CELL_PAD, top + h - CELL_PAD - 12f, paints.bold)
        paints.text.textSize = MIN_LABEL_PT
        c.drawText(r.headingTime, left + CELL_PAD, top + h - CELL_PAD, paints.text)

        // Diagram box, leaving room around the geometry for labels.
        paints.text.textSize = labelPt
        paints.minX = left + 4f
        paints.maxX = left + w - 4f
        paints.minY = top + 4f
        paints.maxY = top + h - CELL_PAD - CAPTION_H
        val room = labelPt * 2.2f
        val boxL = left + CELL_PAD + room
        val boxR = left + w - CELL_PAD - room
        val boxT = top + CELL_PAD + room
        val boxB = top + h - CELL_PAD - CAPTION_H - room
        if (boxR <= boxL || boxB <= boxT) return
        val cx = (boxL + boxR) / 2f
        val cy = (boxT + boxB) / 2f

        if (r.points.size < 2) {
            // No points (a record from before points were stored): one line, labelled with the total.
            val half = (boxR - boxL) * 0.35f
            c.drawLine(cx - half, cy, cx + half, cy, paints.line)
            c.drawCircle(cx - half, cy, 2.5f, paints.dot)
            c.drawCircle(cx + half, cy, 2.5f, paints.dot)
            placeLabel(c, DistanceFormat.format(r.total, units), cx, cy, 0f, -1f, paints)
            return
        }

        val xy = planeCoords(r.points)
        val n = r.points.size
        val lengths = List(n - 1) { DistanceFormat.format(r.segments[it], units) }
        var at = fitDiagram(xy, n, boxL, boxT, boxR, boxB)
        var labels = placeLabels(null, at, n, lengths, paints)

        // Labels that would cover each other, a point or another segment: number the
        // segments instead, and list the lengths beside the diagram.
        if (anyCollision(labels, at, n)) {
            val entries = List(n - 1) { "${it + 1}  ${lengths[it]}" }
            val fm = paints.text.fontMetrics
            val lineH = (fm.descent - fm.ascent) * 1.3f
            val listT = top + CELL_PAD
            val listB = paints.maxY
            val rows = maxOf(1, ((listB - listT) / lineH).toInt())
            val columns = (entries.size + rows - 1) / rows
            val columnW = entries.maxOf { paints.text.measureText(it) } + LIST_GAP
            val listL = maxOf(left + w - CELL_PAD - columns * columnW + LIST_GAP, cx)
            val shown = minOf(entries.size, rows)
            val firstY = (listT + listB) / 2f - shown * lineH / 2f - fm.ascent
            for (k in entries.indices) {
                c.drawText(entries[k], listL + (k / rows) * columnW, firstY + (k % rows) * lineH, paints.text)
            }
            paints.maxX = listL - LIST_GAP
            at = fitDiagram(xy, n, boxL, boxT, maxOf(listL - LIST_GAP - room, boxL + 1f), boxB)
            labels = placeLabels(null, at, n, List(n - 1) { "${it + 1}" }, paints)
        }

        for (i in 1 until n) c.drawLine(at[(i - 1) * 2], at[(i - 1) * 2 + 1], at[i * 2], at[i * 2 + 1], paints.line)
        val dotR = if (labelPt > MIN_LABEL_PT) 3f else 2.5f
        for (i in 0 until n) c.drawCircle(at[i * 2], at[i * 2 + 1], dotR, paints.dot)
        placeLabels(c, at, n, labels.map { it.text }, paints)
    }

    /** The diagram's page positions (x at 2i, y at 2i+1) at one uniform scale, centred in the box. */
    private fun fitDiagram(xy: FloatArray, n: Int, boxL: Float, boxT: Float, boxR: Float, boxB: Float): FloatArray {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until n) {
            minX = minOf(minX, xy[i * 2]); maxX = maxOf(maxX, xy[i * 2])
            minY = minOf(minY, xy[i * 2 + 1]); maxY = maxOf(maxY, xy[i * 2 + 1])
        }
        val spanX = maxX - minX
        val spanY = maxY - minY
        val scale = when {
            spanX < 1e-6f && spanY < 1e-6f -> 1f
            spanX < 1e-6f -> (boxB - boxT) / spanY
            spanY < 1e-6f -> (boxR - boxL) / spanX
            else -> minOf((boxR - boxL) / spanX, (boxB - boxT) / spanY)
        }
        val cx = (boxL + boxR) / 2f
        val cy = (boxT + boxB) / 2f
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f
        // Page y runs down; the diagram's y runs up.
        return FloatArray(n * 2) { k ->
            if (k % 2 == 0) cx + (xy[k] - midX) * scale else cy - (xy[k] - midY) * scale
        }
    }

    private class Label(val text: String, val l: Float, val t: Float, val r: Float, val b: Float)

    /** Whether any label box overlaps another, a point, or a segment other than its own. */
    private fun anyCollision(labels: List<Label>, at: FloatArray, n: Int): Boolean {
        fun hits(a: Label, x: Float, y: Float, pad: Float) =
            x > a.l - pad && x < a.r + pad && y > a.t - pad && y < a.b + pad
        for (i in labels.indices) {
            val a = labels[i]
            for (j in i + 1 until labels.size) {
                val o = labels[j]
                if (a.l < o.r && o.l < a.r && a.t < o.b && o.t < a.b) return true
            }
            for (p in 0 until n) if (hits(a, at[p * 2], at[p * 2 + 1], DOT_CLEARANCE)) return true
            // Label i belongs to segment i + 1 (points i to i + 1); sample the others.
            for (s in 1 until n) {
                if (s == i + 1) continue
                for (k in 1 until SEGMENT_SAMPLES) {
                    val f = k / SEGMENT_SAMPLES.toFloat()
                    val x = at[(s - 1) * 2] + (at[s * 2] - at[(s - 1) * 2]) * f
                    val y = at[(s - 1) * 2 + 1] + (at[s * 2 + 1] - at[(s - 1) * 2 + 1]) * f
                    if (hits(a, x, y, 0f)) return true
                }
            }
        }
        return false
    }

    /**
     * Places one label per segment at its midpoint, pushed off the line on the side away
     * from the shape's centre (either side for a straight shape). Draws them if [c] is set.
     */
    private fun placeLabels(c: Canvas?, at: FloatArray, n: Int, texts: List<String>, paints: Paints): List<Label> {
        var gx = 0f
        var gy = 0f
        for (i in 0 until n) {
            gx += at[i * 2]
            gy += at[i * 2 + 1]
        }
        gx /= n
        gy /= n
        return List(n - 1) { k ->
            val i = k + 1
            val ax = at[(i - 1) * 2]
            val ay = at[(i - 1) * 2 + 1]
            val bx = at[i * 2]
            val by = at[i * 2 + 1]
            val mx = (ax + bx) / 2f
            val my = (ay + by) / 2f
            val len = hypot(bx - ax, by - ay)
            var nx = if (len > 1e-3f) -(by - ay) / len else 0f
            var ny = if (len > 1e-3f) (bx - ax) / len else -1f
            if (nx * (mx - gx) + ny * (my - gy) < 0f) {
                nx = -nx
                ny = -ny
            }
            placeLabel(c, texts[k], mx, my, nx, ny, paints)
        }
    }

    /**
     * Text centred [4 pt + half its box] from ([x], [y]) along the unit normal, on white,
     * shifted back inside the cell if it would cross its edge. Returns its box.
     */
    private fun placeLabel(c: Canvas?, label: String, x: Float, y: Float, nx: Float, ny: Float, paints: Paints): Label {
        val p = paints.text
        val tw = p.measureText(label)
        val fm = p.fontMetrics
        val th = fm.descent - fm.ascent
        val offset = 4f + abs(nx) * tw / 2f + abs(ny) * th / 2f
        val lx = (x + nx * offset).coerceIn(paints.minX + tw / 2f + 1.5f, maxOf(paints.maxX - tw / 2f - 1.5f, paints.minX + tw / 2f + 1.5f))
        val ly = (y + ny * offset).coerceIn(paints.minY + th / 2f + 1f, maxOf(paints.maxY - th / 2f - 1f, paints.minY + th / 2f + 1f))
        val box = Label(label, lx - tw / 2f - 1.5f, ly - th / 2f - 1f, lx + tw / 2f + 1.5f, ly + th / 2f + 1f)
        if (c != null) {
            c.drawRect(box.l, box.t, box.r, box.b, paints.paper)
            c.drawText(label, lx - tw / 2f, ly - (fm.ascent + fm.descent) / 2f, p)
        }
        return box
    }
}

/**
 * Projects a polyline onto its best-fit plane (PCA; SPEC.md §2.12): x at 2i, y at 2i+1, in
 * metres, y up. On a steep plane (a wall) world-up stays up; on a flat one (a floor) it's
 * seen from above. A straight line is drawn horizontal, or vertical if it's steep.
 */
internal fun planeCoords(points: List<DoubleArray>): FloatArray {
    val n = points.size
    val out = FloatArray(n * 2)
    if (n < 2) return out
    val c = DoubleArray(3) { a -> points.sumOf { it[a] } / n }
    val cov = Array(3) { DoubleArray(3) }
    for (p in points) {
        for (i in 0..2) for (j in 0..2) cov[i][j] += (p[i] - c[i]) * (p[j] - c[j])
    }
    val (values, vectors) = symmetricEigen(cov)
    val order = (0..2).sortedByDescending { values[it] }
    val e1 = vectors[order[0]]
    val normal = vectors[order[2]]
    val up = doubleArrayOf(0.0, 1.0, 0.0)

    val u: DoubleArray
    val v: DoubleArray
    if (values[order[1]] <= 1e-9 * maxOf(values[order[0]], 1e-12)) {
        // Collinear: along e1, drawn vertical if the line is steep.
        if (abs(dot(e1, up)) > 0.7) {
            u = doubleArrayOf(0.0, 0.0, 0.0)
            v = if (dot(e1, up) < 0) scale(e1, -1.0) else e1
        } else {
            u = e1
            v = doubleArrayOf(0.0, 0.0, 0.0)
        }
    } else if (abs(dot(normal, up)) < 0.7) {
        // A steep plane: up is up, and across is horizontal within the plane.
        v = normalise(sub(up, scale(normal, dot(up, normal))))
        u = normalise(cross(v, normal))
    } else {
        // A flat plane, seen from above: the widest direction across, the other up the page.
        val nUp = if (normal[1] < 0) scale(normal, -1.0) else normal
        u = normalise(sub(e1, scale(nUp, dot(e1, nUp))))
        v = cross(nUp, u)
    }
    for (i in 0 until n) {
        val d = sub(points[i], c)
        out[i * 2] = dot(d, u).toFloat()
        out[i * 2 + 1] = dot(d, v).toFloat()
    }
    return out
}

/** Eigenvalues and unit eigenvectors of a symmetric 3×3 matrix (cyclic Jacobi). */
private fun symmetricEigen(m: Array<DoubleArray>): Pair<DoubleArray, Array<DoubleArray>> {
    val a = Array(3) { m[it].copyOf() }
    val v = Array(3) { i -> DoubleArray(3) { j -> if (i == j) 1.0 else 0.0 } }
    for (sweep in 0 until 50) {
        var off = 0.0
        for (p in 0..2) for (q in p + 1..2) off += a[p][q] * a[p][q]
        if (off < 1e-24) break
        for (p in 0..2) for (q in p + 1..2) {
            if (abs(a[p][q]) < 1e-30) continue
            val theta = (a[q][q] - a[p][p]) / (2 * a[p][q])
            val t = (if (theta >= 0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1))
            val cs = 1 / sqrt(t * t + 1)
            val sn = t * cs
            for (k in 0..2) {
                val akp = a[k][p]
                val akq = a[k][q]
                a[k][p] = cs * akp - sn * akq
                a[k][q] = sn * akp + cs * akq
            }
            for (k in 0..2) {
                val apk = a[p][k]
                val aqk = a[q][k]
                a[p][k] = cs * apk - sn * aqk
                a[q][k] = sn * apk + cs * aqk
            }
            for (k in 0..2) {
                val vkp = v[k][p]
                val vkq = v[k][q]
                v[k][p] = cs * vkp - sn * vkq
                v[k][q] = sn * vkp + cs * vkq
            }
        }
    }
    // Columns of v are the eigenvectors.
    return Pair(DoubleArray(3) { a[it][it] }, Array(3) { j -> DoubleArray(3) { i -> v[i][j] } })
}

private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
private fun scale(a: DoubleArray, s: Double) = doubleArrayOf(a[0] * s, a[1] * s, a[2] * s)
private fun cross(a: DoubleArray, b: DoubleArray) =
    doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
private fun normalise(a: DoubleArray): DoubleArray {
    val l = sqrt(dot(a, a))
    return if (l < 1e-12) a else scale(a, 1 / l)
}
