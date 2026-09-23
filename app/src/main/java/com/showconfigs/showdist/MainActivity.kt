package com.showconfigs.showdist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import com.showconfigs.showdist.leveler.LevelerScreen
import com.showconfigs.showdist.measure.MeasureScreen
import com.showconfigs.showdist.menu.MainMenu
import com.showconfigs.showdist.ui.PlaceholderScreen
import com.showconfigs.showdist.ui.ShowdistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ShowdistTheme { ShowdistApp() } }
    }
}

private enum class Screen { Menu, Measure, Leveler, History, Settings }

/**
 * Every screen is a full-screen page on a back stack (SPEC.md §1.1). Only the top screen is
 * composed, so leaving Measure closes its AR session and the menu's ruler stops drifting.
 * Back on the menu (the root) exits the app.
 */
@Composable
private fun ShowdistApp() {
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.map { it.name } },
            restore = { names -> names.map(Screen::valueOf).toMutableStateList() },
        ),
    ) { mutableListOf(Screen.Menu).toMutableStateList() }

    fun push(screen: Screen) = stack.add(screen)
    fun pop() = stack.removeAt(stack.lastIndex)

    BackHandler(enabled = stack.size > 1) { pop() }

    when (stack.last()) {
        Screen.Menu -> MainMenu(
            savedCount = 0, // No recordings are saved until hold-to-save exists.
            onMeasure = { push(Screen.Measure) },
            onLeveler = { push(Screen.Leveler) },
            onHistory = { push(Screen.History) },
            onSettings = { push(Screen.Settings) },
        )
        Screen.Measure -> MeasureScreen()
        Screen.Leveler -> LevelerScreen(onBack = ::pop)
        Screen.History -> PlaceholderScreen("HISTORY", "History arrives with saving.", ::pop)
        Screen.Settings -> PlaceholderScreen("SETTINGS", "Settings arrive in Stage 7.", ::pop)
    }
}
