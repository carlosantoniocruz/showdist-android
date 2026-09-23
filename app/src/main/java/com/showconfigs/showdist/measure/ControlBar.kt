package com.showconfigs.showdist.measure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette

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
            BarColumn { BarIconButton(UndoIcon, "Undo last point", hasPoints, onUndo) }
            BarColumn { MainButton(canPlace, onPlace) }
            BarColumn { BarIconButton(CloseIcon, "Clear all points", hasPoints, onClear) }
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
        Image(
            painter = rememberVectorPainter(icon),
            contentDescription = null,
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

/** Material Symbols Rounded "undo", white. */
private val UndoIcon: ImageVector = materialIcon(
    "Undo",
    "M12.5 8c-2.65 0-5.05.99-6.9 2.6L3.71 8.71C3.08 8.08 2 8.52 2 9.41V15c0 .55.45 1 1 1h5.59" +
        "c.89 0 1.34-1.08.71-1.71l-1.91-1.91c1.39-1.16 3.16-1.88 5.12-1.88 3.16 0 5.89 1.84 7.19 4.5" +
        ".27.56.91.84 1.5.64.71-.23 1.07-1.04.75-1.72C20.23 10.42 16.65 8 12.5 8z",
)

/** Material Symbols Rounded "close", white. */
private val CloseIcon: ImageVector = materialIcon(
    "Close",
    "M18.3 5.71c-.39-.39-1.02-.39-1.41 0L12 10.59 7.11 5.7c-.39-.39-1.02-.39-1.41 0-.39.39-.39 1.02 0 1.41" +
        "L10.59 12 5.7 16.89c-.39.39-.39 1.02 0 1.41.39.39 1.02.39 1.41 0L12 13.41l4.89 4.89" +
        "c.39.39 1.02.39 1.41 0 .39-.39.39-1.02 0-1.41L13.41 12l4.89-4.89c.38-.38.38-1.02 0-1.4z",
)

private fun materialIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Palette.White))
        .build()
