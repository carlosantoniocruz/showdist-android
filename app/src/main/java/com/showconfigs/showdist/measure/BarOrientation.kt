package com.showconfigs.showdist.measure

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private const val SAMPLE_PERIOD_US = 200_000 // ~5 Hz
private const val FLAT_THRESHOLD = 3.0f // m/s²
private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

/**
 * Quarter-turns that keep the bar's small controls upright (SPEC.md §2.9): 0 upright,
 * +1 on the phone's left edge (controls turn clockwise), −1 on its right edge. Flat,
 * upside-down and the ±10° bands around the diagonals keep the current value.
 */
private class BarOrientation(private val sensorManager: SensorManager) : SensorEventListener {
    var quarterTurns by mutableIntStateOf(0)
        private set

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SAMPLE_PERIOD_US)
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        if (sqrt(x * x + y * y) < FLAT_THRESHOLD) return
        val deg = atan2(x, y) * RAD_TO_DEG
        when {
            abs(deg) < 35f -> quarterTurns = 0
            deg > 55f && deg < 125f -> quarterTurns = 1
            deg < -55f && deg > -125f -> quarterTurns = -1
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * The bar controls' rotation in degrees (clockwise), animated over 250 ms ease-out.
 * The sensor listens only while [lifecycle] is resumed: not in the background, and not
 * while History covers Measure.
 */
@Composable
fun rememberBarRotation(lifecycle: Lifecycle): State<Float> {
    val context = LocalContext.current
    val orientation = remember {
        BarOrientation(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }
    DisposableEffect(lifecycle, orientation) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> orientation.start()
                Lifecycle.Event.ON_PAUSE -> orientation.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            orientation.stop()
        }
    }
    return animateFloatAsState(
        targetValue = orientation.quarterTurns * 90f,
        animationSpec = tween(250, easing = EaseOut),
        label = "barRotation",
    )
}
