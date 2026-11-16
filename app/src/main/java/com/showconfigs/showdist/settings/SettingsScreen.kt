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
 * Settings (SPEC.md §1.5), and only these: vibration, the hand the bottom rows are laid out
 * for, the calibration tools, About and Licenses. Units are not here: the toggle in Measure
 * is their one home.
 */
@Composable
fun SettingsScreen(
    onCalibrateScale: () -> Unit,
    onWarmUp: () -> Unit,
    onLevelZero: () -> Unit,
    onAbout: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { ShowdistAppBar("SETTINGS") }, bottomBar = { BackBar(onBack) }, containerColor = Palette.Black) { padding ->
        // A compact list in a centred column on wide screens (SPEC.md §6.5); every divider
        // has the same small gap above and below, and nothing stretches to fill the screen.
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.readingColumn().padding(top = 4.dp, bottom = 16.dp)) {
                    SectionTitle("HAPTICS")
                    VibrationOption()
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    SectionTitle("LAYOUT")
                    HandOption("Right-handed", left = false)
                    HandOption("Left-handed", left = true)
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    SectionTitle("CALIBRATION")
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
                    SettingsDivider(Modifier.padding(vertical = SECTION_GAP))
                    SettingsRow("About", onClick = onAbout) { Chevron() }
                    SettingsRow("Licenses", onClick = onLicenses) { Chevron() }
                }
            }
        }
    }
}

/** The gap above and below each divider between Settings sections. */
private val SECTION_GAP = 8.dp

/** Which thumb the bottom rows are laid out for (SPEC.md §1.5); the selected radio is accent. */
@Composable
private fun HandOption(label: String, left: Boolean) {
    val context = LocalContext.current
    val selected = HandSetting.leftHanded == left
    SettingsRow(label, role = Role.RadioButton, onClick = { HandSetting.set(context, left) }) {
        Icon(
            if (selected) ShowdistIcons.RadioButtonChecked else ShowdistIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Palette.Accent else Palette.TextMuted,
        )
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
                checkedTrackColor = Palette.Accent,
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
            .heightIn(min = if (subtitle == null) 48.dp else 58.dp)
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

/** 1 dp accent divider (SPEC.md §1.5, §1.7). */
@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Divider)
}

@Composable
internal fun Chevron() {
    Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.TextMuted)
}
