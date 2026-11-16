package com.showconfigs.showdist.measure

import com.showconfigs.showdist.ui.Corner
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import kotlinx.coroutines.delay

/** How long "Ready" stays before the pill fades out. */
private const val READY_HOLD_MS = 1200L

/** A problem must last this long before the pill comes back, so a flickering hit can't blink it. */
private const val SHOW_AFTER_MS = 400L

private const val FADE_IN_MS = 250
private const val FADE_OUT_MS = 600

private fun TrackingStatus.dot(): Color = when (this) {
    // One accent only: cyan when ready, white when tracking has failed, grey while it settles.
    TrackingStatus.Ready -> Palette.Accent
    TrackingStatus.Lost, TrackingStatus.TooDark, TrackingStatus.CameraUnavailable -> Palette.White
    else -> Palette.Hint
}

/**
 * The tracking status pill (SPEC.md §2.3a): a small scrim pill with a coloured dot and the
 * [status] in plain words. It fades out once tracking is ready and comes back when it isn't.
 * Only its own layer's alpha animates, so fading redraws nothing else.
 */
@Composable
fun TrackingPill(status: TrackingStatus, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(status) {
        if (status == TrackingStatus.Ready) {
            delay(READY_HOLD_MS)
            shown = false
        } else {
            if (!shown) delay(SHOW_AFTER_MS)
            shown = true
        }
    }
    val alpha = animateFloatAsState(
        if (shown) 1f else 0f,
        tween(if (shown) FADE_IN_MS else FADE_OUT_MS),
        label = "trackingPillAlpha",
    )
    val dot by animateColorAsState(status.dot(), tween(300), label = "trackingPillDot")
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(Palette.Scrim, Corner)
            .padding(start = 12.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(8.dp).drawBehind { drawCircle(dot) })
        Spacer(Modifier.width(8.dp))
        Crossfade(status, animationSpec = tween(250), label = "trackingPillText") { s ->
            BasicText(s.text, style = BaseText.copy(color = Palette.White, fontSize = 14.sp, letterSpacing = 0.3.sp))
        }
    }
}
