package com.showconfigs.showdist.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.CaptionText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons

/**
 * Settings (SPEC.md §1.5): the unit system and the licenses.
 * About lives in the main menu's header.
 */
@Composable
fun SettingsScreen(
    unitSettings: UnitSettings,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { ShowdistAppBar("SETTINGS", onBack) }, containerColor = Palette.Black) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            SectionTitle("UNITS")
            UnitOption("Imperial (ft, in)", UnitSystem.Imperial, unitSettings)
            UnitOption("Metric (m, cm)", UnitSystem.Metric, unitSettings)
            Spacer(Modifier.height(16.dp))
            SettingsDivider(Modifier.padding(horizontal = 20.dp))
            SectionTitle("ABOUT")
            SettingsRow(
                "Licenses",
                subtitle = "ARCore, SceneView, AndroidX and JetBrains Mono (OFL-1.1)",
                onClick = onLicenses,
            ) { Chevron() }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
        style = CaptionText.copy(textAlign = TextAlign.Start),
    )
}

/** The selected radio is amber for imperial and cyan for metric, the unit colours (SPEC.md §5). */
@Composable
private fun UnitOption(label: String, value: UnitSystem, unitSettings: UnitSettings) {
    val selected = unitSettings.units == value
    SettingsRow(label, role = Role.RadioButton, onClick = { unitSettings.select(value) }) {
        Icon(
            if (selected) ShowdistIcons.RadioButtonChecked else ShowdistIcons.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Palette.unit(value) else Palette.TextMuted,
        )
    }
}

/**
 * A tappable list row: near-white 16 sp title, optional muted 12 sp subtitle, trailing icon.
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
            .heightIn(min = 56.dp)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = BaseText.copy(fontSize = 16.sp))
            if (subtitle != null) Text(subtitle, style = BaseText.copy(color = Palette.TextMuted, fontSize = 12.sp))
        }
        Spacer(Modifier.width(16.dp))
        trailing()
    }
}

/** 1 dp divider in the grid colour (SPEC.md §1.5, §1.7). */
@Composable
internal fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Palette.Grid)
}

@Composable
internal fun Chevron() {
    Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.TextMuted)
}
