package com.showconfigs.showdist.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons

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
    Box(Modifier.fillMaxSize().background(Palette.DarkTyrianBlue).safeDrawingPadding()) {
        LandingRuler(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(RulerBandHeight))

        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, bottom = RulerBandHeight)) {
            Spacer(Modifier.height(76.dp))
            Text(
                text = "SHOWDIST",
                modifier = Modifier.fillMaxWidth(),
                style = BaseText.copy(
                    color = Palette.LightMauve,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            // Equal flexible spacers centre the cards between the wordmark and the ruler.
            Spacer(Modifier.weight(1f))
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
                description = "Hold flat against a wall to check plumb.",
                prominent = false,
                onClick = onLeveler,
            )
            Spacer(Modifier.weight(1f))
        }

        SettingsButton(onSettings, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
    }
}

/** Tool card (SPEC.md §1.4): MEASURE is prominent (peachRed), LEVELER quiet (olympicBlue). */
@Composable
private fun ToolCard(label: String, description: String, prominent: Boolean, onClick: () -> Unit) {
    val accent = if (prominent) Palette.PeachRed else Palette.OlympicBlue
    val shape = RoundedCornerShape(14.dp)
    val labelSize: TextUnit = if (prominent) 20.sp else 16.sp
    val arrowSize: Dp = if (prominent) 22.dp else 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (prominent) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(BorderStroke(1.dp, accent.copy(alpha = if (prominent) 0.9f else 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (prominent) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = BaseText.copy(fontSize = labelSize, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
            )
            Spacer(Modifier.height(if (prominent) 6.dp else 4.dp))
            Text(
                text = description,
                style = BaseText.copy(
                    color = Palette.WarmGray,
                    fontSize = if (prominent) 13.sp else 12.sp,
                    lineHeight = 1.35.em,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(ShowdistIcons.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(arrowSize))
    }
}

/** History link under Measure (SPEC.md §1.4). Opens History without starting the camera. */
@Composable
private fun HistoryLink(savedCount: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.5.dp).height(44.dp).background(Palette.PeachRed.copy(alpha = 0.6f)))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ShowdistIcons.History, contentDescription = null, tint = Palette.WarmGray, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("HISTORY", style = BaseText.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$savedCount saved",
                modifier = Modifier.weight(1f),
                style = BaseText.copy(color = Palette.WarmGray, fontSize = 12.sp),
            )
            Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.WarmGray, modifier = Modifier.size(20.dp))
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
                Icon(ShowdistIcons.SettingsOutlined, contentDescription = "Settings", tint = Palette.WarmGray)
            }
        }
    }
}
