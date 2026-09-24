package com.showconfigs.showdist.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.CaptionText
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.LocalSnackbarHostState
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import kotlinx.coroutines.launch

/** Runs Share / Copy text and reports the outcome in the app's snackbar. */
class ExportActions(private val run: (List<Recording>, share: Boolean) -> Unit) {
    fun share(recordings: List<Recording>) = run(recordings, true)
    fun copy(recordings: List<Recording>) = run(recordings, false)
}

@Composable
fun rememberExportActions(units: UnitSystem): ExportActions {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    return ExportActions { recordings, share ->
        scope.launch {
            val message = if (share) Export.share(context, recordings, units) else Export.copy(context, recordings, units)
            if (message != null) snackbar.showSnackbar(message)
        }
    }
}

/**
 * The measurement sheet (SPEC.md §2.7): "SAVED" after saving, "MEASUREMENT" from History.
 * Share and Copy close the sheet first, then act. With [onViewInAr] (History only), a
 * "View in AR" button projects the recording in Measure (SPEC.md §2.11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementSheet(
    title: String,
    recording: Recording,
    units: UnitSystem,
    onDismiss: () -> Unit,
    onViewInAr: (() -> Unit)? = null,
) {
    val export = rememberExportActions(units)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f

    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.InkSurface,
    ) {
        Column(
            Modifier
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
        ) {
            Text(title, style = CaptionText.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start))
            Spacer(Modifier.height(10.dp))
            Text(
                DistanceFormat.format(recording.total, units),
                style = BaseText.copy(fontSize = 44.sp, fontWeight = FontWeight.ExtraLight, fontFeatureSettings = FEATURES_TABULAR),
            )
            Spacer(Modifier.height(4.dp))
            Text(recording.countsLine(), style = BaseText.copy(color = Palette.WarmGrey, fontSize = 13.sp))
            Spacer(Modifier.height(18.dp))
            recording.segments.forEachIndexed { i, length ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(32.dp), style = BaseText.copy(color = Palette.WarmGrey, fontSize = 13.sp))
                    Text(
                        DistanceFormat.format(length, units),
                        style = BaseText.copy(fontSize = 15.sp, fontFeatureSettings = FEATURES_TABULAR),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (onViewInAr != null && recording.points.size >= 2) {
                Button(
                    onClick = { closeThen(onViewInAr) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Peach, contentColor = Palette.White),
                ) { Text("View in AR", style = BaseText.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { closeThen { export.share(listOf(recording)) } },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Palette.White.copy(alpha = 0.3f)),
            ) { IconLabel(ShowdistIcons.IosShare, "Share", Palette.White) }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { closeThen { export.copy(listOf(recording)) } }, modifier = Modifier.fillMaxWidth()) {
                IconLabel(ShowdistIcons.ContentCopy, "Copy text", Palette.White)
            }
            TextButton(onClick = { closeThen {} }, modifier = Modifier.fillMaxWidth()) {
                Text("Done", style = BaseText.copy(color = Palette.WarmGrey, fontSize = 14.sp))
            }
        }
    }
}

@Composable
private fun IconLabel(icon: ImageVector, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = BaseText.copy(color = color, fontSize = 14.sp))
    }
}

/**
 * The share-menu button (SPEC.md §2.7): an ios_share icon opening "Share" and
 * "Copy text". White when enabled, warmGrey when disabled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMenuButton(enabled: Boolean, onShare: () -> Unit, onCopy: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
            tooltip = { PlainTooltip { Text("Share or copy") } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = { open = true },
                enabled = enabled,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Palette.White,
                    disabledContentColor = Palette.WarmGrey,
                ),
            ) {
                Icon(ShowdistIcons.IosShare, contentDescription = "Share or copy", modifier = Modifier.size(22.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.InkSurface) {
            MenuItem(ShowdistIcons.IosShare, "Share") { open = false; onShare() }
            MenuItem(ShowdistIcons.ContentCopy, "Copy text") { open = false; onCopy() }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = BaseText.copy(fontSize = 14.sp)) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = Palette.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
    )
}
