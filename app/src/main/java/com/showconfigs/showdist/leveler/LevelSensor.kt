package com.showconfigs.showdist.leveler

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val GRAVITY = 9.80665f
private const val ALPHA = 0.15f
private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

/**
 * Single-axis roll from the accelerometer (SPEC.md §4.1). A one-pole low-pass smooths x/g
 * and y/g; roll is measured against the nearest of portrait, landscape-left and
 * landscape-right, so it's always in −45..45° whatever way the UI is rotated.
 * Rotating the phone clockwise (viewed from the front) gives a negative reading.
 */
class LevelSensor(private val sensorManager: SensorManager) : SensorEventListener {
    /** Smoothed roll in degrees. Snapshot state, updated about 50 times a second. */
    var roll by mutableFloatStateOf(0f)
        private set

    private var estX = 0f
    private var estY = 0f

    /** Start listening at game rate (~50 Hz). With no accelerometer the bubble just stays centred. */
    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        estX += (event.values[0] / GRAVITY - estX) * ALPHA
        estY += (event.values[1] / GRAVITY - estY) * ALPHA
        val theta = atan2(estX, estY) * RAD_TO_DEG
        roll = theta - (theta / 90f).roundToInt() * 90f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
