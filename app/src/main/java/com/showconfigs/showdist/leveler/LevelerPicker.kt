package com.showconfigs.showdist.leveler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ToolCard

/** Chooses the tube or the bullseye before the Leveler opens (SPEC.md §4). */
@Composable
fun LevelerPicker(onPick: (LevelerMode) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { ShowdistAppBar("LEVELER", onBack) }, containerColor = Palette.Ink) { padding ->
        // The cards sit as a group, centred on the whole screen rather than the space under
        // the app bar, so only the bottom inset is applied.
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ToolCard(
                label = "TUBE",
                description = "Hold the phone's edge against a wall or beam to check plumb or level along one line.",
                prominent = false,
                onClick = { onPick(LevelerMode.Tube) },
                leading = { TubePicture() },
            )
            Spacer(Modifier.height(20.dp))
            ToolCard(
                label = "BULLSEYE",
                description = "Lay the phone flat on a surface to check it front to back and side to side.",
                prominent = false,
                onClick = { onPick(LevelerMode.Bullseye) },
                leading = { BullseyePicture() },
            )
        }
    }
}

private val PictureSize = 52.dp

/** A small tube vial, as drawn on the tube Leveler (SPEC.md §4.2): marks and a centred bubble. */
@Composable
private fun TubePicture() {
    Canvas(Modifier.size(PictureSize)) {
        val stroke = 1.5.dp.toPx()
        val thickness = 18.dp.toPx()
        val vial = Size(size.width, thickness)
        val topLeft = Offset(0f, (size.height - thickness) / 2f)
        val radius = CornerRadius(thickness / 2f)
        drawRoundRect(Palette.InkSurface, topLeft, vial, radius)
        drawRoundRect(Palette.Citrine, topLeft, vial, radius, style = Stroke(stroke))
        val bubbleR = 5.5.dp.toPx()
        val mark = bubbleR + 3.dp.toPx()
        val span = thickness / 2f - 4.dp.toPx()
        for (x in floatArrayOf(center.x - mark, center.x + mark)) {
            drawLine(Palette.Citrine, Offset(x, center.y - span), Offset(x, center.y + span), stroke)
        }
        drawCircle(Palette.White, bubbleR, center)
    }
}

/** A small bullseye, as drawn on the bullseye Leveler (SPEC.md §4.3): crosshairs, ring and bubble. */
@Composable
private fun BullseyePicture() {
    Canvas(Modifier.size(PictureSize)) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f - stroke
        drawCircle(Palette.InkSurface, radius, center)
        drawCircle(Palette.Citrine, radius, center, style = Stroke(stroke))
        val faint = Palette.Citrine.copy(alpha = 0.35f)
        drawLine(faint, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(faint, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
        val bubbleR = 5.5.dp.toPx()
        drawCircle(Palette.Citrine, bubbleR + 3.dp.toPx(), center, style = Stroke(stroke))
        drawCircle(Palette.White, bubbleR, center)
    }
}
