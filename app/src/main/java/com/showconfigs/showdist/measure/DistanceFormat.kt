package com.showconfigs.showdist.measure

import kotlin.math.roundToLong

enum class UnitSystem { Imperial, Metric }

/** Big enough for any formatted distance, e.g. "9999′ 11 1/2″". */
const val DISTANCE_TEXT_CAPACITY = 24

/**
 * Distance formatting (SPEC.md §2.5). Integer arithmetic keeps it locale-independent
 * and rounds half away from zero (inputs are never negative).
 */
object DistanceFormat {
    private const val METRES_PER_INCH = 0.0254

    fun format(metres: Double, units: UnitSystem): String {
        val buf = CharArray(DISTANCE_TEXT_CAPACITY)
        return String(buf, 0, formatInto(metres, units, buf))
    }

    /**
     * Writes the formatted distance into [out] and returns its length. Allocation-free,
     * for per-frame labels.
     */
    fun formatInto(metres: Double, units: UnitSystem, out: CharArray): Int = when (units) {
        UnitSystem.Metric -> metricInto(metres, out)
        UnitSystem.Imperial -> imperialInto(metres, out)
    }

    /**
     * A number that changes exactly when the formatted text would change, so callers can
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

    private fun metricInto(m: Double, out: CharArray): Int {
        var p: Int
        when {
            m < 0.1 -> {
                val tenths = (m * 1000).roundToLong()
                p = putLong(out, 0, tenths / 10)
                out[p++] = '.'
                out[p++] = '0' + (tenths % 10).toInt()
                p = putText(out, p, " cm")
            }
            m < 1.0 -> {
                p = putLong(out, 0, (m * 100).roundToLong())
                p = putText(out, p, " cm")
            }
            else -> {
                val hundredths = (m * 100).roundToLong()
                val frac = (hundredths % 100).toInt()
                p = putLong(out, 0, hundredths / 100)
                out[p++] = '.'
                out[p++] = '0' + frac / 10
                out[p++] = '0' + frac % 10
                p = putText(out, p, " m")
            }
        }
        return p
    }

    private fun imperialInto(m: Double, out: CharArray): Int {
        val halves = halfInches(m)
        val wholeInches = halves / 2
        val feet = wholeInches / 12
        var p = 0
        if (feet > 0) {
            p = putLong(out, p, feet)
            out[p++] = '′'
            out[p++] = ' '
        }
        p = putLong(out, p, wholeInches % 12)
        if (halves % 2 == 1L) p = putText(out, p, " 1/2")
        out[p++] = '″'
        return p
    }

    private fun halfInches(m: Double): Long = (m / METRES_PER_INCH * 2).roundToLong().coerceAtLeast(0)

    private fun putLong(out: CharArray, start: Int, value: Long): Int {
        var digits = 1
        var rest = value / 10
        while (rest > 0) {
            digits++
            rest /= 10
        }
        var v = value
        for (i in start + digits - 1 downTo start) {
            out[i] = '0' + (v % 10).toInt()
            v /= 10
        }
        return start + digits
    }

    private fun putText(out: CharArray, start: Int, text: String): Int {
        for (i in text.indices) out[start + i] = text[i]
        return start + text.length
    }
}
