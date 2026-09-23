package com.showconfigs.showdist.measure

import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val MB = 1024 * 1024

/**
 * TEMPORARY (Stage 3): anchor count and memory, refreshed once per second.
 * "session" counts the anchors ARCore still holds, so a leak shows up as it exceeding "pts".
 * Native heap is shown too because ARCore's memory lives there, not on the Java heap.
 */
@Composable
fun DebugOverlay(tracker: MeasureTracker, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(tracker) {
        val runtime = Runtime.getRuntime()
        while (true) {
            val javaMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
            val nativeMb = Debug.getNativeHeapAllocatedSize() / MB
            text = "pts ${tracker.pointCount} · session ${tracker.sessionAnchorCount}\n" +
                "java $javaMb MB · native $nativeMb MB"
            delay(1000)
        }
    }
    BasicText(
        text = text,
        modifier = modifier.background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 3.dp),
        style = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
    )
}
