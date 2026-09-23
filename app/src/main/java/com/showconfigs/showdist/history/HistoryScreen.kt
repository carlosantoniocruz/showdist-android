package com.showconfigs.showdist.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.AppBarAction
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons

/** A pending delete: which ids, and the dialog wording for them. */
private class DeleteRequest(val ids: Set<String>, val title: String, val confirm: String)

/** History (SPEC.md §1.9): saved measurements, newest first, with selection and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(units: UnitSystem, onBack: () -> Unit) {
    val recordings = RecordingStore.recordings
    val export = rememberExportActions(units)
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    fun selectedInOrder() = recordings.filter { it.id in selected }

    // System Back leaves selection mode first, and only pops the screen on the next press.
    BackHandler(enabled = selecting) { exitSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionBar(
                    count = selected.size,
                    onCancel = ::exitSelection,
                    onSelectAll = { selected = recordings.map { it.id }.toSet() },
                    onShare = { export.share(selectedInOrder()) },
                    onCopy = { export.copy(selectedInOrder()) },
                    onDelete = {
                        val n = selected.size
                        pendingDelete = DeleteRequest(
                            selected,
                            if (n == 1) "Delete 1 measurement?" else "Delete $n measurements?",
                            "Delete",
                        )
                    },
                )
            } else {
                ShowdistAppBar("HISTORY", onBack) {
                    OverflowMenu(
                        enabled = recordings.isNotEmpty(),
                        onSelect = { selecting = true },
                        onDeleteAll = {
                            pendingDelete = DeleteRequest(
                                recordings.map { it.id }.toSet(),
                                "Delete all ${recordings.size} measurements?",
                                "Delete all",
                            )
                        },
                    )
                }
            }
        },
        containerColor = Palette.DarkTyrianBlue,
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No measurements yet.\nPlace two or more dots, then hold anywhere on screen to save.",
                    style = BaseText.copy(color = Palette.WarmGray, fontSize = 14.sp, lineHeight = 1.5.em, textAlign = TextAlign.Center),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(recordings, key = { _, r -> r.id }) { index, recording ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(start = 72.dp),
                        thickness = 1.dp,
                        color = Palette.White.copy(alpha = 0.08f),
                    )
                }
                HistoryRow(
                    recording = recording,
                    units = units,
                    selecting = selecting,
                    isSelected = recording.id in selected,
                    onClick = { if (selecting) toggle(recording.id) else openId = recording.id },
                    onLongClick = {
                        if (selecting) {
                            toggle(recording.id)
                        } else {
                            selecting = true
                            selected = setOf(recording.id)
                        }
                    },
                    onShare = { export.share(listOf(recording)) },
                    onCopy = { export.copy(listOf(recording)) },
                )
            }
        }
    }

    recordings.firstOrNull { it.id == openId }?.let { recording ->
        MeasurementSheet("MEASUREMENT", recording, units, onDismiss = { openId = null })
    }

    pendingDelete?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Palette.DarkTyrianBlue,
            title = { Text(request.title, style = BaseText.copy(fontSize = 18.sp, fontWeight = FontWeight.Normal)) },
            text = { Text("This can't be undone.", style = BaseText.copy(color = Palette.WarmGray, fontSize = 14.sp)) },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel", style = BaseText.copy(color = Palette.WarmGray, fontSize = 14.sp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    RecordingStore.remove(request.ids)
                    pendingDelete = null
                    exitSelection()
                }) {
                    Text(request.confirm, style = BaseText.copy(fontSize = 14.sp))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    recording: Recording,
    units: UnitSystem,
    selecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
) {
    val accent = if (isSelected) Palette.LightMauve else Palette.White
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) Palette.LightMauve.copy(alpha = 0.12f) else Palette.DarkTyrianBlue)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeThumbnail(recording, accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                DistanceFormat.format(recording.total, units),
                style = BaseText.copy(fontSize = 20.sp, fontWeight = FontWeight.Light),
            )
            Text("${recording.points.size} points", style = BaseText.copy(color = Palette.WarmGray, fontSize = 12.sp))
        }
        if (selecting) {
            Icon(
                if (isSelected) ShowdistIcons.CheckCircle else ShowdistIcons.CircleOutlined,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) Palette.LightMauve else Palette.WarmGray,
                modifier = Modifier.padding(end = 12.dp),
            )
        } else {
            ShareMenuButton(enabled = true, onShare = onShare, onCopy = onCopy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text("$count selected", style = BaseText.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal)) },
        navigationIcon = { AppBarAction(ShowdistIcons.Close, "Cancel selection", onClick = onCancel) },
        actions = {
            AppBarAction(ShowdistIcons.SelectAll, "Select all", onClick = onSelectAll)
            ShareMenuButton(enabled = count > 0, onShare = onShare, onCopy = onCopy)
            AppBarAction(
                ShowdistIcons.DeleteOutline,
                "Delete selected",
                enabled = count > 0,
                tint = Palette.PeachRed,
                onClick = onDelete,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Palette.DarkTyrianBlue,
            scrolledContainerColor = Palette.DarkTyrianBlue,
            titleContentColor = Palette.White,
        ),
    )
}

/** The app bar's "More" menu: Select, Delete all. Disabled when the list is empty. */
@Composable
private fun OverflowMenu(enabled: Boolean, onSelect: () -> Unit, onDeleteAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppBarAction(ShowdistIcons.MoreVert, "More", enabled = enabled) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.DarkTyrianBlue) {
            DropdownMenuItem(
                text = { Text("Select", style = BaseText.copy(fontSize = 14.sp)) },
                onClick = { open = false; onSelect() },
            )
            DropdownMenuItem(
                text = { Text("Delete all", style = BaseText.copy(fontSize = 14.sp)) },
                onClick = { open = false; onDeleteAll() },
            )
        }
    }
}
