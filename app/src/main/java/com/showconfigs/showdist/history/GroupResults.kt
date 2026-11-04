package com.showconfigs.showdist.history

import com.showconfigs.showdist.measure.UnitSystem
import java.util.Locale

/** Square and cubic feet per square and cubic metre: 1 / 0.3048² and 1 / 0.3048³, exact. */
private const val FT2_PER_M2 = 1.0 / (0.3048 * 0.3048)
private const val FT3_PER_M3 = 1.0 / (0.3048 * 0.3048 * 0.3048)

/**
 * A group's floor area and volume (SPEC.md §2.14), in m² and m³, at full precision. Area needs
 * a Length and a Width line; volume needs those and a Height. Null where they're missing.
 */
class GroupResults(val areaM2: Double?, val volumeM3: Double?) {
    val any: Boolean get() = areaM2 != null
}

fun MeasureGroup.results(): GroupResults {
    fun tagged(t: LineTag) = lines.firstOrNull { it.tag == t }?.metres
    val l = tagged(LineTag.Length)
    val w = tagged(LineTag.Width)
    val h = tagged(LineTag.Height)
    val area = if (l != null && w != null) l * w else null
    val volume = if (area != null && h != null) area * h else null
    return GroupResults(area, volume)
}

/** "120.5 sq ft" or "11.2 m²": two decimals below 100, one above (SPEC.md §2.14). */
fun formatArea(m2: Double, units: UnitSystem): String =
    if (units == UnitSystem.Metric) "${decimals(m2)} m²" else "${decimals(m2 * FT2_PER_M2)} sq ft"

/** "960 cu ft" or "27.3 m³", rounded the same way. */
fun formatVolume(m3: Double, units: UnitSystem): String =
    if (units == UnitSystem.Metric) "${decimals(m3)} m³" else "${decimals(m3 * FT3_PER_M3)} cu ft"

private fun decimals(v: Double): String =
    String.format(Locale.US, if (v < 100) "%,.2f" else "%,.1f", v)

/** The results as text lines, area first, then volume, then the rectangular note; empty if none. */
fun MeasureGroup.resultLines(units: UnitSystem): List<String> {
    val r = results()
    val area = r.areaM2 ?: return emptyList()
    return buildList {
        add("Floor area: ${formatArea(area, units)}")
        r.volumeM3?.let { add("Volume: ${formatVolume(it, units)}") }
        add(RECTANGULAR_NOTE)
    }
}

const val RECTANGULAR_NOTE = "Assumes a rectangular space."
