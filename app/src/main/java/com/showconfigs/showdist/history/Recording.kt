package com.showconfigs.showdist.history

import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * A saved measurement (SPEC.md §2.7): an ordered polyline of world positions in metres,
 * whose order is the segment order. [id] is the creation time in microseconds since the
 * epoch; [createdAt] is an ISO-8601 local timestamp such as `2026-09-23T17:31:05.123456`.
 * [unit] is the unit setting when it was saved (null for recordings saved before v1.1).
 * [storedTotal] is the total from the file, used only when there are no points to measure.
 */
class Recording(
    val id: String,
    val createdAt: String,
    val points: List<DoubleArray>,
    val unit: UnitSystem? = null,
    storedTotal: Double? = null,
) {
    val segments: List<Double> = (1 until points.size).map { i -> distance(points[i - 1], points[i]) }
    val total: Double = if (points.size >= 2 || storedTotal == null) segments.sum() else storedTotal

    /** "YYYY-MM-DD HH:MM", shown in shared text and the PDF report. */
    val headingTime: String get() = createdAt.take(16).replace('T', ' ')


    companion object {
        /** A new recording stamped with the current time and the current [unit] setting. */
        fun create(points: List<DoubleArray>, unit: UnitSystem): Recording {
            val micros = System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000
            val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(micros / 1000))
            val createdAt = base + (micros % 1000).toString().padStart(3, '0')
            return Recording(micros.toString(), createdAt, points, unit)
        }
    }
}

private fun distance(a: DoubleArray, b: DoubleArray): Double {
    val dx = a[0] - b[0]
    val dy = a[1] - b[1]
    val dz = a[2] - b[2]
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/**
 * Plain text for Copy and Share (SPEC.md §2.7), built in memory and never written to
 * storage: per recording its number, date, unit, each segment's length and the total, in
 * the current [units]; recordings are separated by a blank line.
 */
fun List<Recording>.toShareText(units: UnitSystem): String = buildString {
    this@toShareText.forEachIndexed { i, r ->
        if (i > 0) append("\n\n")
        append("#${i + 1} · ${r.headingTime}\n")
        append("Unit: ${if (units == UnitSystem.Metric) "metric" else "imperial"}\n")
        r.segments.forEachIndexed { j, length -> append("${j + 1}. ${DistanceFormat.format(length, units)}\n") }
        append("Total: ${DistanceFormat.format(r.total, units)}")
    }
}

/** "N points · M segments" under the sheet's total. */
fun Recording.countsLine(): String = "${points.size} points · ${segments.size} segments"
