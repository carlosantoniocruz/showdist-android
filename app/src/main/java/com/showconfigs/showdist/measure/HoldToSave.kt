package com.showconfigs.showdist.measure

import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.platform.LocalView
import com.showconfigs.showdist.history.Recording
import com.showconfigs.showdist.history.RecordingStore
import kotlinx.coroutines.Job
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
    onChargeStart: () -> Unit,
    onRelease: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // Null means the long-press delay passed with the finger still down and still.
        val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            awaitLiftOrMove(down.id, viewConfiguration.touchSlop)
        }
        if (endedEarly != null || !canCharge()) return@awaitEachGesture

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

/** The hold-to-save gesture for the whole Measure screen, and its charge (0..1). */
class HoldToSaveState internal constructor(private val animatable: Animatable<Float, *>, val modifier: Modifier) {
    /** Read only while drawing, so the charging arc redraws without recomposing. */
    fun charge(): Float = animatable.value
}

/**
 * Hold-to-save (SPEC.md §2.6): with at least 2 points, a long-press charges linearly over
 * 800 ms; lifting early rewinds over 200 ms and saves nothing. On completion: a medium
 * haptic, the points are saved as a Recording, the anchors cleared (the session keeps
 * running), and [onSaved] shows the "SAVED" sheet.
 */
@Composable
fun rememberHoldToSave(tracker: MeasureTracker, onSaved: (Recording) -> Unit): HoldToSaveState {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentOnSaved by rememberUpdatedState(onSaved)
    return remember(tracker) {
        val charge = Animatable(0f)
        var job: Job? = null
        var completed = false
        val gesture = Modifier.holdToSave(
            canCharge = { tracker.pointCount >= 2 },
            onChargeStart = {
                // Flutter's selectionClick and mediumImpact map to these constants.
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                completed = false
                job = scope.launch {
                    val remaining = ((1f - charge.value) * CHARGE_MS).toInt()
                    charge.animateTo(1f, tween(remaining, easing = LinearEasing))
                    completed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val recording = Recording.create(tracker.snapshotPoints())
                    tracker.clear()
                    RecordingStore.add(recording)
                    charge.snapTo(0f)
                    currentOnSaved(recording)
                }
            },
            onRelease = {
                if (!completed) {
                    job?.cancel()
                    scope.launch { charge.animateTo(0f, tween(REWIND_MS, easing = LinearEasing)) }
                }
            },
        )
        HoldToSaveState(charge, gesture)
    }
}
