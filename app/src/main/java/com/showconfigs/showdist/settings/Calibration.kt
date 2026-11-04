package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PREFS = "showdist"
private const val KEY_SCALE = "showdist.calibration.scale"
private const val KEY_LEVEL_X = "showdist.calibration.level_x"
private const val KEY_LEVEL_Y = "showdist.calibration.level_y"

/** A correction outside this range is a mis-measurement, not a calibration (SPEC.md §1.5a). */
const val MIN_SCALE = 0.85
const val MAX_SCALE = 1.15

/** Something of known length to calibrate against. [metres] is its long edge. */
enum class KnownObject(val label: String, val what: String, val metres: Double?) {
    Letter("Letter paper, long edge (11 in)", "the sheet's long edge", 0.2794),
    A4("A4 paper, long edge (297 mm)", "the sheet's long edge", 0.297),
    DollarBill("US dollar bill, long edge (156 mm)", "the bill's long edge", 0.156),
    CreditCard("Credit card, long edge (85.6 mm)", "the card's long edge", 0.0856),
    Custom("Something else I've measured", "the object", null),
}

/** What a calibration attempt came to, shown in plain words. */
sealed interface ScaleResult {
    val message: String

    class Fixed(errorPercent: Double) : ScaleResult {
        override val message: String = when {
            abs(errorPercent) < 0.5 -> "Your measurements were already spot on."
            else -> "Your measurements were ${abs(errorPercent).roundPercent()}% ${if (errorPercent < 0) "short" else "long"}. Fixed."
        }
    }

    class Rejected(errorPercent: Double) : ScaleResult {
        override val message: String =
            "That reading was ${abs(errorPercent).roundPercent()}% off, too far to be right. Check the two ends and try again."
    }
}

private fun Double.roundPercent(): String =
    if (this < 10) String.format(java.util.Locale.US, "%.1f", this).removeSuffix(".0") else roundToInt().toString()

/**
 * The calibration values (SPEC.md §1.5a), persisted and read everywhere they apply:
 * [scale] multiplies every new measurement's lengths (live, saved, History, PDF), and the
 * level offsets are subtracted from the accelerometer before either level reads it.
 */
object Calibration {
    var scale by mutableDoubleStateOf(1.0)
        private set
    var levelX by mutableFloatStateOf(0f)
        private set
    var levelY by mutableFloatStateOf(0f)
        private set

    /** The object picked for the next scale measurement, and its true length in metres. */
    var target by mutableStateOf<Pair<KnownObject, Double>?>(null)

    /** The last scale attempt's outcome, shown on the scale screen until it's left. */
    var lastResult by mutableStateOf<ScaleResult?>(null)

    val hasScale: Boolean get() = scale != 1.0
    val hasLevelZero: Boolean get() = levelX != 0f || levelY != 0f

    fun load(context: Context) {
        val p = context.prefs()
        scale = p.getFloat(KEY_SCALE, 1f).toDouble().takeIf { it in MIN_SCALE..MAX_SCALE } ?: 1.0
        levelX = p.getFloat(KEY_LEVEL_X, 0f)
        levelY = p.getFloat(KEY_LEVEL_Y, 0f)
    }

    /**
     * Applies a scale measurement: [rawMetres] is what the app measured with no correction,
     * [trueMetres] the object's real length. Wild results are rejected and change nothing.
     */
    fun calibrateScale(context: Context, rawMetres: Double, trueMetres: Double): ScaleResult {
        val errorPercent = (rawMetres - trueMetres) / trueMetres * 100
        val factor = trueMetres / rawMetres
        val result = if (rawMetres > 0 && factor in MIN_SCALE..MAX_SCALE) {
            scale = factor
            context.prefs().edit { putFloat(KEY_SCALE, factor.toFloat()) }
            ScaleResult.Fixed(errorPercent)
        } else {
            ScaleResult.Rejected(errorPercent)
        }
        lastResult = result
        return result
    }

    fun resetScale(context: Context) {
        scale = 1.0
        lastResult = null
        context.prefs().edit { remove(KEY_SCALE) }
    }

    /** Stores the accelerometer's x/g and y/g read lying flat as the level's zero. */
    fun setLevelZero(context: Context, x: Float, y: Float) {
        levelX = x
        levelY = y
        context.prefs().edit { putFloat(KEY_LEVEL_X, x).putFloat(KEY_LEVEL_Y, y) }
    }

    fun resetLevelZero(context: Context) {
        levelX = 0f
        levelY = 0f
        context.prefs().edit { remove(KEY_LEVEL_X).remove(KEY_LEVEL_Y) }
    }

    private fun Context.prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
