package com.showconfigs.showdist.leveler

import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.showconfigs.showdist.ui.AppBarAction
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.CaptionText
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Which Leveler the picker opened (SPEC.md §4). */
enum class LevelerMode { Tube, Bullseye }

/**
 * The Leveler (SPEC.md §4): the tube, a single-axis level held flat against a wall, or
 * the bullseye, a two-axis level laid flat on a surface.
 */
@Composable
fun LevelerScreen(mode: LevelerMode, onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensor = remember {
        LevelSensor(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }
    // Survive rotation, but not leaving the screen: they reset each time the Leveler opens.
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    val zeroed = if (mode == LevelerMode.Tube) offset != 0f else offsetX != 0f || offsetY != 0f

    // The tube rotates with the phone; the bullseye lies flat, where rotation can't be sensed.
    if (mode == LevelerMode.Tube) AllowLandscapeWhileShown()
    // The sensor runs only while the screen is visible and the app is in the foreground.
    LifecycleResumeEffect(sensor) {
        sensor.start()
        onPauseOrDispose { sensor.stop() }
    }

    Scaffold(
        topBar = {
            ShowdistAppBar(if (mode == LevelerMode.Tube) "TUBE LEVELER" else "BULLSEYE LEVELER", onBack) {
                AppBarAction(ShowdistIcons.CenterFocusStrong, "Zero — null against an uneven surface") {
                    offset = sensor.roll
                    offsetX = sensor.tiltX
                    offsetY = sensor.tiltY
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                AppBarAction(ShowdistIcons.RestartAlt, "Reset to true level", enabled = zeroed) {
                    offset = 0f
                    offsetX = 0f
                    offsetY = 0f
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        },
        containerColor = Palette.Black,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (mode == LevelerMode.Tube) "HOLD FLAT AGAINST THE SURFACE" else "LAY FLAT ON THE SURFACE",
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 20.dp, end = 32.dp),
                style = CaptionText.copy(fontSize = 11.sp, letterSpacing = 2.sp),
            )
            when (mode) {
                LevelerMode.Tube -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Vial(displayed = { sensor.roll - offset }, modifier = Modifier.fillMaxSize())
                    }
                    // Recomposes only when the readout's tenth of a degree changes, not on every sample.
                    val tenths by remember { derivedStateOf { tenthsOf(sensor.roll - offset) } }
                    Readout(tenths, level = tenths <= LEVEL_TENTHS, Modifier.padding(bottom = 24.dp))
                }
                LevelerMode.Bullseye -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Bullseye(
                            x = { sensor.tiltX - offsetX },
                            y = { sensor.tiltY - offsetY },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val xTenths by remember { derivedStateOf { tenthsOf(sensor.tiltX - offsetX) } }
                    val yTenths by remember { derivedStateOf { tenthsOf(sensor.tiltY - offsetY) } }
                    val level by remember {
                        derivedStateOf { hypot(sensor.tiltX - offsetX, sensor.tiltY - offsetY) <= LEVEL_THRESHOLD_DEG }
                    }
                    Row(Modifier.padding(bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
                        AxisLabel("X")
                        Readout(xTenths, level, size = 48.sp)
                        Spacer(Modifier.width(24.dp))
                        AxisLabel("Y")
                        Readout(yTenths, level, size = 48.sp)
                    }
                }
            }
        }
    }
}

private fun tenthsOf(degrees: Float) = (abs(degrees) * 10).roundToInt()

/** The tube's level test in readout tenths: |displayed| ≤ 0.3° (SPEC.md §4.1). */
private const val LEVEL_TENTHS = 3

/**
 * `|value|` to one decimal place and "°" (SPEC.md §4.2): bold, tabular, near-white, green
 * when level; 64 sp for the tube, 48 sp for the bullseye's pair, big enough to read from a
 * foot away.
 */
@Composable
private fun Readout(tenths: Int, level: Boolean, modifier: Modifier = Modifier, size: TextUnit = 64.sp) {
    Text(
        text = "${tenths / 10}.${tenths % 10}°",
        modifier = modifier,
        style = BaseText.copy(
            color = if (level) Palette.Green else Palette.Text,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = FEATURES_TABULAR,
        ),
    )
}

/** The bullseye's axis letter before each readout (SPEC.md §4.3). */
@Composable
private fun AxisLabel(axis: String) {
    Text(
        text = axis,
        modifier = Modifier.padding(end = 8.dp, bottom = 16.dp),
        style = CaptionText.copy(color = Palette.Violet, fontSize = 16.sp, letterSpacing = 0.sp),
    )
}

/**
 * While shown, the screen may rotate between portrait and either landscape, never
 * upside-down; on exit it's forced back to portrait-only (SPEC.md §4.1).
 */
@Composable
private fun AllowLandscapeWhileShown() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }
}
