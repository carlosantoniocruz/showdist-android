package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.showconfigs.showdist.ui.ArScheme

private const val PREFS = "showdist"
private const val KEY_SCHEME = "showdist.line_colours"

/**
 * The persisted line colours for the AR view (SPEC.md §1.5): Aurora by default. Changing it
 * updates every reader immediately and saves it.
 */
class ArSchemeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Missing or unknown values mean Aurora.
    var scheme by mutableStateOf(
        prefs.getString(KEY_SCHEME, null).let { key -> ArScheme.entries.firstOrNull { it.key == key } } ?: ArScheme.Aurora,
    )
        private set

    fun select(value: ArScheme) {
        if (value == scheme) return
        scheme = value
        prefs.edit { putString(KEY_SCHEME, value.key) }
    }
}
