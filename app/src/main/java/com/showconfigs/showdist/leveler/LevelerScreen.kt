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
import kotlin.math.roundToInt

/** The Leveler (SPEC.md §4): a single-axis bubble level held flat against a wall. */
@Composable
fun LevelerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val sensor = remember {
        LevelSensor(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }
    // Survives rotation, but not leaving the screen: it resets each time the Leveler opens.
    var offset by rememberSaveable { mutableFloatStateOf(0f) }

    AllowLandscapeWhileShown()
    // The sensor runs only while the screen is visible and the app is in the foreground.
    LifecycleResumeEffect(sensor) {
        sensor.start()
        onPauseOrDispose { sensor.stop() }
    }

    // Recomposes only when the readout's tenth of a degree changes, not on every sample.
    val readoutTenths by remember { derivedStateOf { (abs(sensor.roll - offset) * 10).roundToInt() } }

    Scaffold(
        topBar = {
            ShowdistAppBar("LEVELER", onBack) {
                AppBarAction(ShowdistIcons.CenterFocusStrong, "Zero — null against an uneven wall") {
                    offset = sensor.roll
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                AppBarAction(ShowdistIcons.RestartAlt, "Reset to true level", enabled = offset != 0f) {
                    offset = 0f
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        },
        containerColor = Palette.Ink,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "HOLD FLAT AGAINST THE SURFACE",
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 20.dp, end = 32.dp),
                style = CaptionText.copy(fontSize = 11.sp, letterSpacing = 2.sp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Vial(displayed = { sensor.roll - offset }, modifier = Modifier.fillMaxSize())
            }
            Text(
                text = "${readoutTenths / 10}.${readoutTenths % 10}°",
                modifier = Modifier.padding(bottom = 40.dp),
                style = BaseText.copy(
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraLight,
                    fontFeatureSettings = FEATURES_TABULAR,
                ),
            )
        }
    }
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
