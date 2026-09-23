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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
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
import com.showconfigs.showdist.ui.Palette
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

@Composable
fun MeasureScreen() {
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

    Box(Modifier.fillMaxSize().background(Palette.DarkTyrianBlue)) {
        when (val s = state) {
            StartState.Waking -> Caption("WAKING THE CAMERA", Modifier.align(Alignment.Center))
            StartState.Ready -> ArCameraArea(onSessionFailed = { state = problemFor(it) })
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
 * The camera area: ARSceneView with the reticle drawn as a Compose overlay at its centre.
 * Leaving composition destroys the session, discarding ARCore's map (SPEC.md §2.1).
 */
@Composable
private fun ArCameraArea(onSessionFailed: (Exception) -> Unit) {
    val tracker = remember { ReticleTracker() }

    Box(Modifier.fillMaxSize().onSizeChanged { tracker.setViewSize(it.width, it.height) }) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            // ARSession downgrades this to DISABLED on devices without depth support.
            depthMode = Config.DepthMode.AUTOMATIC,
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
            focusMode = Config.FocusMode.AUTO,
            sessionConfiguration = { _, config ->
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            },
            // The spec draws no plane visualisation, only the camera feed and the overlay.
            planeRenderer = false,
            onSessionUpdated = { _, frame -> tracker.onFrame(frame) },
            onSessionFailed = onSessionFailed,
            // Permission and ARCore availability are handled before this composes;
            // suppress SceneView's own overlays so only the spec's Problem view is shown.
            cameraPermissionOverlay = {},
            arCoreAvailabilityOverlay = {},
        )
        Reticle(tracker, Modifier.fillMaxSize())
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

/** Caption style (SPEC.md §6.3). JetBrains Mono arrives in Stage 5. */
@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = Palette.WarmGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

/** Problem view (SPEC.md §2.1). */
@Composable
private fun ProblemView(problem: StartState.Problem, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(40.dp)) { drawCircle(Palette.PeachRed.copy(alpha = 0.85f)) }
        Spacer(Modifier.height(24.dp))
        BasicText(
            text = problem.title,
            style = TextStyle(
                color = Palette.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = problem.body,
            style = TextStyle(
                color = Palette.WarmGray,
                fontSize = 14.sp,
                lineHeight = 1.4.em,
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
            .border(BorderStroke(1.dp, Palette.White.copy(alpha = 0.3f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        BasicText(label, style = TextStyle(color = Palette.White, fontSize = 14.sp, fontWeight = FontWeight.Medium))
    }
}
