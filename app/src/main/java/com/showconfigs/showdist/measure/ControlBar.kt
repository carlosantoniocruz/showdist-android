package com.showconfigs.showdist.measure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons

/**
 * The Measure control bar (SPEC.md §2.6): the M/FT unit toggle, Undo, the main button,
 * Clear and History, in five equal columns. The four small controls sit in fixed 60×60
 * slots that turn by [rotation] degrees to stay upright (SPEC.md §2.9); the bar itself and
 * the main button never rotate. [rotation] and [charge] are read only in the draw/layer phase, so
 * neither recomposes the bar.
 */
@Composable
fun ControlBar(
    canPlace: Boolean,
    hasPoints: Boolean,
    savedCount: Int,
    units: UnitSystem,
    charge: () -> Float,
    rotation: () -> Float,
    onUnits: (UnitSystem) -> Unit,
    onHistory: () -> Unit,
    onPlace: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Palette.Black)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarColumn { RotatingSlot(rotation) { UnitToggle(units, onUnits) } }
            BarColumn {
                RotatingSlot(rotation) { BarIconButton(ShowdistIcons.Undo, "Undo last point", hasPoints, onUndo) }
            }
            BarColumn { MainButton(canPlace, charge, onPlace) }
            BarColumn {
                RotatingSlot(rotation) { BarIconButton(ShowdistIcons.Close, "Clear all points", hasPoints, onClear) }
            }
            BarColumn { RotatingSlot(rotation) { HistoryButton(savedCount, onHistory) } }
        }
    }
}

@Composable
private fun RowScope.BarColumn(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Bare 24 dp icon in a 52×52 hit area; near-white at 0.9 enabled, 0.3 disabled. */
@Composable
private fun BarIconButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Palette.Text,
            modifier = Modifier.size(24.dp).alpha(if (enabled) 0.9f else 0.3f),
        )
    }
}

/** A fixed 60×60 slot turned about its centre by [rotation] degrees clockwise. */
@Composable
private fun RotatingSlot(rotation: () -> Float, content: @Composable () -> Unit) {
    Box(
        Modifier.size(60.dp).graphicsLayer { rotationZ = rotation() }.wrapContentSize(unbounded = true),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** "M" and "FT"; the active one is bold in its unit colour (cyan, amber), the other dimmed. */
@Composable
private fun UnitToggle(units: UnitSystem, onUnits: (UnitSystem) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        UnitLabel("M", Palette.Cyan, units == UnitSystem.Metric) { onUnits(UnitSystem.Metric) }
        UnitLabel("FT", Palette.Amber, units == UnitSystem.Imperial) { onUnits(UnitSystem.Imperial) }
    }
}

@Composable
private fun UnitLabel(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = active }
            .padding(horizontal = 7.dp, vertical = 16.dp),
        style = BaseText.copy(
            color = if (active) color else Palette.TextMuted.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.5.sp,
        ),
    )
}

/** History, with a count badge (white, 11 sp, bold) at the top-right once anything is saved. */
@Composable
private fun HistoryButton(savedCount: Int, onClick: () -> Unit) {
    Box {
        BarIconButton(ShowdistIcons.History, "History", enabled = true, onClick = onClick)
        if (savedCount > 0) {
            Text(
                "$savedCount",
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp),
                style = BaseText.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/**
 * The 96 dp main button: 46 dp ring (2 dp stroke) and a 16 dp centre dot in green, at 0.4
 * alpha when there's no reticle hit. While a save charges, a white arc sweeps clockwise
 * from 12 o'clock over the ring.
 */
@Composable
private fun MainButton(enabled: Boolean, charge: () -> Float, onPlace: () -> Unit) {
    val view = LocalView.current
    val accent = if (enabled) Palette.Green else Palette.Green.copy(alpha = 0.4f)
    Canvas(
        Modifier
            .size(96.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) {
                // Flutter's selectionClick maps to CLOCK_TICK on Android.
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onPlace()
            }
            .semantics { contentDescription = "Place point. Hold anywhere on screen to save measurement" },
    ) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(accent, radius = 46.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        drawCircle(accent, radius = 16.dp.toPx(), center = c)
        val sweep = charge()
        if (sweep > 0f) {
            val r = 46.dp.toPx()
            drawArc(
                Palette.White,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}
