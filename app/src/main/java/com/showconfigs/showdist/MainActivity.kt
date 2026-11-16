package com.showconfigs.showdist

import com.showconfigs.showdist.history.GroupScreen
import com.showconfigs.showdist.history.GroupStore
import com.showconfigs.showdist.measure.MeasureKind
import com.showconfigs.showdist.settings.MeasureKindSetting
import com.showconfigs.showdist.settings.AboutScreen
import com.showconfigs.showdist.measure.MeasureMode
import com.showconfigs.showdist.settings.Calibration
import com.showconfigs.showdist.settings.CalibrateLevelScreen
import com.showconfigs.showdist.settings.CalibrateScaleScreen
import com.showconfigs.showdist.ui.HapticsSetting
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import com.showconfigs.showdist.ui.Corner
import com.showconfigs.showdist.ui.Palette
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
import com.showconfigs.showdist.leveler.LevelerScreen
import com.showconfigs.showdist.measure.MeasureScreen
import com.showconfigs.showdist.settings.HandSetting
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
        GroupStore.init(this)
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

private enum class Screen { Measure, Leveler, History, Group, Settings, About, Licenses, LicenseDetail, CalibrateScale, CalibrateLevel }

/**
 * A back-stack entry; [arg] is the license index for LicenseDetail, the [LevelerMode]
 * ordinal for Leveler and the [MeasureMode] ordinal for Measure, unused otherwise.
 */
private data class Route(val screen: Screen, val arg: Int = 0)

/**
 * Every screen is a full-screen page on a back stack whose root is Measure (SPEC.md §1.1):
 * the app opens straight into the AR view, and Back there exits the app. Measure is always
 * composed, its session paused while another page covers it, so its points survive a visit
 * to the Leveler, History or Settings. A Measure entry further up the stack is a task on that
 * same session (calibrating, warming up, adding lines to a saved group): it shows Measure
 * again in that mode, and popping it returns to the page that asked.
 */
@Composable
private fun ShowdistApp() {
    val context = LocalContext.current
    val unitSettings = remember { UnitSettings(context) }
    remember { HapticsSetting.load(context) }
    remember { Calibration.load(context) }
    remember { MeasureKindSetting.load(context) }
    remember { HandSetting.load(context) }
    val stack = rememberSaveable(
        saver = listSaver(
            save = { list -> list.flatMap { listOf(it.screen.name, it.arg.toString()) } },
            restore = { flat ->
                // A name this version doesn't know (state saved by an older one) starts afresh.
                val routes = flat.chunked(2).map { (name, arg) ->
                    Screen.entries.firstOrNull { it.name == name }?.let { Route(it, arg.toInt()) }
                }
                val ok = routes.none { it == null } && routes.firstOrNull() == Route(Screen.Measure)
                (if (ok) routes.filterNotNull() else listOf(Route(Screen.Measure))).toMutableStateList()
            },
        ),
    ) { mutableListOf(Route(Screen.Measure)).toMutableStateList() }

    // The recording "View in AR" projects in Measure (SPEC.md §2.11).
    var projectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectionSerial by rememberSaveable { mutableIntStateOf(0) }
    // The leveler the switcher opens: the one last used.
    var levelerMode by rememberSaveable { mutableStateOf(LevelerMode.Tube) }
    // The saved group Measure is adding lines to, if any (SPEC.md §2.13); the group History has open.
    var appendGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    // Whether Measure holds unsaved points; a task needs an empty session.
    var measureBusy by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun push(screen: Screen, arg: Int = 0) = stack.add(Route(screen, arg))
    fun pop() {
        if (stack.size > 1 && stack.removeAt(stack.lastIndex).screen == Screen.Measure) appendGroupId = null
    }

    /** Starts a task on the Measure session, unless it holds a measurement in progress. */
    fun measureTask(mode: MeasureMode, before: () -> Unit = {}) {
        if (measureBusy) {
            scope.launch { snackbar.showSnackbar("Save or clear the measurement in progress first") }
        } else {
            before()
            push(Screen.Measure, mode.ordinal)
        }
    }

    /** Back to Measure, where the recording is projected. */
    fun viewInAr(recording: Recording) {
        projectionId = recording.id
        projectionSerial++
        while (stack.size > 1) pop()
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    val top = stack.last()
    val task = top.screen == Screen.Measure && stack.size > 1

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            // One Measure, in a fixed slot, so no page change recreates the AR session.
            MeasureScreen(
                mode = if (task) MeasureMode.entries[top.arg] else MeasureMode.Normal,
                kind = if (appendGroupId != null) MeasureKind.Group else MeasureKindSetting.remembered ?: MeasureKind.Chained,
                appendTo = appendGroupId,
                units = unitSettings.units,
                projection = RecordingStore.recordings.firstOrNull { it.id == projectionId },
                projectionSerial = projectionSerial,
                covered = top.screen != Screen.Measure,
                task = task,
                onUnits = unitSettings::select,
                onLevel = { push(Screen.Leveler, levelerMode.ordinal) },
                onHistory = { push(Screen.History) },
                onSettings = { push(Screen.Settings) },
                onBack = ::pop,
                onBusy = { measureBusy = it },
                onCalibrated = { raw ->
                    Calibration.target?.let { (_, trueMetres) -> Calibration.calibrateScale(context, raw, trueMetres) }
                    pop()
                },
                // Lines added to a saved group: back to that group.
                onGroupAppended = ::pop,
            )
            when (top.screen) {
                Screen.Measure -> Unit // Drawn in its slot above.
                Screen.Leveler -> LevelerScreen(
                    LevelerMode.entries[top.arg],
                    onMode = {
                        levelerMode = it
                        stack[stack.lastIndex] = Route(Screen.Leveler, it.ordinal)
                    },
                    onMeasure = ::pop,
                )
                Screen.History -> HistoryScreen(
                    unitSettings.units,
                    onViewInAr = ::viewInAr,
                    onOpenGroup = {
                        openGroupId = it
                        push(Screen.Group)
                    },
                    onBack = ::pop,
                )
                Screen.Group -> GroupScreen(
                    groupId = openGroupId,
                    units = unitSettings.units,
                    onAddLines = { id -> measureTask(MeasureMode.Normal) { appendGroupId = id } },
                    onBack = ::pop,
                )
                Screen.Settings -> SettingsScreen(
                    onCalibrateScale = { push(Screen.CalibrateScale) },
                    // The warm-up only watches tracking, so it can run over points already placed.
                    onWarmUp = { push(Screen.Measure, MeasureMode.WarmUp.ordinal) },
                    onLevelZero = { push(Screen.CalibrateLevel) },
                    onAbout = { push(Screen.About) },
                    onLicenses = { push(Screen.Licenses) },
                    onBack = ::pop,
                )
                Screen.About -> AboutScreen(onBack = ::pop)
                Screen.CalibrateScale -> CalibrateScaleScreen(
                    unitSettings.units,
                    onMeasure = { measureTask(MeasureMode.CalibrateScale) },
                    onBack = ::pop,
                )
                Screen.CalibrateLevel -> CalibrateLevelScreen(onBack = ::pop)
                Screen.Licenses -> LicensesScreen(onOpen = { push(Screen.LicenseDetail, it) }, onBack = ::pop)
                Screen.LicenseDetail -> LicenseDetailScreen(top.arg, onBack = ::pop)
            }
            // The snackbar in the app's own colours, not Material's inverse surface.
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { data ->
                Snackbar(
                    data,
                    shape = Corner,
                    containerColor = Palette.SurfaceHigh,
                    contentColor = Palette.Text,
                    actionColor = Palette.Accent,
                )
            }
        }
    }
}
