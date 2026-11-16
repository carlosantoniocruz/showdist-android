package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

private const val PREFS = "showdist"
private const val KEY = "showdist.left_handed"

/**
 * Which hand the bottom rows are laid out for (SPEC.md §1.5): right by default; left
 * mirrors every bottom row, so the same controls sit under the other thumb.
 */
object HandSetting {
    var leftHanded by mutableStateOf(false)
        private set

    fun load(context: Context) {
        leftHanded = context.prefs().getBoolean(KEY, false)
    }

    fun set(context: Context, value: Boolean) {
        leftHanded = value
        context.prefs().edit { putBoolean(KEY, value) }
    }

    private fun Context.prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
