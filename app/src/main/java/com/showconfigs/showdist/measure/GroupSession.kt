package com.showconfigs.showdist.measure

import com.showconfigs.showdist.ui.DialogText
import com.showconfigs.showdist.ui.ShowdistDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.history.GroupLine
import com.showconfigs.showdist.history.LineTag
import com.showconfigs.showdist.history.GroupStore
import com.showconfigs.showdist.history.MeasureGroup
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.LocalSnackbarHostState
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Longest line label or group name. */
const val NAME_MAX = 32

/**
 * A group being measured (SPEC.md §2.13): the labels given to its lines so far, and the
 * dialogs for a line's label and the group's name. The lines themselves are the tracker's
 * point pairs, each pair with its own anchors.
 */
@Stable
class GroupSession internal constructor(
    private val tracker: MeasureTracker,
    private val unitsNow: () -> UnitSystem,
    private val appendTo: String?,
    private val onAppended: () -> Unit,
    private val scope: CoroutineScope,
    private val notify: suspend (String) -> Unit,
) {
    /** Labels by line index; lines past the current count (after an undo) are ignored. */
    val labels = mutableStateMapOf<Int, String>()

    /** Length, Width or Height by line index (SPEC.md §2.14); each tag on one line at most. */
    val tags = mutableStateMapOf<Int, LineTag>()

    /** Lines whose tag the user has chosen (or cleared), so no suggestion overrides it. */
    private val tagChosen = mutableStateMapOf<Int, Boolean>()

    /**
     * Suggests a tag for a just-finished line: the first three lines get Length, Width and
     * Height, in that order, skipping any already used. Applied unless the user picks.
     */
    fun suggestTag(line: Int) {
        if (line >= 3 || tagChosen[line] == true || tags.containsKey(line)) return
        val next = LineTag.entries.firstOrNull { it !in tags.values } ?: return
        tags[line] = next
    }

    /** Sets (or clears, with null) a line's tag, taking it off any other line. */
    fun setTag(line: Int, tag: LineTag?) {
        tagChosen[line] = true
        if (tag == null) {
            tags.remove(line)
        } else {
            tags.entries.filter { it.value == tag && it.key != line }.map { it.key }.forEach { tags.remove(it) }
            tags[line] = tag
        }
    }

    var naming by mutableStateOf(false)
        private set
    var editingLine by mutableStateOf<Int?>(null)
        private set

    /** Adding to a saved group rather than starting one. */
    val appending: Boolean get() = appendTo != null

    /** The line just finished, while no new one has started; its card shows then. */
    val justFinished: Int?
        get() {
            val n = tracker.completedLines
            return if (n >= 1 && tracker.pointCount % 2 == 0 && !naming) n - 1 else null
        }

    fun editLabel(line: Int) {
        editingLine = line
    }

    fun setLabel(line: Int, label: String?) {
        val clean = label?.trim()?.take(NAME_MAX)?.takeIf { it.isNotEmpty() }
        if (clean == null) labels.remove(line) else labels[line] = clean
        editingLine = null
    }

    fun cancelLabel() {
        editingLine = null
    }

    /** Done (or hold anywhere): name a new group, or add the lines to the saved one. */
    fun finish() {
        if (tracker.completedLines == 0) return
        if (appendTo != null) {
            val g = GroupStore.byId(appendTo) ?: return
            GroupStore.update(g.copy(lines = g.lines + lines()))
            reset()
            onAppended()
        } else {
            naming = true
        }
    }

    fun cancelNaming() {
        naming = false
    }

    fun save(name: String) {
        val clean = name.trim().take(NAME_MAX).ifEmpty { defaultName() }
        val lines = lines()
        GroupStore.add(MeasureGroup.create(clean, lines, unitsNow()))
        naming = false
        reset()
        scope.launch { notify("Saved $clean · ${lines.size} line${if (lines.size == 1) "" else "s"}") }
    }

    internal fun dropTagsFrom(line: Int) {
        tags.keys.filter { it >= line }.forEach { tags.remove(it) }
        tagChosen.keys.filter { it >= line }.forEach { tagChosen.remove(it) }
    }

    fun defaultName(): String = "Group ${GroupStore.groups.size + 1}"

    /** The finished lines, re-read from their anchors (a half-drawn line is left out). */
    private fun lines(): List<GroupLine> {
        val pts = tracker.snapshotPoints()
        return List(tracker.completedLines) { i ->
            GroupLine(MeasureGroup.newLineId(), pts[i * 2], pts[i * 2 + 1], tracker.scale, labels[i], tags[i])
        }
    }

    private fun reset() {
        tracker.clear()
        labels.clear()
        tags.clear()
        tagChosen.clear()
    }
}

@Composable
fun rememberGroupSession(tracker: MeasureTracker, units: UnitSystem, appendTo: String?, onAppended: () -> Unit): GroupSession {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHostState.current
    val currentUnits by rememberUpdatedState(units)
    val currentOnAppended by rememberUpdatedState(onAppended)
    val session = remember(tracker, appendTo) {
        GroupSession(tracker, { currentUnits }, appendTo, { currentOnAppended() }, scope) { snackbar.showSnackbar(it) }
    }
    // Labels and tags belong to finished lines: an undo or clear drops those of lines no
    // longer there; a newly finished line gets its suggested tag.
    LaunchedEffect(tracker.pointCount) {
        val done = tracker.completedLines
        session.labels.keys.filter { it >= done }.forEach { session.labels.remove(it) }
        session.dropTagsFrom(done)
        if (done >= 1 && tracker.pointCount % 2 == 0) session.suggestTag(done - 1)
    }
    return session
}

/** Under the current line's length: how many lines so far, and Done (SPEC.md §2.13). */
@Composable
fun GroupBar(lines: Int, onDone: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "$lines line${if (lines == 1) "" else "s"}",
            Modifier.background(Palette.Scrim, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp),
            style = BaseText.copy(color = Palette.White, fontSize = 15.sp),
        )
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .background(Palette.Scrim, shape)
                .border(BorderStroke(1.5.dp, Palette.Accent), shape)
                .clickable(role = Role.Button, onClick = onDone)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("Done", style = BaseText.copy(color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * After a line is finished: its number, length and label, with a way to name it, and its
 * tag chips (SPEC.md §2.13, §2.14). Gone as soon as the next line starts.
 */
@Composable
fun LineCard(group: GroupSession, units: UnitSystem, length: Double, modifier: Modifier = Modifier) {
    val line = group.justFinished ?: return
    val label = group.labels[line]
    Column(
        modifier
            .background(Palette.Scrim, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(role = Role.Button, onClickLabel = "Label this line") { group.editLabel(line) }
                .heightIn(min = 44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "${label ?: "Line ${line + 1}"} · ${DistanceFormat.format(length, units)}",
                style = BaseText.copy(color = Palette.White, fontSize = 15.sp),
            )
            BasicText(
                if (label == null) "   Add label" else "   Edit",
                style = BaseText.copy(color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val current = group.tags[line]
            for (t in LineTag.entries) TagChip(t.label, selected = current == t) { group.setTag(line, if (current == t) null else t) }
            TagChip("None", selected = current == null) { group.setTag(line, null) }
        }
    }
}

/** A tag choice: a 48 dp target around a small pill, accent when selected. */
@Composable
fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            Modifier
                .then(
                    if (selected) Modifier.background(Palette.Accent, shape)
                    else Modifier.border(BorderStroke(1.dp, Palette.Accent.copy(alpha = 0.6f)), shape),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            style = BaseText.copy(
                color = if (selected) Palette.Black else Palette.Accent,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

/** The group's name and a line's label, asked in small dialogs. */
@Composable
fun GroupDialogs(group: GroupSession) {
    if (group.naming) {
        NameDialog(
            title = "Name this group",
            initial = "",
            placeholder = group.defaultName(),
            confirm = "Save",
            dismiss = "Keep measuring",
            onConfirm = group::save,
            onDismiss = group::cancelNaming,
        )
    }
    group.editingLine?.let { line ->
        NameDialog(
            title = "Label line ${line + 1}",
            initial = group.labels[line].orEmpty(),
            placeholder = "e.g. North wall",
            confirm = "Save",
            dismiss = "Skip",
            onConfirm = { group.setLabel(line, it) },
            onDismiss = group::cancelLabel,
        )
    }
}

/** A one-field dialog; an empty field confirms with [placeholder] where that's a default. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirm: String,
    dismiss: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ShowdistDialog(
        onDismiss = onDismiss,
        title = title,
        dismiss = dismiss,
        confirm = confirm,
        onConfirm = { onConfirm(text) },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(NAME_MAX) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = DialogText.Body.copy(color = Palette.TextMuted)) },
            singleLine = true,
            textStyle = DialogText.Body,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Palette.Accent,
                cursorColor = Palette.Accent,
            ),
        )
    }
}
