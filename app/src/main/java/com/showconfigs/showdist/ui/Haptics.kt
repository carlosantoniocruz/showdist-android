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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit

private const val PREFS = "showdist"
private const val KEY_HAPTICS = "showdist.haptics"

/** The app's own Vibration setting (SPEC.md §1.5): on by default, persisted. */
object HapticsSetting {
    var enabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        enabled = context.prefs().getBoolean(KEY_HAPTICS, true)
    }

    fun set(context: Context, value: Boolean) {
        enabled = value
        context.prefs().edit { putBoolean(KEY_HAPTICS, value) }
    }

    private fun Context.prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Haptics that can build in strength (SPEC.md §6.4), with fallbacks for simpler motors:
 * 1. rich primitives (API 30+, if the motor supports them), scaled by strength;
 * 2. short one-shots with amplitude control (API 26+);
 * 3. the view's standard haptic constants, lighter or heavier by strength.
 * Nothing plays when the app's Vibration setting ([HapticsSetting]) or the system's touch
 * feedback is off. Calls are cheap and never made
 * from a render loop: callers fire them from gesture or animation coroutines.
 */
class Haptics(private val view: View) {
    private val vibrator: Vibrator? = view.context.vibrator()
    private val primitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator != null &&
        vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
    private val amplitude = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasAmplitudeControl() == true

    private fun enabled(): Boolean = HapticsSetting.enabled &&
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    /** The standard light tick for taps and small events (selection click). */
    fun tick() {
        if (enabled()) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    /** A short, soft double tick: "not ready". */
    fun notReady() {
        if (!enabled()) return
        when {
            primitives -> play(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f, 90)
                    .compose(),
            )
            amplitude -> play(VibrationEffect.createWaveform(longArrayOf(0, 10, 90, 10), intArrayOf(0, 70, 0, 70), -1))
            else -> {
                tick()
                view.postDelayed({ tick() }, 100)
            }
        }
    }

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
