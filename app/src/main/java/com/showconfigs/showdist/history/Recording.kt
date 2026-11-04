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
 * [scale] is the measurement calibration in force when it was saved (SPEC.md §1.5a): the
 * points stay as ARCore placed them (so "View in AR" lines up), and every length is
 * multiplied by it. Recordings saved before calibration existed have 1.
 */
class Recording(
    val id: String,
    val createdAt: String,
    val points: List<DoubleArray>,
    val unit: UnitSystem? = null,
    storedTotal: Double? = null,
    val scale: Double = 1.0,
) {
    val segments: List<Double> = (1 until points.size).map { i -> distance(points[i - 1], points[i]) * scale }
    val total: Double = if (points.size >= 2 || storedTotal == null) segments.sum() else storedTotal

    /** "YYYY-MM-DD HH:MM", shown in shared text and the PDF report. */
    val headingTime: String get() = createdAt.take(16).replace('T', ' ')


    companion object {
        /** A new recording stamped with the current time and the current [unit] setting. */
        fun create(points: List<DoubleArray>, unit: UnitSystem, scale: Double): Recording {
            val micros = System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000
            val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(micros / 1000))
            val createdAt = base + (micros % 1000).toString().padStart(3, '0')
            return Recording(micros.toString(), createdAt, points, unit, scale = scale)
        }
    }
}

private fun distance(a: DoubleArray, b: DoubleArray): Double {
    val dx = a[0] - b[0]
    val dy = a[1] - b[1]
    val dz = a[2] - b[2]
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** "N points · M segments" under the sheet's total. */
fun Recording.countsLine(): String = "${points.size} points · ${segments.size} segments"
