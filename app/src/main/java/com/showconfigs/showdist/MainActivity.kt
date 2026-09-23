package com.showconfigs.showdist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import com.showconfigs.showdist.leveler.LevelerScreen
import com.showconfigs.showdist.measure.MeasureScreen
import com.showconfigs.showdist.menu.MainMenu
import com.showconfigs.showdist.settings.AboutScreen
import com.showconfigs.showdist.settings.LicenseDetailScreen
import com.showconfigs.showdist.settings.LicensesScreen
import com.showconfigs.showdist.settings.SettingsScreen
import com.showconfigs.showdist.settings.UnitSettings
import com.showconfigs.showdist.ui.PlaceholderScreen
import com.showconfigs.showdist.ui.ShowdistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShowdistTheme { ShowdistApp() } }
    }
}

private enum class Screen { Menu, Measure, Leveler, History, Settings, About, Licenses, LicenseDetail }

/** A back-stack entry; [arg] is the license index for LicenseDetail, unused otherwise. */
private data class Route(val screen: Screen, val arg: Int = 0)

/**
 * Every screen is a full-screen page on a back stack (SPEC.md §1.1). Only the top screen is
 * composed, so leaving Measure closes its AR session and the menu's ruler stops drifting.
 * Back on the menu (the root) exits the app.
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
    when (top.screen) {
        Screen.Menu -> MainMenu(
            savedCount = 0, // No recordings are saved until hold-to-save exists.
            onMeasure = { push(Screen.Measure) },
            onLeveler = { push(Screen.Leveler) },
            onHistory = { push(Screen.History) },
            onSettings = { push(Screen.Settings) },
        )
        Screen.Measure -> MeasureScreen(units = unitSettings.units)
        Screen.Leveler -> LevelerScreen(onBack = ::pop)
        Screen.History -> PlaceholderScreen("HISTORY", "History arrives with saving.", ::pop)
        Screen.Settings -> SettingsScreen(unitSettings, onAbout = { push(Screen.About) }, onBack = ::pop)
        Screen.About -> AboutScreen(onLicenses = { push(Screen.Licenses) }, onBack = ::pop)
        Screen.Licenses -> LicensesScreen(onOpen = { push(Screen.LicenseDetail, it) }, onBack = ::pop)
        Screen.LicenseDetail -> LicenseDetailScreen(top.arg, onBack = ::pop)
    }
}
