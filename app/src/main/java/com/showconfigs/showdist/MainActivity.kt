package com.showconfigs.showdist

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.showconfigs.showdist.history.HistoryScreen
import com.showconfigs.showdist.history.Recording
import com.showconfigs.showdist.history.RecordingStore
import com.showconfigs.showdist.leveler.LevelerMode
import com.showconfigs.showdist.leveler.LevelerPicker
import com.showconfigs.showdist.leveler.LevelerScreen
import com.showconfigs.showdist.measure.MeasureScreen
import com.showconfigs.showdist.menu.MainMenu
import com.showconfigs.showdist.settings.AboutScreen
import com.showconfigs.showdist.settings.LicenseDetailScreen
import com.showconfigs.showdist.settings.LicensesScreen
import com.showconfigs.showdist.settings.SettingsScreen
import com.showconfigs.showdist.settings.UnitSettings
import com.showconfigs.showdist.ui.LocalSnackbarHostState
import com.showconfigs.showdist.ui.ShowdistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingStore.init(this)
        // Screen kept on while the app is in the foreground (SPEC.md §0); the flag only
        // applies while this window is visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent { ShowdistTheme { ShowdistApp() } }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs, the share sheet and permission prompts can bring the bars back.
        if (hasFocus) hideSystemBars()
    }

    /** Immersive sticky (SPEC.md §0): bars hidden; an edge swipe shows them briefly. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private enum class Screen { Menu, Measure, LevelerPicker, Leveler, History, Settings, About, Licenses, LicenseDetail }

/**
 * A back-stack entry; [arg] is the license index for LicenseDetail and the [LevelerMode]
 * ordinal for Leveler, unused otherwise.
 */
private data class Route(val screen: Screen, val arg: Int = 0)

/**
 * Every screen is a full-screen page on a back stack (SPEC.md §1.1). Only the top screen is
 * composed, so the menu's ruler stops drifting when covered, with one exception: Measure
 * stays composed (session paused) under History opened from it, so its points survive.
 * Leaving Measure itself closes its AR session. Back on the menu (the root) exits the app.
 */
@Composable
private fun ShowdistApp() {
    val context = LocalContext.current
    val unitSettings = remember { UnitSettings(context) }
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.flatMap { listOf(it.screen.name, it.arg.toString()) } },
            restore = { flat ->
                flat.chunked(2).map { (name, arg) -> Route(Screen.valueOf(name), arg.toInt()) }.toMutableStateList()
            },
        ),
    ) { mutableListOf(Route(Screen.Menu)).toMutableStateList() }

    // The recording "View in AR" projects in Measure (SPEC.md §2.11); cleared when Measure closes.
    var projectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectionSerial by rememberSaveable { mutableIntStateOf(0) }

    fun push(screen: Screen, arg: Int = 0) = stack.add(Route(screen, arg))
    fun pop() {
        if (stack.removeAt(stack.lastIndex).screen == Screen.Measure) projectionId = null
    }

    /** Back to the open Measure if there is one (History over it), else a new Measure on top. */
    fun viewInAr(recording: Recording) {
        projectionId = recording.id
        projectionSerial++
        if (stack.any { it.screen == Screen.Measure }) {
            while (stack.last().screen != Screen.Measure) pop()
        } else {
            push(Screen.Measure)
        }
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    val top = stack.last()
    val snackbar = remember { SnackbarHostState() }
    val saved = RecordingStore.recordings.size

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            // A fixed slot, so History pushed from Measure doesn't recreate the AR session.
            if (stack.any { it.screen == Screen.Measure }) {
                MeasureScreen(
                    units = unitSettings.units,
                    projection = RecordingStore.recordings.firstOrNull { it.id == projectionId },
                    projectionSerial = projectionSerial,
                    covered = top.screen != Screen.Measure,
                    onUnits = unitSettings::select,
                    onHistory = { push(Screen.History) },
                    onBack = ::pop,
                )
            }
            when (top.screen) {
                Screen.Menu -> MainMenu(
                    savedCount = saved,
                    onMeasure = { push(Screen.Measure) },
                    onLeveler = { push(Screen.LevelerPicker) },
                    onHistory = { push(Screen.History) },
                    onSettings = { push(Screen.Settings) },
                )
                Screen.Measure -> Unit // Drawn in its slot above.
                Screen.LevelerPicker -> LevelerPicker(onPick = { push(Screen.Leveler, it.ordinal) }, onBack = ::pop)
                Screen.Leveler -> LevelerScreen(LevelerMode.entries[top.arg], onBack = ::pop)
                Screen.History -> HistoryScreen(unitSettings.units, onViewInAr = ::viewInAr, onBack = ::pop)
                Screen.Settings -> SettingsScreen(unitSettings, onAbout = { push(Screen.About) }, onBack = ::pop)
                Screen.About -> AboutScreen(onLicenses = { push(Screen.Licenses) }, onBack = ::pop)
                Screen.Licenses -> LicensesScreen(onOpen = { push(Screen.LicenseDetail, it) }, onBack = ::pop)
                Screen.LicenseDetail -> LicenseDetailScreen(top.arg, onBack = ::pop)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
