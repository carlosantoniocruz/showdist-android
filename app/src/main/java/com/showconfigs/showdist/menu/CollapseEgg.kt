package com.showconfigs.showdist.menu

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// Timed like Android 16's own Easter egg (PlatLogoActivity): its warp ramps up over
// LAUNCH_TIME = 5 s of holding the logo, and the next stage launches 1 s after that.
private const val RAMP_MS = 5_000f
private const val TRIGGER_MS = 6_000f

/** Top spin, reached at the end of the ramp: four turns a second. */
private const val MAX_SPIN = 4f * 2f * PI.toFloat()

/** How far in the moons are drawn by the end of the ramp; the last second takes them home. */
private const val RAMP_PULL = 0.8f

private const val BURST_MS = 1_400f
private const val REFORM_MS = 900f
private const val RELAX_MS = 700f

private const val PREFS = "showdist"
private const val KEY_UNLOCKED = "showdist.fun_facts"

/**
 * The menu's hidden collapse (SPEC.md §1.4): hold the sun and the moons spin faster and
 * faster as they're drawn into it, until it bursts and the fun facts turn on (or, if
 * they're already on, off). Lifting early
 * lets them drift back out. [spin] (radians, added to every moon's angle), [pull] (0 on the
 * orbit, 1 at the sun's centre) and [burst] (explosion progress 0..1, below 0 when none)
 * are snapshot state, read while laying out and drawing.
 */
@Stable
class CollapseEgg(
    private val scope: CoroutineScope,
    private val view: View,
    private val reduceMotion: Boolean,
    private val onBurst: () -> Unit,
) {
    var spin by mutableFloatStateOf(0f)
        private set
    var pull by mutableFloatStateOf(0f)
        private set
    var burst by mutableFloatStateOf(-1f)
        private set

    /** The moons are hidden from the burst until they re-form. */
    var moonsHidden by mutableStateOf(false)
        private set

    private var speed = 0f
    private var job: Job? = null

    /** True while anything is happening, so the moons ignore taps. */
    val active: Boolean get() = pull > 0f || burst >= 0f || speed > 0f

    fun press() {
        if (burst >= 0f) return
        job?.cancel()
        job = scope.launch { charge() }
    }

    fun release() {
        if (burst >= 0f) return
        job?.cancel()
        job = scope.launch { relax() }
    }

    private suspend fun charge() {
        val startPull = pull
        val start = withFrameNanos { it }
        var last = start
        var nextTick = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1e9f
            last = now
            val t = (now - start) / 1e6f
            val p = (t / RAMP_MS).coerceAtMost(1f)
            if (!reduceMotion) {
                speed = MAX_SPIN * p * p
                spin += speed * dt
                val ramp = RAMP_PULL * p * p * (3f - 2f * p)
                val home = ((t - RAMP_MS) / (TRIGGER_MS - RAMP_MS)).coerceIn(0f, 1f)
                pull = maxOf(startPull * (1f - p), ramp + (1f - RAMP_PULL) * home * home)
            }
            // Ticks that quicken with the spin: every 250 ms at first, every 40 ms at the end.
            if (t >= nextTick) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                nextTick = t + 250f - 210f * p
            }
            if (t >= TRIGGER_MS) break
        }
        explode()
    }

    private suspend fun explode() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
        )
        onBurst()
        if (reduceMotion) return
        // Marked as bursting before the first frame, so a finger lifting now can't cancel it
        // halfway (that once left the moons hidden for good).
        burst = 0f
        moonsHidden = true
        pull = 1f
        speed = 0f
        try {
            val start = withFrameNanos { it }
            while (true) {
                val t = (withFrameNanos { it } - start) / 1e6f
                burst = (t / BURST_MS).coerceAtMost(1f)
                if (burst >= 1f) break
            }
        } finally {
            // However the burst ends, the moons come back.
            burst = -1f
            moonsHidden = false
        }
        // The moons re-form, flung back out to their orbit with one last turn.
        val fromSpin = spin
        val toSpin = nextWholeTurn(spin + 2f * PI.toFloat())
        val reform = withFrameNanos { it }
        while (true) {
            val p = ((withFrameNanos { it } - reform) / 1e6f / REFORM_MS).coerceAtMost(1f)
            val out = 1f - (1f - p) * (1f - p) * (1f - p)
            pull = 1f - out
            spin = fromSpin + (toSpin - fromSpin) * out
            if (p >= 1f) break
        }
        pull = 0f
        spin = 0f
    }

    /**
     * Spin and pull ease back to rest, as if the sun let go, the spin coasting on to the
     * next whole turn so every moon lands back in its own place.
     */
    private suspend fun relax() {
        val fromPull = pull
        val fromSpin = spin
        // Coast as far as the current speed would carry it while easing out, then round up.
        val toSpin = nextWholeTurn(spin + speed * RELAX_MS / 1000f / 3f)
        speed = 0f
        val start = withFrameNanos { it }
        while (true) {
            val p = ((withFrameNanos { it } - start) / 1e6f / RELAX_MS).coerceAtMost(1f)
            val out = 1f - (1f - p) * (1f - p) * (1f - p)
            pull = fromPull * (1f - out)
            spin = fromSpin + (toSpin - fromSpin) * out
            if (p >= 1f) break
        }
        pull = 0f
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

/** The burst's sparks: fixed directions, speeds, sizes and colours, made once. */
class BurstSparks(count: Int = 56) {
    private val random = Random(1961)
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val reach = FloatArray(count) { 0.35f + random.nextFloat() * 0.65f }
    val size = FloatArray(count) { 1.5f + random.nextFloat() * 3f }
    val hot = BooleanArray(count) { random.nextFloat() < 0.4f }

    init {
        for (i in 0 until count) {
            val a = (i + random.nextFloat() * 0.8f) / count * 2f * PI.toFloat()
            dx[i] = cos(a)
            dy[i] = sin(a)
        }
    }
}

/**
 * The burst at [burst] (0..1) from the sun at [c], radius [sunR]: a white flash, a sandstone
 * shockwave running out past the screen's corners, and sparks flung outwards.
 */
fun DrawScope.drawBurst(burst: Float, c: Offset, sunR: Float, sparks: BurstSparks) {
    val far = hypot(size.width, size.height)
    val out = 1f - (1f - burst) * (1f - burst)
    val flash = (1f - burst * 3f).coerceAtLeast(0f)
    if (flash > 0f) drawCircle(Palette.White.copy(alpha = 0.9f * flash), sunR * (1f + 3f * out), c)
    drawCircle(
        Palette.Sandstone.copy(alpha = 1f - burst),
        far * out,
        c,
        style = Stroke((10.dp.toPx() * (1f - burst)).coerceAtLeast(1f)),
    )
    val fade = 1f - burst
    for (i in sparks.reach.indices) {
        val d = sunR * 0.6f + far * 0.5f * sparks.reach[i] * out
        drawCircle(
            (if (sparks.hot[i]) Palette.White else Palette.Sandstone).copy(alpha = fade),
            sparks.size[i].dp.toPx() * (1f - 0.5f * burst),
            Offset(c.x + sparks.dx[i] * d, c.y + sparks.dy[i] * d),
        )
    }
}

/** Historical fun facts the collapse unlocks, scrolling along the menu's top row. */
private val FunFacts = listOf(
    "In 1791 France defined the metre as one ten-millionth of the distance from the North Pole to the equator, along the meridian through Paris.",
    "Since 1983 the metre has been the distance light travels in a vacuum in 1/299,792,458 of a second.",
    "In 1959 the international foot was fixed at exactly 0.3048 metres.",
    "The US survey foot, two parts per million longer, was retired at the end of 2022.",
    "Ancient Egypt's royal cubit, based on the forearm, measured about 52 cm.",
    "Around 240 BC Eratosthenes estimated Earth's circumference by comparing the noon Sun's angle at Syene and Alexandria.",
    "Melchisédech Thévenot is credited with inventing the bubble level around 1661.",
    "In 1999 the Mars Climate Orbiter was lost when one team used pound-force seconds and another newton-seconds.",
    "Apollo 11, 14 and 15 left mirrors on the Moon; lasers bounced off them measure its distance to the millimetre.",
    "Those lunar lasers show the Moon drifting about 3.8 cm farther from Earth every year.",
    "Sunlight takes about 8 minutes 20 seconds to reach Earth.",
    "Since 2012 the astronomical unit has been exactly 149,597,870,700 metres.",
    "From 1889 to 1960 the metre was the length of a platinum-iridium bar kept near Paris.",
    "In 1960 the metre was redefined as 1,650,763.73 wavelengths of light from krypton-86.",
    "Since 1959 the inch has been exactly 2.54 centimetres.",
    "The international nautical mile is exactly 1,852 metres, close to one minute of latitude.",
    "The mile comes from the Roman mille passus: a thousand paces, each pace two steps.",
    "The furlong, 220 yards, takes its name from the length of a furrow in a ploughed field.",
    "Edmund Gunter's 1620 surveying chain is 66 feet long; ten square chains make an acre.",
    "In 1615 Willebrord Snellius began measuring the Earth by triangulation across the Netherlands.",
    "In 2019 the kilogram was redefined by the Planck constant, retiring a metal cylinder kept since 1889.",
    "Light travels about 30 cm in a nanosecond; Grace Hopper handed out wires that long to show it.",
    "A light-year is about 9.46 trillion kilometres; a parsec is about 3.26 light-years.",
    "In 2020 China and Nepal jointly measured Mount Everest at 8,848.86 metres.",
)

/**
 * Whether the fun facts are shown, persisted so they stay once found. Each completed
 * collapse [toggle]s them: the first turns them on, the next off again.
 */
class FunFactsUnlock(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var unlocked by mutableStateOf(prefs.getBoolean(KEY_UNLOCKED, false))
        private set

    fun toggle() {
        unlocked = !unlocked
        prefs.edit { putBoolean(KEY_UNLOCKED, unlocked) }
    }
}

@Composable
fun rememberFunFactsUnlock(): FunFactsUnlock {
    val context = LocalContext.current
    return remember { FunFactsUnlock(context) }
}

/** The facts, one after another, scrolling right to left without end. */
@Composable
fun FunFactsTicker(modifier: Modifier = Modifier) {
    val text = remember { FunFacts.joinToString(separator = "     ✦     ", postfix = "     ✦     ") }
    Text(
        text,
        modifier = modifier
            .semantics { contentDescription = "Fun facts" }
            .basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 600, repeatDelayMillis = 0, velocity = 40.dp),
        maxLines = 1,
        style = Type.Secondary.copy(color = Palette.Sandstone),
    )
}
