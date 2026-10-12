package com.showconfigs.showdist.measure

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.showconfigs.showdist.history.MeasurementSheet
import com.showconfigs.showdist.history.Recording
import com.showconfigs.showdist.history.RecordingStore
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import io.github.sceneview.ar.ARSceneView

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
    units: UnitSystem,
    projection: Recording?,
    projectionSerial: Int,
    covered: Boolean,
    onUnits: (UnitSystem) -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
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
            StartState.Waking -> Caption("WAKING THE CAMERA", Modifier.align(Alignment.Center))
            StartState.Ready -> MeasureArea(units, projection, projectionSerial, covered, onUnits, onHistory, onBack, onSessionFailed = { state = problemFor(it) })
            is StartState.Problem -> ProblemView(s) {
                when (s.action) {
                    ProblemAction.AllowCamera -> permissionLauncher.launch(Manifest.permission.CAMERA)
                    ProblemAction.OpenSettings -> activity.openAppSettings()
                    ProblemAction.TryAgain, null -> start()
                }
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
    units: UnitSystem,
    projection: Recording?,
    projectionSerial: Int,
    covered: Boolean,
    onUnits: (UnitSystem) -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit,
    onSessionFailed: (Exception) -> Unit,
) {
    val tracker = remember { MeasureTracker() }
    val snapRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    SideEffect { tracker.snapRadiusPx = snapRadiusPx }
    // Labels and the total reformat on the next camera frame after a change.
    SideEffect { tracker.units = units }
    DisposableEffect(tracker) { onDispose { tracker.release() } }
    // Each "View in AR" bumps the serial, so picking the same recording again restarts it.
    LaunchedEffect(projectionSerial) { projection?.let { tracker.startProjection(it.points) } }
    val sessionLifecycle = rememberCappedLifecycle(capped = covered)
    val barRotation = rememberBarRotation(sessionLifecycle)
    var saved by remember { mutableStateOf<Recording?>(null) }
    val holdToSave = rememberHoldToSave(tracker) { saved = it }

    Column(Modifier.fillMaxSize().then(holdToSave.modifier)) {
        Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { tracker.setViewSize(it.width, it.height) }) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
                // ARSession downgrades this to DISABLED on devices without depth support.
                depthMode = Config.DepthMode.AUTOMATIC,
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
                focusMode = Config.FocusMode.AUTO,
                sessionConfiguration = { _, config ->
                    config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                    // Every point comes from a real surface hit (SPEC.md §2.4).
                    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
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
            // Layers (SPEC.md §2.2): camera, overlay canvas, top info column.
            MeasureOverlay(tracker, Modifier.fillMaxSize())
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 60.dp, top = 16.dp, end = 60.dp), // clear of the menu button
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HintLine(tracker.hint)
                if (tracker.pointCount >= 2) {
                    Spacer(Modifier.height(6.dp))
                    RunningTotal(tracker.totalMetres, units)
                }
            }
            MenuButton(
                rotation = { barRotation.value },
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(start = 8.dp, top = 8.dp),
            )
        }
        ControlBar(
            canPlace = tracker.canPlace,
            hasPoints = tracker.pointCount > 0 || tracker.projectionStage != ProjectionStage.None,
            savedCount = RecordingStore.recordings.size,
            units = units,
            charge = holdToSave::charge,
            rotation = { barRotation.value },
            onUnits = onUnits,
            onHistory = onHistory,
            onPlace = tracker::requestAdd,
            onUndo = tracker::undo,
            onClear = tracker::clear,
        )
    }

    saved?.let { MeasurementSheet("SAVED", it, units, onDismiss = { saved = null }) }
}

/**
 * Back to the main menu (SPEC.md §2.2): top-left of the camera area, as far as possible from
 * the control bar so it isn't hit by accident. A 48 dp target around a 40 dp scrim disc with
 * a white arrow; it turns with the bar icons (SPEC.md §2.9).
 */
@Composable
private fun MenuButton(rotation: () -> Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .graphicsLayer { rotationZ = rotation() }
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Back to menu" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).background(Palette.Scrim, CircleShape), contentAlignment = Alignment.Center) {
            Icon(ShowdistIcons.ArrowBack, contentDescription = null, tint = Palette.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
        }
    }
}

/** The hint line (SPEC.md §2.3): 15 sp white on a scrim pill, cross-fading on change. */
@Composable
private fun HintLine(hint: Hint) {
    Crossfade(hint, animationSpec = tween(250), label = "hint") { h ->
        Text(
            h.text,
            modifier = Modifier
                .background(Palette.Scrim, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            textAlign = TextAlign.Center,
            style = BaseText.copy(color = Palette.White, fontSize = 15.sp, letterSpacing = 0.3.sp),
        )
    }
}

/** Running total pill (SPEC.md §2.2): Palette.Scrim, pill corners, 40 sp, pure white. */
@Composable
private fun RunningTotal(metres: Double, units: UnitSystem, modifier: Modifier = Modifier) {
    BasicText(
        text = DistanceFormat.format(metres, units),
        modifier = modifier
            .background(Palette.Scrim, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        style = BaseText.copy(
            color = Palette.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraLight,
            fontFeatureSettings = FEATURES_TABULAR,
        ),
    )
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
