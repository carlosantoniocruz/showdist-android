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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Tool card (SPEC.md §1.4): a prominent card (peach, inkSurface fill) or a quiet one
 * (olympicBlue, no fill), with an optional [leading] picture. Used on the main menu and
 * the Leveler picker.
 */
@Composable
fun ToolCard(
    label: String,
    description: String,
    prominent: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val accent = if (prominent) Palette.Peach else Palette.OlympicBlue
    val shape = RoundedCornerShape(14.dp)
    val labelSize: TextUnit = if (prominent) 20.sp else 16.sp
    val arrowSize: Dp = if (prominent) 22.dp else 18.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (prominent) Palette.InkSurface else Color.Transparent)
            .border(BorderStroke(1.dp, accent.copy(alpha = if (prominent) 0.9f else 0.45f)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (prominent) 18.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = BaseText.copy(fontSize = labelSize, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp),
            )
            Spacer(Modifier.height(if (prominent) 6.dp else 4.dp))
            Text(
                text = description,
                style = BaseText.copy(
                    color = Palette.WarmGrey,
                    fontSize = if (prominent) 13.sp else 12.sp,
                    lineHeight = 1.35.em,
                ),
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(ShowdistIcons.ArrowForward, contentDescription = null, tint = accent, modifier = Modifier.size(arrowSize))
    }
}
