package com.showconfigs.showdist.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.ArScheme
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type

/** Settings (SPEC.md §1.5): the unit system and line colours. Licenses are reached from About. */
@Composable
fun SettingsScreen(unitSettings: UnitSettings, schemeSettings: ArSchemeSettings, onBack: () -> Unit) {
    Scaffold(topBar = { ShowdistAppBar("SETTINGS", onBack) }, containerColor = Palette.Black) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        ) {
            SectionTitle("UNITS")
            UnitOption("Imperial (ft, in)", UnitSystem.Imperial, unitSettings)
            UnitOption("Metric (m, cm)", UnitSystem.Metric, unitSettings)
            SectionTitle("LINE COLOURS")
            for (scheme in ArScheme.entries) SchemeOption(scheme, schemeSettings)
        }
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

/** A line-colour option: its name, a preview of its dot, solid and dashed marks, a radio. */
@Composable
private fun SchemeOption(scheme: ArScheme, schemeSettings: ArSchemeSettings) {
    val selected = schemeSettings.scheme == scheme
    SettingsRow(scheme.label, role = Role.RadioButton, onClick = { schemeSettings.select(scheme) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SchemePreview(scheme)
            Spacer(Modifier.width(16.dp))
            Icon(
                if (selected) ShowdistIcons.RadioButtonChecked else ShowdistIcons.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Palette.Sandstone else Palette.TextMuted,
            )
        }
    }
}

/** A dot on a solid segment, then a dashed run, at the AR view's sizes (SPEC.md §3.2). */
@Composable
private fun SchemePreview(scheme: ArScheme) {
    Canvas(Modifier.size(width = 96.dp, height = 16.dp)) {
        val y = size.height / 2f
        val solidEnd = size.width * 0.45f
        drawLine(scheme.segment, Offset(6.dp.toPx(), y), Offset(solidEnd, y), 2.5.dp.toPx(), StrokeCap.Round)
        drawLine(
            scheme.live, Offset(solidEnd + 12.dp.toPx(), y), Offset(size.width - 1.5.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 9.dp.toPx())),
        )
        drawCircle(scheme.point, 6.dp.toPx(), Offset(6.dp.toPx(), y))
        drawCircle(scheme.point, 6.dp.toPx(), Offset(solidEnd, y))
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
            .heightIn(min = 64.dp)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
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
