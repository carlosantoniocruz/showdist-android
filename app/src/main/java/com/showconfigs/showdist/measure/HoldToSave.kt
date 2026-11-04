package com.showconfigs.showdist.measure

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.showconfigs.showdist.history.Recording
import com.showconfigs.showdist.history.RecordingStore
import com.showconfigs.showdist.ui.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Long-press anywhere to charge a save (SPEC.md §2.6). After the standard long-press delay
 * [onChargeStart] runs if [canCharge]; [onRelease] runs when the finger lifts or the
 * gesture is cancelled. Taps and short presses pass straight through to the controls
 * underneath. Once charging, the gesture takes every event (in the Initial pass, before
 * children see them), so a long-press on the main button doesn't also place a point.
 */
fun Modifier.holdToSave(
    canCharge: () -> Boolean,
    onRefused: () -> Unit,
    onChargeStart: () -> Unit,
    onRelease: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // Null means the long-press delay passed with the finger still down and still.
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            awaitLiftOrMove(down.id, viewConfiguration.touchSlop)
        }
        if (endedEarly != null) return@awaitEachGesture
        if (!canCharge()) {
            onRefused()
            return@awaitEachGesture
        }

        onChargeStart()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            // Also runs if the gesture is cancelled, e.g. the screen leaves composition.
            onRelease()
        }
    }
}

/** Returns once the pointer lifts, disappears, or moves further than [slop] in total. */
private suspend fun AwaitPointerEventScope.awaitLiftOrMove(id: PointerId, slop: Float) {
    var moved = 0f
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        moved += change.positionChange().getDistance()
        if (moved > slop) return
    }
}

private const val CHARGE_MS = 800
private const val REWIND_MS = 200

/** Build-up pulses while charging: every 160 ms at first, every 60 ms near the end. */
private const val PULSE_FIRST_MS = 160f
private const val PULSE_LAST_MS = 60f

/** The hold-to-save gesture for the whole Measure screen, and its charge (0..1). */
class HoldToSaveState internal constructor(private val animatable: Animatable<Float, *>, val modifier: Modifier) {
    /** Read only while drawing, so the charging arc redraws without recomposing. */
    fun charge(): Float = animatable.value
}

/**
 * Hold-to-save (SPEC.md §2.6): with at least 2 points and tracking, a long-press charges
 * linearly over 800 ms, with haptic pulses that quicken and strengthen as it fills; lifting
 * early stops them at once (one very light tick), rewinds over 200 ms and saves nothing.
 * Holding with points while tracking is lost doesn't charge: a soft double tick says "not
 * ready". On completion: a crisp confirming tap, the points are saved as a Recording, the
 * [onComplete] does the saving: a chain is stored and its sheet shown, a group asks for its
 * name (SPEC.md §2.13). [ready] says whether there's anything to save.
 * Haptics run in the gesture's coroutines, never in the render loop.
 */
@Composable
fun rememberHoldToSave(
    tracker: MeasureTracker,
    enabled: Boolean,
    ready: () -> Boolean,
    onComplete: () -> Unit,
): HoldToSaveState {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentReady by rememberUpdatedState(ready)
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val currentOnComplete by rememberUpdatedState(onComplete)
    return remember(tracker) {
        val charge = Animatable(0f)
        var job: Job? = null
        var pulses: Job? = null
        var completed = false
        val gesture = Modifier.holdToSave(
            canCharge = { currentEnabled && currentReady() && tracker.tracking },
            onRefused = { if (currentEnabled && currentReady()) haptics.notReady() },
            onChargeStart = {
                completed = false
                pulses = scope.launch {
                    while (true) {
                        val c = charge.value
                        haptics.pulse(0.15f + 0.55f * c)
                        delay((PULSE_FIRST_MS - (PULSE_FIRST_MS - PULSE_LAST_MS) * c).toLong())
                    }
                }
                job = scope.launch {
                    val remaining = ((1f - charge.value) * CHARGE_MS).toInt()
                    charge.animateTo(1f, tween(remaining, easing = LinearEasing))
                    completed = true
                    pulses?.cancel()
                    haptics.confirm()
                    charge.snapTo(0f)
                    currentOnComplete()
                }
            },
            onRelease = {
                pulses?.cancel()
                if (!completed) {
                    haptics.cancel()
                    haptics.pulse(0f)
                    job?.cancel()
                    scope.launch { charge.animateTo(0f, tween(REWIND_MS, easing = LinearEasing)) }
                }
            },
        )
        HoldToSaveState(charge, gesture)
    }
}
