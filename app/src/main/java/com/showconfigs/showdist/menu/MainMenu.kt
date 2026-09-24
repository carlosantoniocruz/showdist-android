package com.showconfigs.showdist.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.ToolCard

private val RulerBandHeight = 92.dp

/**
 * The main menu (SPEC.md §1.4). It never touches the camera or the AR session.
 * [savedCount] drives the History link, shown only when something has been saved.
 */
@Composable
fun MainMenu(
    savedCount: Int,
    onMeasure: () -> Unit,
    onLeveler: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Palette.Ink).safeDrawingPadding()) {
        LandingRuler(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RulerBandHeight))

        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = RulerBandHeight)) {
            // Equal flexible spacers centre the wordmark, its dimension line and the cards
            // between the top and the ruler.
            Spacer(Modifier.weight(1f))
            // The dimension line spans exactly the wordmark's width.
            Column(Modifier.align(Alignment.CenterHorizontally).width(IntrinsicSize.Max)) {
                Text(
                    text = "SHOWDIST",
                    style = BaseText.copy(
                        color = Palette.Citrine,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                DimensionLine(Modifier.fillMaxWidth().height(20.dp))
            }
            Spacer(Modifier.height(40.dp))
            ToolCard(
                label = "MEASURE",
                description = "Point the camera, tap two spots, get the distance.",
                prominent = true,
                onClick = onMeasure,
            )
            if (savedCount > 0) HistoryLink(savedCount, onHistory)
            Spacer(Modifier.height(20.dp))
            ToolCard(
                label = "LEVELER",
                description = "Check level and plumb with a tube or bullseye.",
                prominent = false,
                onClick = onLeveler,
            )
            Spacer(Modifier.weight(1f))
        }

        SettingsButton(onSettings, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
    }
}

/**
 * The app icon's dimension line (SPEC.md §1.4, §8.1), stretched to the wordmark's width:
 * a citrine rule with an end tick at each end, and a peach point on each end.
 */
@Composable
private fun DimensionLine(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val r = 5.dp.toPx()
        val cy = size.height / 2f
        val x0 = r
        val x1 = size.width - r
        drawLine(Palette.Citrine, Offset(x0, cy), Offset(x1, cy), stroke, StrokeCap.Round)
        for (x in floatArrayOf(x0, x1)) {
            drawLine(Palette.Citrine, Offset(x, stroke / 2f), Offset(x, size.height - stroke / 2f), stroke, StrokeCap.Round)
            drawCircle(Palette.Peach, r, Offset(x, cy))
        }
    }
}

/** History link under Measure (SPEC.md §1.4). Opens History without starting the camera. */
@Composable
private fun HistoryLink(savedCount: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.5.dp).height(44.dp).background(Palette.Peach.copy(alpha = 0.6f)))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ShowdistIcons.History, contentDescription = null, tint = Palette.WarmGrey, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("HISTORY", style = BaseText.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$savedCount saved",
                modifier = Modifier.weight(1f),
                style = BaseText.copy(color = Palette.WarmGrey, fontSize = 12.sp),
            )
            Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.WarmGrey, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // TooltipBox doesn't pass alignment on to its anchor, so position a wrapper instead.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text("Settings") } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = onClick) {
                Icon(ShowdistIcons.SettingsOutlined, contentDescription = "Settings", tint = Palette.WarmGrey)
            }
        }
    }
}
