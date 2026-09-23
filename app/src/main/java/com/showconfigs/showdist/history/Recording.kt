package com.showconfigs.showdist.history

import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

private const val TEXT_SEGMENT_LIMIT = 20

/**
 * A saved measurement (SPEC.md §2.7): an ordered polyline of world positions in metres.
 * [id] is the creation time in microseconds since the epoch; [createdAt] is an ISO-8601
 * local timestamp such as `2026-09-23T17:31:05.123456`.
 */
class Recording(val id: String, val createdAt: String, val points: List<DoubleArray>) {
    val segments: List<Double> = (1 until points.size).map { i -> distance(points[i - 1], points[i]) }
    val total: Double = segments.sum()

    /** "YYYY-MM-DD HH:MM", used as each block's heading in bulk text. */
    val headingTime: String get() = createdAt.take(16).replace('T', ' ')

    /** "YYYYMMDD-HHMMSS", used in export file names. */
    val fileTime: String get() = createdAt.take(19).filter { it.isDigit() }.let { "${it.take(8)}-${it.drop(8)}" }

    companion object {
        /** A new recording stamped with the current time. */
        fun create(points: List<DoubleArray>): Recording {
            val micros = System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000
            val base = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(micros / 1000))
            val createdAt = base + (micros % 1000).toString().padStart(3, '0')
            return Recording(micros.toString(), createdAt, points)
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
 * Single-recording text (SPEC.md §2.7), for the share file and clipboard. With [truncate],
 * segments past 20 are replaced by a "+ N more" line.
 */
fun Recording.toText(units: UnitSystem, truncate: Boolean = true): String = buildString {
    val s = segments.size
    appendLine("Showdist: ${DistanceFormat.format(total, units)}")
    append("${points.size} points, $s segments")
    val shown = if (truncate) minOf(s, TEXT_SEGMENT_LIMIT) else s
    for (i in 0 until shown) append("\n${i + 1}. ${DistanceFormat.format(segments[i], units)}")
    if (shown < s) append("\n+ ${s - shown} more in the attached file")
}

/** Text for several recordings; a single one uses the single format, untruncated. */
fun List<Recording>.toBulkText(units: UnitSystem): String {
    if (size == 1) return first().toText(units, truncate = false)
    return buildString {
        append("Showdist: $size measurements")
        for (r in this@toBulkText) append("\n\n${r.headingTime}\n${r.toText(units, truncate = false)}")
    }
}

/** "N points · M segments" under the sheet's total. */
fun Recording.countsLine(): String = "${points.size} points · ${segments.size} segments"
