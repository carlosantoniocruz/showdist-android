package com.showconfigs.showdist.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's one snackbar host, shown above every screen, so a message survives the sheet
 * or screen that triggered it ("Copied to clipboard", "Could not share: …").
 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> { error("No SnackbarHostState") }
