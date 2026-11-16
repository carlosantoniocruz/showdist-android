package com.showconfigs.showdist.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** A new orientation counts only after it has held this long (SPEC.md §2.9). */
private const val SETTLE_MS = 400L

/**
 * Hysteresis: an orientation is entered only within this many degrees of its upright, so the
 * 30° either side of each diagonal (and upside-down) changes nothing.
 */
private const val ENTER_DEG = 30f

/** Lying flatter than this (m/s² of gravity in the screen's plane), the turn can't be told: keep the last. */
private const val FLAT_THRESHOLD = 3.0f
private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

/** A tilt of more than 3° counts as the phone being moved: cos 3°. */
private const val MOTION_COS = 0.99863f

/** Each sample moves the gravity estimate this far toward the reading. */
private const val GRAVITY_SMOOTHING = 0.1f

/**
 * The app's one orientation handler (SPEC.md §2.9). The activity stays portrait; this says
 * how the phone is actually held, in quarter turns: 0 upright, +1 resting on its left edge
 * (content turns clockwise to read upright), −1 on its right edge. Screens lay themselves
 * out from [quarterTurns] and nothing else.
 *
 * It never flickers: gravity is low-pass filtered, a new orientation must sit within 30° of
 * its upright (so nothing changes near a diagonal, flat, or upside-down), and it must hold
 * for 400 ms before [quarterTurns] changes, once. The first reading after [start] is taken
 * at once, so a screen opened sideways is right from its first frame.
 */
class DeviceOrientation(private val sensorManager: SensorManager) : SensorEventListener {
    var quarterTurns by mutableIntStateOf(0)
        private set

    /** Called when the phone has tilted more than 3° from where it last counted (the idle pause, SPEC.md §2.1a). */
    var onMotion: () -> Unit = {}

    private var gX = 0f
    private var gY = 0f
    private var gZ = 0f
    private var refX = 0f
    private var refY = 0f
    private var refZ = 0f
    private var candidate = 0
    private var candidateSince = 0L
    private var first = true

    fun start() {
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        first = true
        gX = 0f
        gY = 0f
        gZ = 0f
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        // Gravity, low-pass filtered: a knock or a vibrating desk shakes the raw reading
        // without the phone having been moved.
        if (gX == 0f && gY == 0f && gZ == 0f) {
            gX = x
            gY = y
            gZ = z
        } else {
            gX += GRAVITY_SMOOTHING * (x - gX)
            gY += GRAVITY_SMOOTHING * (y - gY)
            gZ += GRAVITY_SMOOTHING * (z - gZ)
        }
        val dot = gX * refX + gY * refY + gZ * refZ
        val norms = sqrt((gX * gX + gY * gY + gZ * gZ) * (refX * refX + refY * refY + refZ * refZ))
        if (norms == 0f || dot / norms < MOTION_COS) {
            refX = gX
            refY = gY
            refZ = gZ
            onMotion()
        }

        // The first reading uses the raw sample: the filter hasn't settled yet.
        val ax = if (first) x else gX
        val ay = if (first) y else gY
        if (sqrt(ax * ax + ay * ay) < FLAT_THRESHOLD) return
        val deg = atan2(ax, ay) * RAD_TO_DEG
        val seen = when {
            abs(deg) < ENTER_DEG -> 0
            abs(deg - 90f) < ENTER_DEG -> 1
            abs(deg + 90f) < ENTER_DEG -> -1
            else -> return // near a diagonal or upside-down: nothing changes, and the timer keeps running
        }
        val now = SystemClock.uptimeMillis()
        if (first) {
            first = false
            candidate = seen
            candidateSince = now
            quarterTurns = seen
            return
        }
        if (seen != candidate) {
            candidate = seen
            candidateSince = now
        }
        if (candidate != quarterTurns && now - candidateSince >= SETTLE_MS) quarterTurns = candidate
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * The shared [DeviceOrientation], listening only while [lifecycle] is resumed: not in the
 * background, and not while its screen is covered or paused.
 */
@Composable
fun rememberDeviceOrientation(
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    onMotion: () -> Unit = {},
): DeviceOrientation {
    val context = LocalContext.current
    val currentOnMotion by rememberUpdatedState(onMotion)
    val orientation = remember {
        DeviceOrientation(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).apply {
            this.onMotion = { currentOnMotion() }
        }
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
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) orientation.start()
        onDispose {
            lifecycle.removeObserver(observer)
            orientation.stop()
        }
    }
    return orientation
}

/**
 * Lays the content out for a phone held [turns] quarter turns round, filling its parent: turned
 * sideways it is measured with the parent's width and height swapped, then rotated to read
 * upright, so a whole screen or a bar written for portrait works along the long edge too.
 */
fun Modifier.quarterTurned(turns: Int) = if (turns == 0) this else layout { measurable, constraints ->
    val swapped = Constraints(
        minWidth = constraints.minHeight,
        maxWidth = constraints.maxHeight,
        minHeight = constraints.minWidth,
        maxHeight = constraints.maxWidth,
    )
    val placeable = measurable.measure(swapped)
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2) {
            rotationZ = 90f * turns
        }
    }
}

/**
 * Turns the content [turns] quarter turns about its centre at its own size, taking its
 * rotated footprint in layout: for a pill or a small stack placed against an edge.
 */
fun Modifier.turned(turns: Int) = if (turns == 0) this else layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer((placeable.height - placeable.width) / 2, (placeable.width - placeable.height) / 2) {
            rotationZ = 90f * turns
        }
    }
}
