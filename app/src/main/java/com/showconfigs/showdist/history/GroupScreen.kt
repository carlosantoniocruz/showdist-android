package com.showconfigs.showdist.history

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * in AR ([onAddLines]). Share, Copy and PDF act on
 * the whole group.
 */
@Composable
fun GroupScreen(groupId: String?, units: UnitSystem, onAddLines: (String) -> Unit, onBack: () -> Unit) {
    val group = GroupStore.byId(groupId)
    // Gone (deleted elsewhere): nothing to show.
    LaunchedEffect(group == null) { if (group == null) onBack() }
    if (group == null) return
    val export = rememberExportActions(units)
    val exportPdf = rememberPdfExport(units)
    var edit by remember { mutableStateOf<GroupEdit?>(null) }

    Scaffold(
        topBar = { ShowdistAppBar("GROUP") },
        bottomBar = {
            BackBar(onBack) {
                AppBarAction(ShowdistIcons.PictureAsPdf, "Export PDF") { exportPdf(listOf(HistoryEntry.Group(group))) }
                ShareMenuButton(
                    enabled = true,
                    onShare = { export.shareEntries(listOf(HistoryEntry.Group(group))) },
                    onCopy = { export.copyEntries(listOf(HistoryEntry.Group(group))) },
                )
            }
        },
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
                        tag = line.tag,
                        onTag = { tag -> GroupStore.update(group.withTag(i, tag)) },
                        onRename = { edit = GroupEdit.Label(i) },
                        onDelete = { edit = GroupEdit.Delete(i) },
                    )
                    HorizontalDivider(Modifier.padding(start = 56.dp), thickness = 1.dp, color = Palette.Divider)
                }
                // Area and volume, at the bottom, smaller than the lines (SPEC.md §2.14).
                val results = group.resultLines(units)
                if (results.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(start = 56.dp, end = 20.dp, top = 14.dp)) {
                            for (line in results.dropLast(1)) {
                                Text(line, style = Type.Label.copy(fontSize = 15.sp, fontFeatureSettings = FEATURES_TABULAR))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(results.last(), style = Type.Secondary.copy(fontSize = 13.sp))
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                        AddLinesButton { onAddLines(group.id) }
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
private fun LineRow(
    number: Int,
    name: String,
    labelled: Boolean,
    length: String,
    tag: LineTag?,
    onTag: (LineTag?) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
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
        TagButton(tag, onTag)
        IconButton(onClick = onDelete) {
            Icon(ShowdistIcons.DeleteOutline, contentDescription = "Delete $name", tint = Palette.TextMuted)
        }
    }
}

/** The line's tag, or "Tag" when it has none; tapping offers Length, Width, Height or None. */
@Composable
private fun TagButton(tag: LineTag?, onTag: (LineTag?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClickLabel = "Change tag") { open = true }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                tag?.label?.uppercase() ?: "TAG",
                style = Type.Caption.copy(
                    color = if (tag != null) Palette.Sandstone else Palette.Hint,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Surface) {
            for (t in listOf(LineTag.Length, LineTag.Width, LineTag.Height, null)) {
                DropdownMenuItem(
                    text = { Text(t?.label ?: "None", style = Type.Label.copy(color = if (t == tag) Palette.Sandstone else Palette.Text)) },
                    onClick = {
                        open = false
                        onTag(t)
                    },
                )
            }
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
