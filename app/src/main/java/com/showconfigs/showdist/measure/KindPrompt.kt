package com.showconfigs.showdist.measure

import com.showconfigs.showdist.ui.DialogText
import com.showconfigs.showdist.ui.ShowdistDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type

/**
 * Opening AR Measure (SPEC.md §2.13): Chained or Group, as two large buttons. Settings can
 * make either one the default instead of asking.
 */
@Composable
fun KindPrompt(onPick: (MeasureKind) -> Unit, onDismiss: () -> Unit) {
    ShowdistDialog(onDismiss = onDismiss, title = "How will you measure?") {
        KindButton("Chained", "One continuous path, with a running total") { onPick(MeasureKind.Chained) }
        Spacer(Modifier.height(8.dp))
        KindButton("Group", "Separate lines for one thing: a room, a table, a cabinet") { onPick(MeasureKind.Group) }
    }
}

@Composable
private fun KindButton(title: String, detail: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(BorderStroke(1.5.dp, Palette.Sandstone), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = DialogText.Title.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(2.dp))
        Text(detail, style = DialogText.Secondary)
    }
}
