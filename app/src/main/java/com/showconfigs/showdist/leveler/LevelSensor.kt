package com.showconfigs.showdist.leveler

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import com.showconfigs.showdist.settings.Calibration
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val GRAVITY = 9.80665f
private const val ALPHA = 0.15f
/** The readouts' slower filter: a time constant of about 0.4 s at 50 Hz, so the numbers settle. */
private const val READOUT_ALPHA = 0.05f
private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

/**
 * Tilt from the accelerometer (SPEC.md §4.1, §4.3). A one-pole low-pass smooths x/g, y/g
 * and z/g.
 *
 * [roll] is the tube's single axis: measured against the nearest of portrait,
 * landscape-left and landscape-right, so it's always in −45..45° whatever way the UI is
 * rotated. Rotating the phone clockwise (viewed from the front) gives a negative reading.
 *
 * [tiltX] and [tiltY] are the bullseye's two axes, for a phone lying face up: positive
 * when the right edge, or the top edge, is the higher one.
 *
 * The readout* values come from a second, slower filter so the numbers don't flicker; the
 * bubble keeps the quicker one.
 */
class LevelSensor(private val sensorManager: SensorManager) : SensorEventListener {
    /** Smoothed roll in degrees. Snapshot state, updated about 50 times a second. */
    var roll by mutableFloatStateOf(0f)
        private set

    /** Degrees; positive when the right edge is higher. */
    var tiltX by mutableFloatStateOf(0f)
        private set

    /** Degrees; positive when the top edge is higher. */
    var tiltY by mutableFloatStateOf(0f)
        private set

    /** [roll], [tiltX] and [tiltY] through the readouts' slower filter. */
    var readoutRoll by mutableFloatStateOf(0f)
        private set
    var readoutTiltX by mutableFloatStateOf(0f)
        private set
    var readoutTiltY by mutableFloatStateOf(0f)
        private set

    private var estX = 0f
    private var estY = 0f
    private var estZ = 1f
    private var slowX = 0f
    private var slowY = 0f
    private var slowZ = 1f

    /** Start listening at game rate (~50 Hz). With no accelerometer the bubble just stays centred. */
    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // The calibrated level zero (SPEC.md §1.5a): the sensor's own offset, read lying flat.
        val x = event.values[0] / GRAVITY - Calibration.levelX
        val y = event.values[1] / GRAVITY - Calibration.levelY
        estX += (x - estX) * ALPHA
        estY += (y - estY) * ALPHA
        estZ += (event.values[2] / GRAVITY - estZ) * ALPHA
        // The accelerometer reads +g along whichever axis points up, so a raised right (or top)
        // edge gives a positive x (or y).
        tiltX = atan2(estX, estZ) * RAD_TO_DEG
        tiltY = atan2(estY, estZ) * RAD_TO_DEG
        val theta = atan2(estX, estY) * RAD_TO_DEG
        roll = theta - (theta / 90f).roundToInt() * 90f

        slowX += (x - slowX) * READOUT_ALPHA
        slowY += (y - slowY) * READOUT_ALPHA
        slowZ += (event.values[2] / GRAVITY - slowZ) * READOUT_ALPHA
        readoutTiltX = atan2(slowX, slowZ) * RAD_TO_DEG
        readoutTiltY = atan2(slowY, slowZ) * RAD_TO_DEG
        val slowTheta = atan2(slowX, slowY) * RAD_TO_DEG
        readoutRoll = slowTheta - (slowTheta / 90f).roundToInt() * 90f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
