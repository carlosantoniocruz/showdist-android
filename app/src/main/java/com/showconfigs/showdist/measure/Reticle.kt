package com.showconfigs.showdist.measure

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette

private val DOT_RADIUS = 3.dp
private val BACKING = 1.25.dp
private val RIM = 1.5.dp
private const val LOCK_MS = 160

/**
 * The reticle (SPEC.md §3.1), drawn at the centre of [modifier]'s area: one small white
 * dot, always there, on a thin, crisp near-black backing so it shows on bright walls, white
 * paper and in sunlight. Once a point can be placed here (a surface under the dot, and
 * tracking warmed up), a thin cyan rim fades in tight round the dot, inside the backing:
 * locked and ready. A plain white dot means a tap won't place a point yet; the tracking
 * pill says why. No separate ring, no blur, no glow, nothing spinning.
 *
 * It draws three circles and redraws only while the lock fades or its state changes; the
 * animated value is read in the draw phase, so nothing recomposes.
 */
@Composable
fun Reticle(tracker: MeasureTracker, modifier: Modifier = Modifier) {
    val lock = animateFloatAsState(if (tracker.canPlace) 1f else 0f, tween(LOCK_MS), label = "reticleLock")
    Spacer(
        modifier.size(16.dp).drawBehind {
            val k = lock.value
            val dot = DOT_RADIUS.toPx()
            val rim = RIM.toPx() * k
            drawCircle(Palette.Backing, dot + rim + BACKING.toPx())
            if (k > 0f) drawCircle(Palette.Cyan, dot + rim)
            drawCircle(Palette.White, dot)
        },
    )
}
