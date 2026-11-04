package com.showconfigs.showdist.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem

/** One History item (SPEC.md §1.9): a chained measurement or a group of lines. */
sealed interface HistoryEntry {
    val id: String

    class Chain(val recording: Recording) : HistoryEntry {
        override val id: String get() = recording.id
    }

    class Group(val group: MeasureGroup) : HistoryEntry {
        override val id: String get() = group.id
    }
}

/** Chains and groups together, newest first (both ids are creation times in microseconds). */
fun historyEntries(recordings: List<Recording>, groups: List<MeasureGroup>): List<HistoryEntry> =
    (recordings.map { HistoryEntry.Chain(it) } + groups.map { HistoryEntry.Group(it) })
        .sortedByDescending { it.id.toLongOrNull() ?: 0L }

/** Share and Copy text for a mix of chains and groups, separated by blank lines. */
fun List<HistoryEntry>.toShareText(units: UnitSystem): String {
    var chainNumber = 0
    return joinToString("\n\n") { e ->
        when (e) {
            is HistoryEntry.Chain -> {
                chainNumber++
                val r = e.recording
                buildString {
                    append("#$chainNumber · ${r.headingTime}\n")
                    append("Unit: ${if (units == UnitSystem.Metric) "metric" else "imperial"}\n")
                    r.segments.forEachIndexed { j, length -> append("${j + 1}. ${DistanceFormat.format(length, units)}\n") }
                    append("Total: ${DistanceFormat.format(r.total, units)}")
                }
            }
            is HistoryEntry.Group -> e.group.toShareText(units)
        }
    }
}

/**
 * A group's lines in a 44×44 box (SPEC.md §2.8, §2.13): each line on its own, flattened like a
 * chain's thumbnail, inside a thin rounded frame, so groups and chains look different.
 */
@Composable
fun GroupThumbnail(group: MeasureGroup, color: Color, modifier: Modifier = Modifier) {
    val unit = remember(group.id, group.lines.size) { flatten(group.lines.flatMap { listOf(it.a, it.b) }) }
    Canvas(modifier.size(44.dp)) {
        val frame = 1.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.55f),
            topLeft = Offset(frame / 2, frame / 2),
            size = Size(size.width - frame, size.height - frame),
            cornerRadius = CornerRadius(8.dp.toPx()),
            style = Stroke(frame),
        )
        val pad = 9.dp.toPx()
        val side = size.minDimension - 2 * pad
        fun at(i: Int) = Offset(pad + unit[i * 2] * side, pad + unit[i * 2 + 1] * side)
        for (l in 0 until unit.size / 4) {
            drawLine(color.copy(alpha = 0.8f), at(l * 2), at(l * 2 + 1), 1.4.dp.toPx(), StrokeCap.Round)
            drawCircle(color, 2.dp.toPx(), at(l * 2))
            drawCircle(color, 2.dp.toPx(), at(l * 2 + 1))
        }
    }
}
