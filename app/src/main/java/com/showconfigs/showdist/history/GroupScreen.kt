package com.showconfigs.showdist.history

import com.showconfigs.showdist.ui.DialogText
import com.showconfigs.showdist.ui.ShowdistDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.measure.DistanceFormat
import com.showconfigs.showdist.measure.NameDialog
import com.showconfigs.showdist.measure.UnitSystem
import com.showconfigs.showdist.ui.AppBarAction
import com.showconfigs.showdist.ui.BackBar
import com.showconfigs.showdist.ui.FEATURES_TABULAR
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ReadingSides
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type

/** What a rename or delete dialog is for. */
private sealed interface GroupEdit {
    data object Name : GroupEdit
    data class Label(val line: Int) : GroupEdit
    data class Delete(val line: Int) : GroupEdit
}

/**
 * A saved group (SPEC.md §2.13): its name, then each line with its label and length. The
 * name and each label can be changed by tapping them, lines deleted, and more lines added
 * in AR ([onAddLines], null while another Measure is open). Share, Copy and PDF act on
 * the whole group.
 */
@Composable
fun GroupScreen(groupId: String?, units: UnitSystem, onAddLines: ((String) -> Unit)?, onBack: () -> Unit) {
    val group = GroupStore.byId(groupId)
    // Gone (deleted elsewhere): nothing to show.
    LaunchedEffect(group == null) { if (group == null) onBack() }
    if (group == null) return
    val export = rememberExportActions(units)
    val exportPdf = rememberPdfExport(units)
    var edit by remember { mutableStateOf<GroupEdit?>(null) }

    Scaffold(
        topBar = {
            ShowdistAppBar("GROUP") {
                AppBarAction(ShowdistIcons.PictureAsPdf, "Export PDF") { exportPdf(listOf(HistoryEntry.Group(group))) }
                ShareMenuButton(
                    enabled = true,
                    onShare = { export.shareEntries(listOf(HistoryEntry.Group(group))) },
                    onCopy = { export.copyEntries(listOf(HistoryEntry.Group(group))) },
                )
            }
        },
        bottomBar = { BackBar(onBack) },
        containerColor = Palette.Black,
    ) { padding ->
        ReadingSides(Modifier.fillMaxSize().padding(padding)) { side ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Rename group") { edit = GroupEdit.Name }
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(group.name, Modifier.weight(1f), style = Type.Title.copy(color = Palette.Sandstone, fontSize = 24.sp))
                        Icon(ShowdistIcons.Edit, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.padding(12.dp).size(20.dp))
                    }
                    Text(
                        "${group.lines.size} line${if (group.lines.size == 1) "" else "s"} · ${group.shortDate}",
                        Modifier.padding(start = 20.dp, bottom = 12.dp),
                        style = Type.Secondary,
                    )
                    HorizontalDivider(thickness = 1.dp, color = Palette.Divider)
                }
                itemsIndexed(group.lines, key = { _, l -> l.id }) { i, line ->
                    LineRow(
                        number = i + 1,
                        name = group.lineName(i),
                        labelled = line.label != null,
                        length = DistanceFormat.format(line.metres, units),
                        onRename = { edit = GroupEdit.Label(i) },
                        onDelete = { edit = GroupEdit.Delete(i) },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 1.dp, color = Palette.Divider)
                }
                if (onAddLines != null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                            AddLinesButton { onAddLines(group.id) }
                        }
                    }
                }
            }
        }
    }

    when (val e = edit) {
        GroupEdit.Name -> NameDialog(
            title = "Rename group",
            initial = group.name,
            placeholder = group.name,
            confirm = "Save",
            dismiss = "Cancel",
            onConfirm = {
                val name = it.trim().ifEmpty { group.name }
                GroupStore.update(group.copy(name = name))
                edit = null
            },
            onDismiss = { edit = null },
        )
        is GroupEdit.Label -> NameDialog(
            title = "Label line ${e.line + 1}",
            initial = group.lines[e.line].label.orEmpty(),
            placeholder = "e.g. North wall",
            confirm = "Save",
            dismiss = "Cancel",
            onConfirm = { text ->
                val label = text.trim().takeIf { it.isNotEmpty() }
                GroupStore.update(group.copy(lines = group.lines.mapIndexed { j, l -> if (j == e.line) l.with(label = label) else l }))
                edit = null
            },
            onDismiss = { edit = null },
        )
        is GroupEdit.Delete -> ShowdistDialog(
            onDismiss = { edit = null },
            title = "Delete ${group.lineName(e.line)}?",
            dismiss = "Cancel",
            confirm = "Delete",
            onConfirm = {
                GroupStore.update(group.copy(lines = group.lines.filterIndexed { j, _ -> j != e.line }))
                edit = null
            },
        ) {
            Text("This can't be undone.", style = DialogText.Secondary)
        }
        null -> Unit
    }
}

@Composable
private fun LineRow(number: Int, name: String, labelled: Boolean, length: String, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Rename line", onClick = onRename)
            .heightIn(min = 64.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", Modifier.width(36.dp), style = Type.Secondary.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
        Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
            Text(name, style = Type.Label.copy(color = if (labelled) Palette.Text else Palette.TextMuted))
            Text(length, style = Type.Value.copy(fontSize = 20.sp, fontFeatureSettings = FEATURES_TABULAR))
        }
        IconButton(onClick = onDelete) {
            Icon(ShowdistIcons.DeleteOutline, contentDescription = "Delete $name", tint = Palette.TextMuted)
        }
    }
}

@Composable
private fun AddLinesButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(BorderStroke(1.5.dp, Palette.Sandstone), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ShowdistIcons.Add, contentDescription = null, tint = Palette.Sandstone, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add lines", style = Type.Label.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
    }
}
