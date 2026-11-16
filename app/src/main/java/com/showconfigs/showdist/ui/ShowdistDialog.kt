package com.showconfigs.showdist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.showconfigs.showdist.settings.HandSetting

/** Dialog text is set a step below the screens' own: compact, like everything in a tray. */
private const val DIALOG_SCALE = 0.86f

/** Dialog text sizes: a modest title, the rest at body size. */
object DialogText {
    val Title = Type.Title.copy(fontSize = Type.Title.fontSize * DIALOG_SCALE, fontWeight = FontWeight.Normal)
    val Body = Type.Body.copy(fontSize = Type.Body.fontSize * DIALOG_SCALE)
    val Secondary = Type.Secondary.copy(fontSize = Type.Body.fontSize * DIALOG_SCALE)
    val Button = Type.Label.copy(fontSize = Type.Body.fontSize * DIALOG_SCALE)
}

/** The widest a dialog gets; with 24 dp kept clear at the screen's sides. */
private val DIALOG_MAX = 360.dp

/**
 * Every pop-up in the app (SPEC.md §1.2a): a compact tray at the bottom of the screen, in the
 * thumb zone, on Surface with a hairline edge and 4 dp corners, at most 360 dp wide. A
 * modest title, the content, then the buttons along its bottom edge, on the thumb side
 * (right, or left when [HandSetting] is left-handed). Tapping outside it dismisses.
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
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .widthIn(max = DIALOG_MAX)
                    .fillMaxWidth()
                    .background(Palette.Surface, Corner)
                    .border(1.dp, Palette.Divider, Corner)
                    // Taps on the tray itself aren't "outside".
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (confirm != null || dismiss != null) 2.dp else 14.dp),
            ) {
                if (title != null) {
                    Text(title, style = DialogText.Title)
                    Spacer(Modifier.height(8.dp))
                }
                content()
                if (confirm != null || dismiss != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = if (HandSetting.leftHanded) Arrangement.Absolute.Left else Arrangement.Absolute.Right,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismiss != null) {
                            TextButton(onClick = onDismiss) { Text(dismiss, style = DialogText.Button.copy(color = Palette.TextMuted)) }
                        }
                        if (confirm != null) {
                            TextButton(onClick = onConfirm) {
                                Text(confirm, style = DialogText.Button.copy(color = Palette.Accent, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}
