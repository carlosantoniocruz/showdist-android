package com.showconfigs.showdist.settings

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.showconfigs.showdist.measure.MeasureKind
import com.showconfigs.showdist.ui.BackBar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalContext
import com.showconfigs.showdist.ui.HapticsSetting
import com.showconfigs.showdist.ui.readingColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type

/**
 * Settings (SPEC.md §1.5), kept short: units, calibration, feedback, and Additional Info,
 * which leads to About (and from there the licenses).
 */
@Composable
fun SettingsScreen(
    unitSettings: UnitSettings,
    onCalibrateScale: () -> Unit,
    onWarmUp: () -> Unit,
    onLevelZero: () -> Unit,
    onFormulas: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { ShowdistAppBar("SETTINGS") }, bottomBar = { BackBar(onBack) }, containerColor = Palette.Black) { padding ->
        // Scrolls edge to edge; the rows sit in a centred column on wide screens (SPEC.md §6.5).
        // A compact list; every divider has the same gap above and below, so the sections are
        // evenly spaced without stretching to fill the screen.
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.readingColumn().padding(top = 8.dp, bottom = 24.dp)) {
                    Column {
                        SectionTitle("MEASURING")
                        UnitOption("Imperial (ft, in)", UnitSystem.Imperial, unitSettings)
                        UnitOption("Metric (m, cm)", UnitSystem.Metric, unitSettings)
                        MeasureKindOption()
                    }
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    Column {
                        SectionTitle("CALIBRATE")
                        SettingsRow(
                            "Measurement scale",
                            subtitle = if (Calibration.hasScale) "${correctionText(Calibration.scale)} applied" else "Measure something you know",
                            onClick = onCalibrateScale,
                        ) { Chevron() }
                        SettingsRow("Tracking warm-up", subtitle = "Move your phone slowly", onClick = onWarmUp) { Chevron() }
                        SettingsRow(
                            "Level zero",
                            subtitle = if (Calibration.hasLevelZero) "Zero set" else "Set zero on a surface you trust",
                            onClick = onLevelZero,
                        ) { Chevron() }
                    }
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    Column {
                        SectionTitle("FEEDBACK")
                        VibrationOption()
                    }
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    Column {
                        SectionTitle("ADDITIONAL INFO")
                        SettingsRow("Handy formulas", subtitle = "Shapes, percentages, conversions", onClick = onFormulas) { Chevron() }
                        SettingsRow("About", subtitle = "Showdist, the developer and licenses", onClick = onAbout) { Chevron() }
                    }
                }
            }
        }
    }
}

/** The gap above and below each divider between Settings sections. */
private val SECTION_GAP = 8.dp

/**
 * How AR Measure opens (SPEC.md §2.13): ask Chained or Group each time, or always one. The
 * prompt's "Remember my choice" sets it too; "Ask each time" undoes that.
 */
@Composable
private fun MeasureKindOption() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = MeasureKindSetting.remembered
    val label = { k: MeasureKind? ->
        when (k) {
            null -> "Ask each time"
            MeasureKind.Chained -> "Always chained"
            MeasureKind.Group -> "Always group"
        }
    }
    Box {
        SettingsRow("Measure mode", subtitle = label(current), onClick = { open = true }) { Chevron() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Surface) {
            for (k in listOf(null, MeasureKind.Chained, MeasureKind.Group)) {
                DropdownMenuItem(
                    text = { Text(label(k), style = Type.Label.copy(color = if (k == current) Palette.Sandstone else Palette.Text)) },
                    onClick = {
                        MeasureKindSetting.set(context, k)
                        open = false
                    },
                )
            }
        }
    }
}

/** The app-wide Vibration switch (SPEC.md §1.5); the system's touch feedback still applies. */
@Composable
private fun VibrationOption() {
    val context = LocalContext.current
    val on = HapticsSetting.enabled
    SettingsRow(
        "Vibration",
        subtitle = "Taps and pulses as you measure and level",
        role = Role.Switch,
        onClick = { HapticsSetting.set(context, !on) },
    ) {
        Switch(
            checked = on,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Black,
                checkedTrackColor = Palette.Sandstone,
                uncheckedThumbColor = Palette.TextMuted,
                uncheckedTrackColor = Palette.SurfaceHigh,
                uncheckedBorderColor = Palette.TextMuted,
            ),
        )
    }
}

@Composable
internal fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)
            .semantics { heading() },
        style = Type.Caption.copy(textAlign = TextAlign.Start),
    )
}

/** The selected radio is sandstone (SPEC.md §5). */
@Composable
private fun UnitOption(label: String, value: UnitSystem, unitSettings: UnitSettings) {
    val selected = unitSettings.units == value
    SettingsRow(label, role = Role.RadioButton, onClick = { unitSettings.select(value) }) {
        Icon(
            if (selected) ShowdistIcons.RadioButtonChecked else ShowdistIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Palette.Sandstone else Palette.TextMuted,
        )
    }
}

/**
 * A tappable list row: a white title, an optional grey subtitle, a trailing icon.
 * [horizontalPadding] is 16 dp like a standard list tile.
 */
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String? = null,
    horizontalPadding: Dp = 16.dp,
    role: Role? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Single-line rows are compact; rows with a subtitle get room for both lines.
            .heightIn(min = if (subtitle == null) 52.dp else 64.dp)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = if (subtitle == null) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Label)
            if (subtitle != null) Text(subtitle, style = Type.Secondary)
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

/** 1 dp sandstone divider (SPEC.md §1.5, §1.7). */
@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Divider)
}

@Composable
internal fun Chevron() {
    Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.TextMuted)
}
