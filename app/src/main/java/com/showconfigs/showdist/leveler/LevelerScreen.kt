package com.showconfigs.showdist.leveler

import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.showconfigs.showdist.ui.rememberHaptics
import com.showconfigs.showdist.ui.BarIconButton
import com.showconfigs.showdist.ui.BarItem
import com.showconfigs.showdist.ui.BarSlot
import com.showconfigs.showdist.ui.BottomRow
import com.showconfigs.showdist.ui.BottomRowCentre
import com.showconfigs.showdist.ui.BottomRowSlot
import com.showconfigs.showdist.ui.ModeSwitcher
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Which Leveler the picker opened (SPEC.md §4). */
enum class LevelerMode { Tube, Bullseye }

/**
 * The Leveler (SPEC.md §4): the tube, a single-axis level held flat against a wall, or
 * the bullseye, a two-axis level laid flat on a surface. Its bottom row matches Measure's
 * (SPEC.md §1.1a): Zero in the centre, the switcher back to Measure ([onMeasure]) where
 * Measure has it, the Tube/Bullseye toggle ([onMode]) beside it, and Reset on the tool side.
 */
@Composable
fun LevelerScreen(mode: LevelerMode, onMode: (LevelerMode) -> Unit, onMeasure: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val sensor = remember {
        LevelSensor(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }
    // Survive rotation, but not leaving the screen: they reset each time the Leveler opens.
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
    val zeroed = if (mode == LevelerMode.Tube) offset != 0f else offsetX != 0f || offsetY != 0f

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The tube rotates with the phone; the bullseye lies flat, where rotation can't be sensed.
    if (mode == LevelerMode.Tube) AllowLandscapeWhileShown()
    // The sensor runs only while the screen is visible and the app is in the foreground.
    LifecycleResumeEffect(sensor) {
        sensor.start()
        onPauseOrDispose { sensor.stop() }
    }

    Scaffold(
        // Only the title up here, nothing to tap (SPEC.md §1.2). In landscape (the tube
        // rotates) it gives its height to the vial and readout.
        topBar = { if (!landscape) ShowdistAppBar(if (mode == LevelerMode.Tube) "TUBE LEVELER" else "BULLSEYE LEVELER") },
        bottomBar = {
            BottomRow(
                nav = listOf(
                    BarItem(visual = 56.dp) { BarSlot(BottomRowSlot) { ModeSwitcher(levelActive = true, onSwitch = onMeasure) } },
                    BarItem {
                        BarSlot(BottomRowSlot) {
                            if (mode == LevelerMode.Tube) {
                                BarIconButton(ShowdistIcons.RadioButtonChecked, "Switch to the bullseye level") { onMode(LevelerMode.Bullseye) }
                            } else {
                                BarIconButton(ShowdistIcons.Level, "Switch to the tube level") { onMode(LevelerMode.Tube) }
                            }
                        }
                    },
                ),
                centre = {
                    BarIconButton(
                        ShowdistIcons.CenterFocusStrong,
                        "Zero: null against an uneven surface",
                        size = BottomRowCentre,
                        iconSize = 32.dp,
                        tint = Palette.Sandstone,
                    ) {
                        offset = sensor.readoutRoll
                        offsetX = sensor.readoutTiltX
                        offsetY = sensor.readoutTiltY
                        haptics.tick()
                    }
                },
                tools = listOf(
                    BarItem {
                        BarSlot(BottomRowSlot) {
                            BarIconButton(ShowdistIcons.RestartAlt, "Reset to true level", enabled = zeroed) {
                                offset = 0f
                                offsetX = 0f
                                offsetY = 0f
                                haptics.tick()
                            }
                        }
                    },
                ),
            )
        },
        containerColor = Palette.Black,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (mode == LevelerMode.Tube) "HOLD FLAT AGAINST THE SURFACE" else "LAY FLAT ON THE SURFACE",
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 16.dp, end = 32.dp),
                style = Type.Caption.copy(letterSpacing = 2.sp),
            )
            when (mode) {
                LevelerMode.Tube -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Vial(displayed = { sensor.roll - offset }, modifier = Modifier.fillMaxSize())
                    }
                    // Recomposes only when the readout's tenth of a degree changes, not on every sample.
                    val settle = remember { SettledTenths() }
                    val tenths by remember { derivedStateOf { settle.of(sensor.readoutRoll - offset) } }
                    val level = tenths <= LEVEL_TENTHS
                    TickOnLevel(level)
                    Readout(tenths, level, Modifier.padding(bottom = 16.dp))
                }
                LevelerMode.Bullseye -> {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Bullseye(
                            x = { sensor.tiltX - offsetX },
                            y = { sensor.tiltY - offsetY },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    val settleX = remember { SettledTenths() }
                    val settleY = remember { SettledTenths() }
                    val xTenths by remember { derivedStateOf { settleX.of(sensor.readoutTiltX - offsetX) } }
                    val yTenths by remember { derivedStateOf { settleY.of(sensor.readoutTiltY - offsetY) } }
                    val level by remember {
                        derivedStateOf {
                            hypot(sensor.readoutTiltX - offsetX, sensor.readoutTiltY - offsetY) <= LEVEL_THRESHOLD_DEG
                        }
                    }
                    TickOnLevel(level)
                    // Stacked: two readouts this size don't fit side by side in portrait.
                    Column(Modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.End) {
                        for ((axis, tenths) in arrayOf("X" to xTenths, "Y" to yTenths)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                AxisLabel(axis)
                                Readout(tenths, level, size = BULLSEYE_READOUT)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * A readout in tenths of a degree, with hysteresis (SPEC.md §4.1): the shown tenth changes
 * only once |degrees| is [HYSTERESIS_TENTHS] past it, so a reading on a boundary holds still.
 */
private class SettledTenths {
    private var shown = -1

    fun of(degrees: Float): Int {
        val t = abs(degrees) * 10
        if (shown < 0 || abs(t - shown) >= HYSTERESIS_TENTHS) shown = t.roundToInt()
        return shown
    }
}

private const val HYSTERESIS_TENTHS = 0.75f

/** The tube's level test in readout tenths: |displayed| ≤ 0.3° (SPEC.md §4.1). */
private const val LEVEL_TENTHS = 3

/** The bullseye's pair, stacked, is set a step below the tube's single readout. */
private val BULLSEYE_READOUT = 72.sp

/** One haptic tick each time the reading settles into level (SPEC.md §4.1). */
@Composable
private fun TickOnLevel(level: Boolean) {
    val haptics = rememberHaptics()
    LaunchedEffect(level) {
        if (level) haptics.tick()
    }
}

/**
 * `|value|` to one decimal place and "°" (SPEC.md §4.2): bold, tabular, white, sandstone
 * when level; 96 sp for the tube, 72 sp for each of the bullseye's pair, readable from a
 * foot away.
 */
@Composable
private fun Readout(tenths: Int, level: Boolean, modifier: Modifier = Modifier, size: TextUnit = Type.Readout.fontSize) {
    Text(
        text = "${tenths / 10}.${tenths % 10}°",
        modifier = modifier,
        maxLines = 1,
        style = Type.Readout.copy(color = if (level) Palette.Sandstone else Palette.Text, fontSize = size),
    )
}

/** The bullseye's axis letter before each readout (SPEC.md §4.3). */
@Composable
private fun AxisLabel(axis: String) {
    Text(
        text = axis,
        modifier = Modifier.padding(end = 12.dp, bottom = 22.dp),
        style = Type.Caption.copy(color = Palette.Sandstone, fontSize = 22.sp, letterSpacing = 0.sp),
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
