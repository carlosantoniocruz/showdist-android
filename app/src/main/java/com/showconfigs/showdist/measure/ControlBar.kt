package com.showconfigs.showdist.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BarIconButton
import com.showconfigs.showdist.ui.BarItem
import com.showconfigs.showdist.ui.BarSlot
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.BottomRow
import com.showconfigs.showdist.ui.BottomRowCentre
import com.showconfigs.showdist.ui.BottomRowSlot
import com.showconfigs.showdist.ui.ModeSwitcher
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.rememberHaptics

/**
 * Measure's bottom row (SPEC.md §2.6). From the centre outward: the peach-red capture
 * button, the largest control; on the navigation side the Measure/Level switcher, then
 * History; on the tool side Undo, Clear, and Settings, the smallest, at the edge. The gaps
 * between the controls as drawn are all equal ([BottomRow]). The row
 * never moves: held sideways, each small control turns in place by [rotation] degrees to read
 * upright (SPEC.md §2.9), and the round capture button needs no turning. In a [task] (calibrating, warming up, adding lines to a saved group) the
 * navigation side holds only Back. [rotation] and [charge] are read only in
 * the draw/layer phase, so neither recomposes the row.
 */
@Composable
fun ControlBar(
    canPlace: Boolean,
    hasPoints: Boolean,
    savedCount: Int,
    task: Boolean,
    charge: () -> Float,
    onPlace: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onLevel: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
) {
    BottomRow(
        modifier,
        nav = if (task) {
            listOf(BarItem { BarSlot(BottomRowSlot, rotation) { BarIconButton(ShowdistIcons.ArrowBack, "Back", tint = Palette.Accent, onClick = onBack) } })
        } else {
            listOf(
                BarItem(visual = 56.dp) { BarSlot(BottomRowSlot, rotation) { ModeSwitcher(levelActive = false, onSwitch = onLevel) } },
                BarItem { BarSlot(BottomRowSlot, rotation) { HistoryButton(savedCount, onHistory) } },
            )
        },
        centre = { CaptureButton(canPlace, charge, onPlace) },
        tools = listOfNotNull(
            BarItem { BarSlot(BottomRowSlot, rotation) { BarIconButton(ShowdistIcons.Undo, "Undo last point", hasPoints, onClick = onUndo) } },
            BarItem { BarSlot(BottomRowSlot, rotation) { BarIconButton(ShowdistIcons.Close, "Clear all points", hasPoints, onClick = onClear) } },
            if (task) null else BarItem {
                BarSlot(BottomRowSlot, rotation) {
                    BarIconButton(ShowdistIcons.SettingsOutlined, "Settings", iconSize = 20.dp, onClick = onSettings)
                }
            },
        ),
    )
}

/** History, with a count badge (white, 12 sp, bold) at the top-right once anything is saved. */
@Composable
private fun HistoryButton(savedCount: Int, onClick: () -> Unit) {
    Box {
        BarIconButton(ShowdistIcons.History, "History", onClick = onClick)
        if (savedCount > 0) {
            Text(
                "$savedCount",
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp),
                style = BaseText.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/**
 * The capture button (SPEC.md §2.6), 68 dp: a peach-red disc inside a peach-red ring, at
 * 0.4 alpha when there's no reticle hit. While a save charges, a white arc sweeps clockwise
 * from 12 o'clock over the ring.
 */
@Composable
private fun CaptureButton(enabled: Boolean, charge: () -> Float, onPlace: () -> Unit) {
    val haptics = rememberHaptics()
    val colour = if (enabled) Palette.Capture else Palette.Capture.copy(alpha = 0.4f)
    Canvas(
        Modifier
            .size(BottomRowCentre)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                haptics.tick()
                onPlace()
            }
            .semantics { contentDescription = "Place point. Hold anywhere on screen to save measurement" },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val ring = 32.dp.toPx()
        drawCircle(colour, radius = ring, center = c, style = Stroke(3.dp.toPx()))
        drawCircle(colour, radius = 25.dp.toPx(), center = c)
        val sweep = charge()
        if (sweep > 0f) {
            drawArc(
                Palette.White,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(c.x - ring, c.y - ring),
                size = Size(2 * ring, 2 * ring),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * The unit toggle (SPEC.md §2.6), just above the bottom row beside the live readout: plain
 * text naming the current system, "ft in" or "m cm"; a tap flips it.
 */
@Composable
fun UnitToggle(units: UnitSystem, onUnits: (UnitSystem) -> Unit, modifier: Modifier = Modifier) {
    val metric = units == UnitSystem.Metric
    TextToggle(
        if (metric) "m cm" else "ft in",
        if (metric) "Units: metres and centimetres. Switch to feet and inches" else "Units: feet and inches. Switch to metres and centimetres",
        modifier,
    ) { onUnits(if (metric) UnitSystem.Imperial else UnitSystem.Metric) }
}

/**
 * How points join (SPEC.md §2.13), beside the unit toggle while nothing is placed: "chain"
 * or "group"; a tap flips it. Its only home since Settings was slimmed.
 */
@Composable
fun KindToggle(kind: MeasureKind, onKind: (MeasureKind) -> Unit, modifier: Modifier = Modifier) {
    val group = kind == MeasureKind.Group
    TextToggle(
        if (group) "group" else "chain",
        if (group) "Measuring separate lines as a group. Switch to one chained path" else "Measuring one chained path. Switch to separate lines as a group",
        modifier,
    ) { onKind(if (group) MeasureKind.Chained else MeasureKind.Group) }
}

/** A plain-text toggle over the camera: a 48 dp target around a scrim pill, with a light tick. */
@Composable
private fun TextToggle(text: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            Modifier.background(Palette.Scrim, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
            style = BaseText.copy(color = Palette.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        )
    }
}
