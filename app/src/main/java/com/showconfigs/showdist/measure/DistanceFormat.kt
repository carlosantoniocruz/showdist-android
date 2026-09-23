package com.showconfigs.showdist.measure

import kotlin.math.roundToLong

enum class UnitSystem { Imperial, Metric }

/**
 * Distance formatting (SPEC.md §2.5). Integer arithmetic keeps it locale-independent
 * and rounds half away from zero (inputs are never negative).
 */
object DistanceFormat {
    private const val METRES_PER_INCH = 0.0254

    fun format(metres: Double, units: UnitSystem): String = when (units) {
        UnitSystem.Metric -> formatMetric(metres)
        UnitSystem.Imperial -> formatImperial(metres)
    }

    /**
     * A number that changes exactly when [format]'s output would change, so callers can
     * skip reformatting (and recomposing) while tracking jitter stays below the display step.
     */
    fun displayKey(metres: Double, units: UnitSystem): Long = when (units) {
        UnitSystem.Imperial -> halfInches(metres)
        UnitSystem.Metric -> when {
            metres < 0.1 -> (metres * 1000).roundToLong()
            metres < 1.0 -> 1_000_000 + (metres * 100).roundToLong()
            else -> 2_000_000 + (metres * 100).roundToLong()
        }
    }

    private fun formatMetric(m: Double): String = when {
        m < 0.1 -> {
            val tenths = (m * 1000).roundToLong()
            "${tenths / 10}.${tenths % 10} cm"
        }
        m < 1.0 -> "${(m * 100).roundToLong()} cm"
        else -> {
            val hundredths = (m * 100).roundToLong()
            val frac = hundredths % 100
            "${hundredths / 100}.${if (frac < 10) "0" else ""}$frac m"
        }
    }

    private fun formatImperial(m: Double): String {
        val halves = halfInches(m)
        val wholeInches = halves / 2
        val feet = wholeInches / 12
        val rem = wholeInches % 12
        val inches = if (halves % 2 == 1L) "$rem 1/2″" else "$rem″"
        return if (feet > 0) "$feet′ $inches" else inches
    }

    private fun halfInches(m: Double): Long = (m / METRES_PER_INCH * 2).roundToLong().coerceAtLeast(0)
}
