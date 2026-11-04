package com.showconfigs.showdist.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The widest text-heavy content gets on tablets and wide windows (SPEC.md §6.5). Decided by
 * the space available, never by device type: on phones (narrower than this) nothing changes.
 */
val ReadingWidth = 520.dp

/** Fills the width, but keeps the content in a centred column at most [ReadingWidth] wide. */
fun Modifier.readingColumn(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadingWidth)

/**
 * For scrolling lists, which should stay scrollable edge to edge: gives [content] the side
 * padding that centres a [ReadingWidth] column in the space available (0 on phones).
 */
@Composable
fun ReadingSides(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable (side: Dp) -> Unit) {
    BoxWithConstraints(modifier) {
        content(((maxWidth - ReadingWidth) / 2).coerceAtLeast(0.dp))
    }
}
