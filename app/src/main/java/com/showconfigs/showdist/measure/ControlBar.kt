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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons

/**
 * The Measure control bar (SPEC.md §2.6): five equal columns. History (1) and the M/FT
 * toggle (5) arrive in later stages; their columns are left empty so the layout is final.
 */
@Composable
fun ControlBar(
    canPlace: Boolean,
    hasPoints: Boolean,
    onPlace: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(Palette.DarkTyrianBlue)) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Palette.DarkCitrine))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarColumn {}
            BarColumn { BarIconButton(ShowdistIcons.Undo, "Undo last point", hasPoints, onUndo) }
            BarColumn { MainButton(canPlace, onPlace) }
            BarColumn { BarIconButton(ShowdistIcons.Close, "Clear all points", hasPoints, onClear) }
            BarColumn {}
        }
    }
}

@Composable
private fun RowScope.BarColumn(content: @Composable () -> Unit) {
    Box(Modifier.weight(1f).wrapContentSize(unbounded = true), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Bare 24 dp icon in a 52×52 hit area; white at 0.9 enabled, 0.3 disabled. */
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
            tint = Palette.White,
            modifier = Modifier.size(24.dp).alpha(if (enabled) 0.9f else 0.3f),
        )
    }
}

/**
 * The 96 dp main button: 46 dp ring (1.5 dp stroke) and a 14 dp centre dot in peachRed,
 * at 0.4 alpha when there's no reticle hit. The save-charge arc arrives with hold-to-save.
 */
@Composable
private fun MainButton(enabled: Boolean, onPlace: () -> Unit) {
    val view = LocalView.current
    val accent = if (enabled) Palette.PeachRed else Palette.PeachRed.copy(alpha = 0.4f)
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
        drawCircle(accent, radius = 46.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
        drawCircle(accent, radius = 14.dp.toPx(), center = c)
    }
}
