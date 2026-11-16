package com.showconfigs.showdist.measure

import com.showconfigs.showdist.settings.Calibration
import kotlinx.coroutines.delay
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.derivedStateOf
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import kotlin.math.abs
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import androidx.compose.foundation.layout.Row
import com.showconfigs.showdist.history.GroupStore
import com.showconfigs.showdist.history.MeasurementSheet
import com.showconfigs.showdist.history.Recording
import com.showconfigs.showdist.history.RecordingStore
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import io.github.sceneview.ar.ARSceneView

/**
 * What Measure is opened for (SPEC.md §1.5a): measuring, calibrating the scale against a
 * known object (two points, then [MeasureScreen]'s onCalibrated), or warming up tracking.
 */
enum class MeasureMode { Normal, CalibrateScale, WarmUp }

/**
 * How points join (SPEC.md §2.13): one continuous path with a running total, or separate
 * lines that together belong to one thing (a room, a table, a cabinet).
 */
enum class MeasureKind { Chained, Group }

/** Outcome of the start routine (SPEC.md §2.1). */
private sealed interface StartState {
    data object Waking : StartState
    data object Ready : StartState
    data class Problem(
        val title: String,
        val body: String,
        val action: ProblemAction? = null,
        /** Re-run the start routine when the app returns to the foreground. */
        val retryOnResume: Boolean = false,
    ) : StartState
}

private enum class ProblemAction(val label: String) {
    AllowCamera("Allow camera"),
    OpenSettings("Open settings"),
    TryAgain("Try again"),
}

/**
 * The Measure screen. While [covered] (History is open on top), the AR session is paused
 * in place, so points survive and tracking resumes on return (SPEC.md §2.1).
 */
@Composable
fun MeasureScreen(
    mode: MeasureMode,
    kind: MeasureKind,
    appendTo: String?,
    units: UnitSystem,
    projection: Recording?,
    projectionSerial: Int,
    covered: Boolean,
    task: Boolean,
    onUnits: (UnitSystem) -> Unit,
    onLevel: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onBusy: (Boolean) -> Unit,
    onCalibrated: (rawMetres: Double) -> Unit = {},
    onGroupAppended: () -> Unit = {},
) {
    val activity = LocalActivity.current ?: return
    var state by remember { mutableStateOf<StartState>(StartState.Waking) }
    // The ARCore install prompt is shown only on the first attempt (SPEC.md §2.1).
    var installPrompted by remember { mutableStateOf(false) }

    fun checkArCore() {
        state = try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installPrompted)) {
                ArCoreApk.InstallStatus.INSTALLED -> StartState.Ready
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installPrompted = true
                    StartState.Problem(
                        title = "Installing AR services",
                        body = "Google Play Services for AR is being installed. Come back here when it finishes.",
                        retryOnResume = true,
                    )
                }
            }
        } catch (e: Exception) {
            installPrompted = true
            problemFor(e)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            checkArCore()
        } else {
            // After a denial, "no rationale" means the user chose "don't ask again".
            val permanent = !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            state = cameraDeniedProblem(permanent)
        }
    }

    fun start() {
        state = StartState.Waking
        if (activity.hasCameraPermission()) checkArCore()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) { start() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if ((state as? StartState.Problem)?.retryOnResume == true) start()
    }

    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        when (val s = state) {
            StartState.Ready -> MeasureArea(
                mode, kind, appendTo, units, projection, projectionSerial, covered, task, onUnits, onLevel, onHistory,
                onSettings, onBack, onBusy, onCalibrated, onGroupAppended, onSessionFailed = { state = problemFor(it) },
            )
            // Without the camera the bottom row still leads to the Leveler, History and Settings.
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (s is StartState.Problem) {
                        ProblemView(s) {
                            when (s.action) {
                                ProblemAction.AllowCamera -> permissionLauncher.launch(Manifest.permission.CAMERA)
                                ProblemAction.OpenSettings -> activity.openAppSettings()
                                ProblemAction.TryAgain, null -> start()
                            }
                        }
                    } else {
                        Caption("WAKING THE CAMERA", Modifier.align(Alignment.Center))
                    }
                }
                ControlBar(
                    canPlace = false,
                    hasPoints = false,
                    savedCount = RecordingStore.recordings.size + GroupStore.groups.size,
                    task = task,
                    charge = { 0f },
                    rotation = { 0f },
                    onPlace = {},
                    onUndo = {},
                    onClear = {},
                    onLevel = onLevel,
                    onHistory = onHistory,
                    onSettings = onSettings,
                    onBack = onBack,
                )
            }
        }
    }
}

/**
 * The AR layout (SPEC.md §2.2): the camera area fills everything above the control bar,
 * so nothing measured is hidden behind it. Leaving composition destroys the session,
 * discarding ARCore's map and its anchors (SPEC.md §2.1).
 */
@Composable
private fun MeasureArea(
    mode: MeasureMode,
    kind: MeasureKind,
    appendTo: String?,
    units: UnitSystem,
    projection: Recording?,
    projectionSerial: Int,
    covered: Boolean,
    task: Boolean,
    onUnits: (UnitSystem) -> Unit,
    onLevel: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onBusy: (Boolean) -> Unit,
    onCalibrated: (rawMetres: Double) -> Unit,
    onGroupAppended: () -> Unit,
    onSessionFailed: (Exception) -> Unit,
) {
    val tracker = remember { MeasureTracker() }
    // Chained or Group (SPEC.md §2.13) can change in Settings while Measure stays open; a
    // change takes effect once no points are placed. Calibration always measures one plain line.
    var activeKind by remember { mutableStateOf(kind) }
    val empty = tracker.pointCount == 0
    LaunchedEffect(kind, empty) { if (empty) activeKind = kind }
    LaunchedEffect(empty) { onBusy(!empty) }
    val grouping = activeKind == MeasureKind.Group && mode == MeasureMode.Normal
    SideEffect { if (tracker.pointCount == 0) tracker.groupMode = grouping }
    val group = rememberGroupSession(tracker, units, appendTo, onGroupAppended)
    val calibrating = mode == MeasureMode.CalibrateScale
    // Raw readings while calibrating, so they can be compared with the true length.
    SideEffect { tracker.scale = if (calibrating) 1.0 else Calibration.scale }
    if (calibrating) {
        LaunchedEffect(tracker.pointCount) {
            if (tracker.pointCount == 2) {
                delay(400) // let the line show, and the anchors settle
                onCalibrated(tracker.rawDistance(0, 1))
            }
        }
    }
    // The warm-up (SPEC.md §1.5a): opened from Settings, or from the prompt when tracking is poor.
    var warmUpOpen by remember { mutableStateOf(mode == MeasureMode.WarmUp) }
    LaunchedEffect(mode) { warmUpOpen = mode == MeasureMode.WarmUp }
    var warmUpOffered by remember { mutableStateOf(false) }
    val poor = tracker.status == TrackingStatus.Lost || tracker.status == TrackingStatus.NeedDetail ||
        (tracker.status == TrackingStatus.MoveSlowly && tracker.pointCount > 0)
    LaunchedEffect(poor, warmUpOpen) {
        if (poor && !warmUpOpen) {
            delay(WARM_UP_PROMPT_MS)
            warmUpOffered = true
        } else if (!poor) {
            warmUpOffered = false
        }
    }
    val snapRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    SideEffect { tracker.snapRadiusPx = snapRadiusPx }
    // Labels and the total reformat on the next camera frame after a change.
    SideEffect { tracker.units = units }
    DisposableEffect(tracker) { onDispose { tracker.release() } }
    // A task that places points starts from an empty session and leaves nothing behind in
    // it, whether it finished or was backed out of. The warm-up places none, so it keeps them.
    val clearOnExit = task && mode != MeasureMode.WarmUp
    DisposableEffect(clearOnExit) { onDispose { if (clearOnExit) tracker.clear() } }
    // Each "View in AR" bumps the serial, so picking the same recording again restarts it.
    LaunchedEffect(projectionSerial) { projection?.let { tracker.startProjection(it.points, it.scale) } }
    // The idle pause (SPEC.md §2.1a): about a minute with no touch and no meaningful movement
    // pauses the session exactly as covering Measure does, which stops ARCore and hands the
    // camera back; a tap resumes it. Leaving for another page clears it, so coming back resumes.
    val idle = remember { IdleClock() }
    var idlePaused by remember { mutableStateOf(false) }
    if (covered && idlePaused) idlePaused = false
    val sessionLifecycle = rememberCappedLifecycle(capped = covered || idlePaused)
    val barRotation = rememberBarRotation(sessionLifecycle, onMotion = idle::touch)
    var saved by remember { mutableStateOf<Recording?>(null) }
    // Reading a sheet or typing a name is using the app, though no touch reaches Measure.
    val occupied by rememberUpdatedState(saved != null || group.naming || group.editingLine != null)
    val appLifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(covered, idlePaused) {
        if (covered || idlePaused) return@LaunchedEffect
        idle.touch()
        while (true) {
            delay(IDLE_CHECK_MS)
            // Only time spent in the foreground counts; the background pauses on its own.
            if (occupied || !appLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) idle.touch()
            if (idle.idleMs(tracker.lastMotionNanos) >= IDLE_PAUSE_MS) {
                idlePaused = true
                break
            }
        }
    }
    val holdToSave = rememberHoldToSave(
        tracker,
        enabled = !calibrating && !warmUpOpen,
        ready = { if (grouping) tracker.completedLines >= 1 else tracker.pointCount >= 2 },
        onComplete = {
            if (grouping) {
                group.finish()
            } else {
                val recording = Recording.create(tracker.snapshotPoints(), tracker.units, tracker.scale)
                tracker.clear()
                RecordingStore.add(recording)
                saved = recording
            }
        },
    )

    // −1, 0 or +1 as the bar controls are turned; recomposes only when it crosses 45°.
    val sideways by remember {
        derivedStateOf {
            val r = barRotation.value
            if (abs(r) <= 45f) 0 else if (r > 0f) 1 else -1
        }
    }
    val cornerRadiusPx = rememberScreenCornerRadius()
    // The top info column's bottom edge, so the SAVING pill can sit below it.
    var infoBottomPx by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().notesTouches(idle).then(holdToSave.modifier).edgeProgress(holdToSave::charge, cornerRadiusPx)) {
        // The camera area's size in dp, which caps the live readout's width.
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        val areaWidth = with(LocalDensity.current) { areaSize.width.toDp() }
        val areaHeight = with(LocalDensity.current) { areaSize.height.toDp() }
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged {
                areaSize = it
                tracker.setViewSize(it.width, it.height)
            },
        ) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                // Low-power setup (SPEC.md §2.1): 30 fps camera, planes both ways. Depth is turned on
                // below where the phone supports it: plain walls give no plane to hit without it.
                sessionCameraConfig = ::modestCameraConfig,
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
                depthMode = Config.DepthMode.DISABLED,
                instantPlacementMode = Config.InstantPlacementMode.DISABLED,
                cloudAnchorMode = Config.CloudAnchorMode.DISABLED,
                geospatialMode = Config.GeospatialMode.DISABLED,
                streetscapeGeometryMode = Config.StreetscapeGeometryMode.DISABLED,
                augmentedFaceMode = Config.AugmentedFaceMode.DISABLED,
                semanticMode = Config.SemanticMode.DISABLED,
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
                focusMode = Config.FocusMode.AUTO,
                sessionConfiguration = { session, config ->
                    // Augmented images stay off: the config's image database is left empty.
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
                },
                // The spec draws no plane visualisation, only the camera feed and the overlay.
                planeRenderer = false,
                onSessionUpdated = { session, frame -> tracker.onFrame(session, frame) },
                onSessionFailed = onSessionFailed,
                // Permission and ARCore availability are handled before this composes;
                // suppress SceneView's own overlays so only the spec's Problem view is shown.
                cameraPermissionOverlay = {},
                arCoreAvailabilityOverlay = {},
                lifecycle = sessionLifecycle,
            )
            // Until real camera images arrive, the feed would show uninitialised texture memory.
            if (!tracker.cameraLive) Box(Modifier.fillMaxSize().background(Palette.Black))
            // Layers (SPEC.md §2.2): camera, overlay canvas, top info column.
            MeasureOverlay(tracker, { barRotation.value }, Modifier.fillMaxSize())
            Reticle(tracker, Modifier.align(Alignment.Center))
            // The top holds only passive items (SPEC.md §2.2): the tracking state, then the hint.
            val totalShown = if (grouping) groupLengthShown(tracker) else tracker.pointCount >= 2
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp)
                    .onPlaced { infoBottomPx = it.boundsInParent().bottom.toInt() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrackingPill(tracker.status)
                Spacer(Modifier.height(6.dp))
                HintLine(tracker.hint, instruction = if (calibrating) "Place a point at each end of ${Calibration.target?.first?.what ?: "the object"}" else null)
            }
            // Held sideways (SPEC.md §2.9): the total sits centred on the reader's top edge,
            // which is the screen's right edge (turned clockwise) or left edge, at a smaller size.
            if (totalShown && sideways != 0) {
                RunningTotal(
                    tracker.totalMetres,
                    units,
                    minOf(areaHeight * READOUT_SIDEWAYS_SHARE, READOUT_SIDEWAYS_MAX),
                    Modifier
                        .align(if (sideways > 0) Alignment.CenterEnd else Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 12.dp)
                        .turnWith { barRotation.value },
                    compact = true,
                )
            }
            val saving by remember { derivedStateOf { holdToSave.charge() > 0f } }
            if (saving) {
                SavingPill(
                    Modifier.matchParentSize().belowInfo { infoBottomPx }.turnWith { barRotation.value },
                )
            }
            if (warmUpOpen) {
                WarmUpOverlay(
                    quality = { tracker.warmQuality },
                    onDone = {
                        warmUpOpen = false
                        if (mode == MeasureMode.WarmUp) onBack()
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Everything tappable in the camera area sits at its foot, just above the bottom
            // row (SPEC.md §2.2): prompts, the live readout, then the unit toggle and, in a
            // group, its line count and Done. The stack turns as one when the phone is held
            // sideways (SPEC.md §2.9); the readout then moves to the reader's top edge, above.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(bottom = 4.dp)
                    .turnWith { barRotation.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (warmUpOffered && !warmUpOpen) WarmUpPrompt(onClick = { warmUpOpen = true })
                if (grouping) LineCard(group, units, tracker.totalMetres)
                if (totalShown && sideways == 0) RunningTotal(tracker.totalMetres, units, areaWidth - 48.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    UnitToggle(units, onUnits)
                    if (grouping && tracker.completedLines >= 1) GroupBar(tracker.completedLines, onDone = group::finish)
                }
            }
        }
        ControlBar(
            canPlace = tracker.canPlace,
            hasPoints = tracker.pointCount > 0 || tracker.projectionStage != ProjectionStage.None,
            savedCount = RecordingStore.recordings.size + GroupStore.groups.size,
            task = task,
            charge = holdToSave::charge,
            rotation = { barRotation.value },
            onPlace = tracker::requestAdd,
            onUndo = tracker::undo,
            onClear = tracker::clear,
            onLevel = onLevel,
            onHistory = onHistory,
            onSettings = onSettings,
            onBack = onBack,
        )
    }

    IdlePauseOverlay(idlePaused && !covered, onResume = { idlePaused = false })

    saved?.let { MeasurementSheet("SAVED", it, units, onDismiss = { saved = null }) }
    if (grouping) GroupDialogs(group)
}

/**
 * The hint line (SPEC.md §2.3): 15 sp white on a scrim pill, cross-fading on change. Tracking
 * hints are left to the status pill; for those it keeps its height, empty, so the running
 * total below it doesn't jump. An [instruction] (calibration) replaces the other hints.
 */
@Composable
private fun HintLine(hint: Hint, instruction: String? = null) {
    Crossfade(hint, animationSpec = tween(250), label = "hint") { h ->
        Text(
            if (h.tracking) " " else instruction ?: h.text,
            modifier = Modifier
                .then(if (h.tracking) Modifier else Modifier.background(Palette.Scrim, RoundedCornerShape(50)))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            style = BaseText.copy(color = Palette.White, fontSize = 15.sp, letterSpacing = 0.3.sp),
        )
    }
}

/** The readout's largest and smallest text, in dp so the system font size can't inflate it. */
private val READOUT_MAX = 30.dp
private val READOUT_MIN = 14.dp

/** Held sideways the readout runs along the screen's height: at most this share of it, and this long. */
private const val READOUT_SIDEWAYS_SHARE = 0.6f
private val READOUT_SIDEWAYS_MAX = 340.dp

/**
 * The live readout (SPEC.md §2.2): Palette.Scrim, pill corners, pure white, the unit always
 * in words beside the number. Its text is capped at 30 dp and shrinks, down to 14 dp, to fit
 * [maxWidth] on one line, so it never outgrows the camera view in either orientation;
 * [compact] (held sideways) has tighter padding.
 */
@Composable
private fun RunningTotal(metres: Double, units: UnitSystem, maxWidth: Dp, modifier: Modifier = Modifier, compact: Boolean = false) {
    val sizes = with(LocalDensity.current) {
        TextAutoSize.StepBased(minFontSize = READOUT_MIN.toSp(), maxFontSize = READOUT_MAX.toSp(), stepSize = 1.dp.toSp())
    }
    BasicText(
        text = DistanceFormat.formatWords(metres, units),
        maxLines = 1,
        softWrap = false,
        autoSize = sizes,
        modifier = modifier
            .widthIn(max = maxWidth)
            .background(Palette.Scrim, RoundedCornerShape(50))
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 2.dp else 4.dp),
        style = BaseText.copy(color = Palette.White, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = FEATURES_TABULAR),
    )
}

/**
 * Shown while a hold-to-save charges (SPEC.md §2.6): white "SAVING" on a white-bordered
 * scrim pill. The progress itself runs round the screen's edge.
 */
@Composable
private fun SavingPill(modifier: Modifier = Modifier) {
    BasicText(
        "SAVING",
        modifier = modifier
            .background(Palette.Scrim, RoundedCornerShape(50))
            .border(BorderStroke(1.5.dp, Palette.White), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        style = BaseText.copy(color = Palette.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
    )
}

private val EDGE_WIDTH = 6.dp
private val EDGE_HALO = 3.dp

/**
 * The hold-to-save progress (SPEC.md §2.6): while [charge] is above 0, a sandstone band runs clockwise round the screen's edge from top centre, following
 * the display's rounded corners ([cornerRadiusPx]), over a dim track and a dark halo.
 * The paths are built once per size; [charge] is read only while drawing.
 */
private fun Modifier.edgeProgress(charge: () -> Float, cornerRadiusPx: Float) = drawWithCache {
    val stroke = EDGE_WIDTH.toPx()
    val inset = stroke / 2f
    val r = (cornerRadiusPx - inset).coerceAtLeast(0f)
    val l = inset
    val t = inset
    val rt = size.width - inset
    val b = size.height - inset
    val outline = Path().apply {
        moveTo(size.width / 2f, t)
        lineTo(rt - r, t)
        arcTo(Rect(rt - 2 * r, t, rt, t + 2 * r), -90f, 90f, false)
        lineTo(rt, b - r)
        arcTo(Rect(rt - 2 * r, b - 2 * r, rt, b), 0f, 90f, false)
        lineTo(l + r, b)
        arcTo(Rect(l, b - 2 * r, l + 2 * r, b), 90f, 90f, false)
        lineTo(l, t + r)
        arcTo(Rect(l, t, l + 2 * r, t + 2 * r), 180f, 90f, false)
        close()
    }
    val measure = PathMeasure().apply { setPath(outline, false) }
    val length = measure.length
    val done = Path()
    val bandStroke = Stroke(stroke, cap = StrokeCap.Round)
    val haloStroke = Stroke(stroke + 2 * EDGE_HALO.toPx(), cap = StrokeCap.Round)
    val track = Palette.White.copy(alpha = 0.2f)
    onDrawWithContent {
        drawContent()
        val c = charge().coerceIn(0f, 1f)
        if (c <= 0f) return@onDrawWithContent
        done.reset()
        measure.getSegment(0f, length * c, done, true)
        drawPath(outline, track, style = bandStroke)
        drawPath(done, Palette.Halo, style = haloStroke)
        drawPath(done, Palette.Sandstone, style = bandStroke)
    }
}

/** The display's top-left corner radius in pixels (API 31+), else 0 for square corners. */
@Composable
private fun rememberScreenCornerRadius(): Float {
    val view = LocalView.current
    return remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat() ?: 0f
        } else {
            0f
        }
    }
}

/** A group's length pill shows a finished line, or one being drawn while it has a surface to reach. */
private fun groupLengthShown(t: MeasureTracker): Boolean =
    if (t.pointCount % 2 == 1) t.hasHit else t.pointCount >= 2

/** Clearance kept above the reticle's centre: its 16 dp ring plus its dots and halo. */
private val RETICLE_CLEARANCE = 28.dp

/**
 * Centres the content halfway between [infoBottom] (px) and the top of the reticle at the
 * area's centre (SPEC.md §2.6). Read only while placing, so it never recomposes.
 */
private fun Modifier.belowInfo(infoBottom: () -> Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    layout(constraints.maxWidth, constraints.maxHeight) {
        val reticleTop = constraints.maxHeight / 2 - RETICLE_CLEARANCE.roundToPx()
        val centreY = (infoBottom() + reticleTop) / 2
        placeable.place((constraints.maxWidth - placeable.width) / 2, centreY - placeable.height / 2)
    }
}

/**
 * Turns the content by [rotation] degrees about its centre so it reads upright however the
 * phone is held (SPEC.md §2.9); turned sideways, it takes its rotated footprint in layout.
 */
private fun Modifier.turnWith(rotation: () -> Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    val sideways = abs(rotation()) > 45f
    val w = if (sideways) placeable.height else placeable.width
    val h = if (sideways) placeable.width else placeable.height
    layout(w, h) {
        placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) { rotationZ = rotation() }
    }
}

private fun problemFor(e: Exception): StartState.Problem = when (e) {
    is UnavailableUserDeclinedInstallationException -> StartState.Problem(
        title = "AR services required",
        body = "Showdist needs Google Play Services for AR to see surfaces.",
        action = ProblemAction.TryAgain,
    )
    is UnavailableDeviceNotCompatibleException -> StartState.Problem(
        title = "Device not supported",
        body = "This device does not support ARCore, which measuring depends on.",
    )
    is UnavailableApkTooOldException, is UnavailableSdkTooOldException -> StartState.Problem(
        title = "AR services out of date",
        body = "Update Google Play Services for AR from the Play Store, then try again.",
        action = ProblemAction.TryAgain,
    )
    is CameraNotAvailableException -> StartState.Problem(
        title = "Camera unavailable",
        body = "Another app is using the camera. Close it and try again.",
        action = ProblemAction.TryAgain,
    )
    else -> StartState.Problem(
        title = "Something went wrong",
        body = "error:${e.javaClass.simpleName}: ${e.message}",
        action = ProblemAction.TryAgain,
    )
}

private fun cameraDeniedProblem(permanent: Boolean) = StartState.Problem(
    title = "Camera access needed",
    body = "Measuring uses the camera to track surfaces. Nothing is recorded or uploaded.",
    action = if (permanent) ProblemAction.OpenSettings else ProblemAction.AllowCamera,
    retryOnResume = permanent,
)

private fun Activity.hasCameraPermission(): Boolean =
    checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Activity.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    )
}

/** Caption style (SPEC.md §6.3). */
@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    BasicText(text = text, modifier = modifier, style = Type.Caption)
}

/** Problem view (SPEC.md §2.1). */
@Composable
private fun ProblemView(problem: StartState.Problem, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(ShowdistIcons.ErrorOutline, contentDescription = null, tint = Palette.Sandstone, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(24.dp))
        BasicText(
            text = problem.title,
            style = BaseText.copy(
                color = Palette.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = problem.body,
            style = Type.Secondary.copy(
                fontSize = 16.sp,
                lineHeight = 1.45.em,
                textAlign = TextAlign.Center,
            ),
        )
        problem.action?.let { action ->
            Spacer(Modifier.height(28.dp))
            OutlinedTextButton(action.label, onAction)
        }
    }
}

@Composable
private fun OutlinedTextButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(BorderStroke(1.dp, Palette.Sandstone), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        BasicText(label, style = Type.Label.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
    }
}
