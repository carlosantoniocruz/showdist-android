package com.showconfigs.showdist.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Keeps the screen awake only while this is composed (SPEC.md §0): while measuring, levelling
 * or reading the facts, when the phone is looked at without being touched. Everywhere else,
 * and once Measure has paused itself for idleness, the screen times out as the system is set to.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
