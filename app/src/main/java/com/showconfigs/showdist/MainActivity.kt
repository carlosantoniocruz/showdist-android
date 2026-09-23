package com.showconfigs.showdist

import android.os.Bundle
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.showconfigs.showdist.history.HistoryScreen
import com.showconfigs.showdist.history.RecordingStore
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
        setContent { ShowdistTheme { ShowdistApp() } }
    }
}

private enum class Screen { Menu, Measure, Leveler, History, Settings, About, Licenses, LicenseDetail }

/** A back-stack entry; [arg] is the license index for LicenseDetail, unused otherwise. */
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

    fun push(screen: Screen, arg: Int = 0) = stack.add(Route(screen, arg))
    fun pop() = stack.removeAt(stack.lastIndex)

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
                    covered = top.screen != Screen.Measure,
                    onUnits = unitSettings::select,
                    onHistory = { push(Screen.History) },
                )
            }
            when (top.screen) {
                Screen.Menu -> MainMenu(
                    savedCount = saved,
                    onMeasure = { push(Screen.Measure) },
                    onLeveler = { push(Screen.Leveler) },
                    onHistory = { push(Screen.History) },
                    onSettings = { push(Screen.Settings) },
                )
                Screen.Measure -> Unit // Drawn in its slot above.
                Screen.Leveler -> LevelerScreen(onBack = ::pop)
                Screen.History -> HistoryScreen(unitSettings.units, onBack = ::pop)
                Screen.Settings -> SettingsScreen(unitSettings, onAbout = { push(Screen.About) }, onBack = ::pop)
                Screen.About -> AboutScreen(onLicenses = { push(Screen.Licenses) }, onBack = ::pop)
                Screen.Licenses -> LicensesScreen(onOpen = { push(Screen.LicenseDetail, it) }, onBack = ::pop)
                Screen.LicenseDetail -> LicenseDetailScreen(top.arg, onBack = ::pop)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
}
