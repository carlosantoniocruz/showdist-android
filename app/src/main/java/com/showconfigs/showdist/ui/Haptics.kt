package com.showconfigs.showdist.ui

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptics that can build in strength (SPEC.md §6.4), with fallbacks for simpler motors:
 * 1. rich primitives (API 30+, if the motor supports them), scaled by strength;
 * 2. short one-shots with amplitude control (API 26+);
 * 3. the view's standard haptic constants, lighter or heavier by strength.
 * Nothing plays when the system's touch feedback is off. Calls are cheap and never made
 * from a render loop: callers fire them from gesture or animation coroutines.
 */
class Haptics(private val view: View) {
    private val vibrator: Vibrator? = view.context.vibrator()
    private val primitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null &&
        vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
    private val amplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true

    private fun enabled(): Boolean =
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    /** A light pulse; [strength] 0..1. */
    fun pulse(strength: Float) {
        if (!enabled()) return
        val s = strength.coerceIn(0f, 1f)
        when {
            primitives -> play(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.25f + 0.75f * s)
                    .compose(),
            )
            amplitude -> play(VibrationEffect.createOneShot(12, (40 + 200 * s).toInt()))
            else -> view.performHapticFeedback(
                if (s < 0.5f) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
    }

    /** A crisp, distinct confirming tap, stronger than any [pulse]. */
    fun confirm() {
        if (!enabled()) return
        when {
            primitives -> play(
                VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f).compose(),
            )
            amplitude -> play(VibrationEffect.createOneShot(30, 255))
            else -> view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
            )
        }
    }

    /** Stops anything still playing. */
    fun cancel() {
        vibrator?.cancel()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun play(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect)
        }
    }
}

private fun Context.vibrator(): Vibrator? {
    val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return v?.takeIf { it.hasVibrator() }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}
