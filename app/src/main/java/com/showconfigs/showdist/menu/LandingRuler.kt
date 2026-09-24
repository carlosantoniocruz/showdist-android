package com.showconfigs.showdist.menu

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.showconfigs.showdist.ui.Palette
import kotlinx.coroutines.delay

private const val TICK_SPACING_DP = 24f
private const val PERIOD_DP = TICK_SPACING_DP * 5 // every 5th tick is major
private const val DRIFT_PERIOD_S = 12f
private const val STEP_MS = 33L
private const val STEPS_PER_S = 30f

/** Fixed per-step advance, so a late step slows the drift slightly instead of jumping. */
private const val STEP_DP = PERIOD_DP / (DRIFT_PERIOD_S * STEPS_PER_S)

/**
 * The landing-screen ruler band (SPEC.md §6.4): upward citrine ticks on a baseline,
 * drifting right one 120 dp period every 12 s. It drifts only while the app is resumed and
 * this screen is shown, and is static when the system's "remove animations" setting is on.
 */
@Composable
fun LandingRuler(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    var offsetDp by remember { mutableFloatStateOf(0f) }

    if (!reduceMotion) {
        LaunchedEffect(lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(STEP_MS)
                    offsetDp = (offsetDp + STEP_DP) % PERIOD_DP
                }
            }
        }
    }

    Canvas(modifier) {
        val spacing = TICK_SPACING_DP.dp.toPx()
        val baselineY = size.height - 16.dp.toPx()
        val minorH = 28.dp.toPx()
        val majorH = 60.dp.toPx()
        val minorW = 2.6.dp.toPx()
        val majorW = 4.dp.toPx()
        val color = Palette.Citrine

        drawLine(color, Offset(0f, baselineY), Offset(size.width, baselineY), minorW)

        // Start one period off the left edge so new ticks slide in as the pattern drifts.
        var x = offsetDp.dp.toPx() - PERIOD_DP.dp.toPx()
        var i = 0
        while (x <= size.width + majorW) {
            val major = i % 5 == 0
            val top = baselineY - if (major) majorH else minorH
            drawLine(color, Offset(x, baselineY), Offset(x, top), if (major) majorW else minorW, StrokeCap.Butt)
            x += spacing
            i++
        }
    }
}
