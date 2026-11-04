package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.showconfigs.showdist.measure.MeasureKind

private const val PREFS = "showdist"
private const val KEY = "showdist.measure_kind"

/**
 * Whether AR Measure asks Chained or Group each time, or always opens one (SPEC.md §2.13).
 * [remembered] null means ask. Set from the prompt's "Remember my choice", or from Settings.
 */
object MeasureKindSetting {
    var remembered by mutableStateOf<MeasureKind?>(null)
        private set

    fun load(context: Context) {
        remembered = context.prefs().getString(KEY, null)?.let { k -> MeasureKind.entries.firstOrNull { it.name == k } }
    }

    fun set(context: Context, kind: MeasureKind?) {
        remembered = kind
        context.prefs().edit { if (kind == null) remove(KEY) else putString(KEY, kind.name) }
    }

    private fun Context.prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
