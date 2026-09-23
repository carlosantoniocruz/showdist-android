package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.showconfigs.showdist.measure.UnitSystem

private const val PREFS = "showdist"
private const val KEY_UNITS = "units"

/**
 * The persisted unit system (SPEC.md §2.5): Imperial by default, shared by Settings and
 * the Measure screen. Changing it updates every reader immediately and saves it.
 */
class UnitSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var units by mutableStateOf(
        if (prefs.getString(KEY_UNITS, null) == "metric") UnitSystem.Metric else UnitSystem.Imperial,
    )
        private set

    fun select(value: UnitSystem) {
        if (value == units) return
        units = value
        prefs.edit { putString(KEY_UNITS, if (value == UnitSystem.Metric) "metric" else "imperial") }
    }
}
