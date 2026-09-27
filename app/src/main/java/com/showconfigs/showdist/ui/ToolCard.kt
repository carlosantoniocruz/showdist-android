package com.showconfigs.showdist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Expressive corners for the menu's tools (SPEC.md §1.4). */
private val ToolShape = RoundedCornerShape(28.dp)

/** Fills the tools so the background motif doesn't show through them. */
private val ToolFill = Palette.SurfaceHigh.copy(alpha = 0.92f)

/**
 * A wide tool card (SPEC.md §1.4): a [leading] picture, the label, a one-line description
 * and an arrow in [accent], on a raised fill with a thin [accent] outline.
 */
@Composable
fun ToolCard(
    label: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ToolShape)
            .background(ToolFill)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.7f)), ToolShape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = BaseText.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
            Spacer(Modifier.height(4.dp))
            Text(description, style = BaseText.copy(color = Palette.TextMuted, fontSize = 12.sp, lineHeight = 1.35.em))
        }
        Spacer(Modifier.width(12.dp))
        Icon(ShowdistIcons.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

/**
 * A half-width tool tile (SPEC.md §1.4): a [picture] over the label and a short
 * description, with the same fill and outline as [ToolCard].
 */
@Composable
fun ToolTile(
    label: String,
    description: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(ToolShape)
            .background(ToolFill)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.7f)), ToolShape)
            .clickable(onClick = onClick)
            .padding(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            picture()
            Spacer(Modifier.weight(1f))
            Icon(ShowdistIcons.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(label, style = BaseText.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
        Spacer(Modifier.height(4.dp))
        Text(description, style = BaseText.copy(color = Palette.TextMuted, fontSize = 11.sp, lineHeight = 1.35.em))
    }
}
