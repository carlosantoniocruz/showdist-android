package com.showconfigs.showdist.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The recording's shape in a 44×44 box (SPEC.md §2.8): the polyline flattened onto its two
 * widest axes, with world-up drawn as up, its points as dots.
 */
@Composable
fun ShapeThumbnail(recording: Recording, color: Color, modifier: Modifier = Modifier) {
    val unit = remember(recording.id) { flatten(recording.points) }
    Canvas(modifier.size(44.dp)) {
        val pad = 7.dp.toPx()
        val side = size.minDimension - 2 * pad
        fun at(i: Int) = Offset(pad + unit[i * 2] * side, pad + unit[i * 2 + 1] * side)

        val n = unit.size / 2
        if (n >= 2) {
            val path = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until n) lineTo(at(i).x, at(i).y)
            }
            drawPath(
                path,
                color.copy(alpha = 0.7f),
                style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        for (i in 0 until n) drawCircle(color, 2.6.dp.toPx(), at(i))
    }
}

/** Points normalised into the unit square: u at 2i, v at 2i+1 (v grows downward). */
internal fun flatten(points: List<DoubleArray>): FloatArray {
    val out = FloatArray(points.size * 2)
    if (points.isEmpty()) return out
    val min = DoubleArray(3) { a -> points.minOf { it[a] } }
    val range = DoubleArray(3) { a -> points.maxOf { it[a] } - min[a] }

    // The two widest axes. If world-up (y = 1) is one, it's vertical; otherwise (seen from
    // above, like a floor) the wider one is horizontal.
    val widest = (0..2).sortedByDescending { range[it] }.take(2)
    val (h, v) = if (1 in widest) Pair(widest.first { it != 1 }, 1) else Pair(widest[0], widest[1])

    val span = maxOf(range[h], range[v])
    for (i in points.indices) {
        if (span < 1e-9) {
            out[i * 2] = 0.5f
            out[i * 2 + 1] = 0.5f
        } else {
            out[i * 2] = (0.5 + (points[i][h] - min[h] - range[h] / 2) / span).toFloat()
            out[i * 2 + 1] = (0.5 - (points[i][v] - min[v] - range[v] / 2) / span).toFloat()
        }
    }
    return out
}
