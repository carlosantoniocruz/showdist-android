package com.showconfigs.showdist.settings

import com.showconfigs.showdist.ui.BackBar
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import com.showconfigs.showdist.ui.readingColumn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** A custom length must be within this range, in metres. */
private const val MIN_CUSTOM_M = 0.05
private const val MAX_CUSTOM_M = 10.0

/** Flat enough for a level zero: under about 5° of tilt, face up. */
private const val FLAT_TILT = 0.09f

/**
 * Measurement scale (SPEC.md §1.5a): one instruction, a choice of known object (or a length
 * the user enters), and one button that opens Measure to place a point at each end. The
 * outcome shows here when they come back; Reset clears the correction.
 */
@Composable
fun CalibrateScaleScreen(units: UnitSystem, onMeasure: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var choice by rememberSaveable { mutableStateOf(Calibration.target?.first ?: KnownObject.Letter) }
    var custom by rememberSaveable { mutableStateOf("") }
    val metric = units == UnitSystem.Metric
    val customMetres = custom.replace(',', '.').toDoubleOrNull()?.let { if (metric) it / 100 else it * 0.0254 }
    val customOk = customMetres != null && customMetres in MIN_CUSTOM_M..MAX_CUSTOM_M
    val ready = choice != KnownObject.Custom || customOk
    // A result belongs to this visit: it's cleared when the screen is left (for Settings, or
    // for Measure, which sets a new one on the way back).
    DisposableEffect(Unit) { onDispose { Calibration.lastResult = null } }

    CalibrateScaffold("MEASUREMENT SCALE", onBack) {
        Instruction("Measure something you know.")
        Calibration.lastResult?.let { Result(it.message, ok = it is ScaleResult.Fixed) }
        Spacer(Modifier.height(8.dp))
        for (o in KnownObject.entries) {
            val selected = o == choice
            SettingsRow(o.label, role = Role.RadioButton, onClick = { choice = o }) {
                Icon(
                    if (selected) ShowdistIcons.RadioButtonChecked else ShowdistIcons.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) Palette.Sandstone else Palette.TextMuted,
                )
            }
        }
        if (choice == KnownObject.Custom) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(8) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Its true length, in ${if (metric) "cm" else "inches"}") },
                singleLine = true,
                isError = custom.isNotEmpty() && !customOk,
                supportingText = {
                    if (custom.isNotEmpty() && !customOk) Text(if (metric) "Between 5 cm and 10 m" else "Between 2 in and 390 in")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = Type.Label,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Sandstone,
                    focusedLabelColor = Palette.Sandstone,
                    cursorColor = Palette.Sandstone,
                ),
            )
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Measure", enabled = ready) {
            val metres = if (choice == KnownObject.Custom) customMetres ?: return@PrimaryButton else choice.metres!!
            Calibration.target = choice to metres
            Calibration.lastResult = null
            onMeasure()
        }
        if (Calibration.hasScale) {
            Spacer(Modifier.height(12.dp))
            ResetLink("Reset (${correctionText(Calibration.scale)} now applied)") { Calibration.resetScale(context) }
        }
    }
}

/** "+2.0%" or "−1.5%": how much measurements are lengthened or shortened. */
fun correctionText(scale: Double): String {
    val pct = (scale - 1) * 100
    return (if (pct >= 0) "+" else "−") + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(pct)) + "%"
}

/**
 * Level zero (SPEC.md §1.5a): one instruction and "Set zero", which averages a second of the
 * accelerometer lying flat and stores it as the level's zero; Reset clears it.
 */
@Composable
fun CalibrateLevelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sampler = remember { FlatSampler(context) }
    DisposableEffect(sampler) {
        sampler.start()
        onDispose { sampler.stop() }
    }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }

    CalibrateScaffold("LEVEL ZERO", onBack) {
        Instruction("Place your phone flat on a surface you trust, then tap Set zero.")
        message?.let { (text, ok) -> Result(text, ok) }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(if (busy) "Hold still…" else "Set zero", enabled = !busy) {
            busy = true
            scope.launch {
                sampler.reset()
                delay(1_000)
                val (x, y, z) = sampler.mean()
                busy = false
                message = if (z > 0 && sqrt(x * x + y * y) < FLAT_TILT) {
                    Calibration.setLevelZero(context, x, y)
                    "Zero set. Both levels now read 0 here." to true
                } else {
                    "Lay the phone flat, screen up, then try again." to false
                }
            }
        }
        if (Calibration.hasLevelZero) {
            Spacer(Modifier.height(12.dp))
            ResetLink("Reset") {
                Calibration.resetLevelZero(context)
                message = "Back to the sensor's own zero." to true
            }
        }
    }
}

/** Averages raw x/g, y/g and z/g from the accelerometer since the last [reset]. */
private class FlatSampler(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var sx = 0.0
    private var sy = 0.0
    private var sz = 0.0
    private var n = 0

    fun start() {
        manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = manager.unregisterListener(this)

    @Synchronized
    fun reset() {
        sx = 0.0; sy = 0.0; sz = 0.0; n = 0
    }

    @Synchronized
    fun mean(): Triple<Float, Float, Float> =
        if (n == 0) Triple(0f, 0f, 0f) else Triple((sx / n).toFloat(), (sy / n).toFloat(), (sz / n).toFloat())

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        sx += event.values[0] / SensorManager.STANDARD_GRAVITY
        sy += event.values[1] / SensorManager.STANDARD_GRAVITY
        sz += event.values[2] / SensorManager.STANDARD_GRAVITY
        n++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
private fun CalibrateScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { ShowdistAppBar(title) }, bottomBar = { BackBar(onBack) }, containerColor = Palette.Black) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.readingColumn().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
    }
}

@Composable
private fun Instruction(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 16.dp), style = Type.Body)
}

@Composable
private fun Result(text: String, ok: Boolean) {
    Spacer(Modifier.height(16.dp))
    Text(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        style = Type.Label.copy(color = if (ok) Palette.Sandstone else Palette.Attention, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val color = if (enabled) Palette.Sandstone else Palette.Sandstone.copy(alpha = 0.35f)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(BorderStroke(1.5.dp, color), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.Label.copy(color = color, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun ResetLink(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.Secondary)
    }
}
