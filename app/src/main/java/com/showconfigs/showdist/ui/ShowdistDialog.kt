package com.showconfigs.showdist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The main menu guide's scale (SPEC.md §1.4), which dialog text follows. */
private const val MENU_SCALE = 0.86f

/** Dialog text at the main menu's sizes: titles like a tool's name, the rest like its paragraph. */
object DialogText {
    val Title = Type.Title.copy(fontSize = Type.Title.fontSize * MENU_SCALE, fontWeight = FontWeight.Normal)
    val Body = Type.Body.copy(fontSize = Type.Body.fontSize * MENU_SCALE)
    val Secondary = Type.Secondary.copy(fontSize = Type.Body.fontSize * MENU_SCALE)
    val Button = Type.Label.copy(fontSize = Type.Body.fontSize * MENU_SCALE)
}

/** The widest a dialog gets; with 24 dp kept clear at the screen's sides. */
private val DIALOG_MAX = 360.dp

/**
 * Every pop-up in the app (SPEC.md §1.2a): a compact card on Surface, 20 dp corners, at
 * most 360 dp wide, with tight padding, a modest title and the buttons close under the
 * content, so nothing is wasted. [content] goes between the title and the buttons.
 */
@Composable
fun ShowdistDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    confirm: String? = null,
    onConfirm: () -> Unit = {},
    dismiss: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .widthIn(max = DIALOG_MAX)
                .fillMaxWidth()
                .background(Palette.Surface, RoundedCornerShape(20.dp))
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = if (confirm != null || dismiss != null) 4.dp else 16.dp),
        ) {
            if (title != null) {
                Text(title, style = DialogText.Title)
                Spacer(Modifier.height(10.dp))
            }
            content()
            if (confirm != null || dismiss != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (dismiss != null) {
                        TextButton(onClick = onDismiss) { Text(dismiss, style = DialogText.Button.copy(color = Palette.TextMuted)) }
                    }
                    if (confirm != null) {
                        TextButton(onClick = onConfirm) {
                            Text(confirm, style = DialogText.Button.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
