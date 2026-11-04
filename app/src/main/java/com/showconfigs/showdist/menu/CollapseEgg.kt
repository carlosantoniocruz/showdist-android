package com.showconfigs.showdist.menu

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

// Timed like Android 16's own Easter egg (PlatLogoActivity): about 6 s of holding the logo.
private const val HOLD_MS = 6_000f

/** Top spin, reached at collision: four turns a second. */
private const val MAX_SPIN = 4f * 2f * PI.toFloat()

/** Lifting early: everything eases back over this long. */
private const val RELAX_MS = 700f

/**
 * The menu's hidden collapse (SPEC.md §1.4): hold the sun and the moons spin faster and
 * spiral in as the sun's glow builds to white, until they collide, the scene flashes white
 * and [onCollide] plays the fun facts. Lifting early eases everything back.
 *
 * Everything is driven by one value, [progress] (0 at rest, 1 at collision), plus the
 * accumulated [spin] angle. [pull] (0 on the orbit, 1 at the sun's centre) and [glow]
 * (0 the sun's own colour, 1 white) follow from it. All are snapshot state, read only in
 * layout, layer and draw lambdas, so nothing recomposes per frame.
 */
@Stable
class CollapseEgg(
    private val scope: CoroutineScope,
    private val haptics: Haptics,
    private val reduceMotion: Boolean,
    private val onCollide: () -> Unit,
) {
    var progress by mutableFloatStateOf(0f)
        private set
    var spin by mutableFloatStateOf(0f)
        private set

    /** How far in the moons are: a smoothstep of [progress], so they spiral in gently, then fast. */
    val pull: Float get() = if (reduceMotion) 0f else progress * progress * (3f - 2f * progress)

    /** The sun's glow and the moons' light, tied to how close the moons are. */
    val glow: Float get() = pull

    private var speed = 0f
    private var colliding = false
    private var job: Job? = null

    /** True while anything is happening, so the moons ignore taps. */
    val active: Boolean get() = progress > 0f || speed > 0f

    fun press() {
        if (colliding) return
        job?.cancel()
        job = scope.launch { charge() }
    }

    fun release() {
        if (colliding) return
        job?.cancel()
        haptics.cancel()
        job = scope.launch { relax() }
    }

    private suspend fun charge() {
        val startProgress = progress
        val start = withFrameNanos { it }
        var last = start
        var nextPulse = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f
            last = now
            val t = (now - start) / 1e6f
            // Picks up from wherever an early release left it.
            val p = (startProgress + t / HOLD_MS).coerceAtMost(1f)
            progress = p
            if (!reduceMotion) {
                speed = MAX_SPIN * p * p
                spin += speed * dt
            }
            // A pulse that quickens and strengthens as the moons close in: every 250 ms at
            // first, every 40 ms at the end.
            if (t >= nextPulse) {
                haptics.pulse(p)
                nextPulse = t + 250f - 210f * p
            }
            if (p >= 1f) break
        }
        collide()
    }

    /** The moons meet: a final tap, the facts take over (their scene flashes white), and the orbit resets behind it. */
    private suspend fun collide() {
        colliding = true
        haptics.confirm()
        onCollide()
        // The facts scene is opaque within a few frames; reset the orbit unseen behind it.
        delay(300)
        progress = 0f
        spin = 0f
        speed = 0f
        colliding = false
    }

    /**
     * Progress eases back to rest, as if the sun let go, the spin coasting on to the next
     * whole turn so every moon lands back in its own place. The glow follows progress back.
     */
    private suspend fun relax() {
        val fromProgress = progress
        val fromSpin = spin
        // Coast as far as the current speed would carry it while easing out, then round up.
        val toSpin = nextWholeTurn(spin + speed * RELAX_MS / 1000f / 3f)
        speed = 0f
        val start = withFrameNanos { it }
        while (true) {
            val p = ((withFrameNanos { it } - start) / 1e6f / RELAX_MS).coerceAtMost(1f)
            val out = 1f - (1f - p) * (1f - p) * (1f - p)
            progress = fromProgress * (1f - out)
            spin = fromSpin + (toSpin - fromSpin) * out
            if (p >= 1f) break
        }
        progress = 0f
        spin = 0f
    }

    /** The first whole turn at or after [angle], where the moons sit as they did at rest. */
    private fun nextWholeTurn(angle: Float): Float {
        val turn = 2f * PI.toFloat()
        return kotlin.math.ceil(angle / turn) * turn
    }
}

/** How far a finger may wander once the press is clearly a hold. */
private val HOLD_DRIFT = 48.dp

/**
 * Hold-to-collapse on the sun: pressing starts the charge, lifting or dragging away ends it.
 * Movement is measured as the finger's net distance from where it went down, not the sum of
 * every report, since some touchscreens (Samsung's, for one) report a resting finger as a
 * constant jitter that adds up past the touch slop within a second. Until the long-press
 * timeout a move past the slop still hands the gesture to the orbit's swipe; after it, the
 * hold takes every event so the orbit can't steal it, and allows [HOLD_DRIFT] of wander.
 */
fun Modifier.collapseOnHold(egg: CollapseEgg) = pointerInput(egg) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        egg.press()
        val drift = HOLD_DRIFT.toPx()
        var holding = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val wander = (change.position - down.position).getDistance()
            if (!holding) {
                // A swipe that starts on the sun turns the orbit instead.
                if (wander > viewConfiguration.touchSlop) break
                holding = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
            }
            if (holding) {
                if (wander > drift) break
                change.consume()
            }
        }
        egg.release()
    }
}
