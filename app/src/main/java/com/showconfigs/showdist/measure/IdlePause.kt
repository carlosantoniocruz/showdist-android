package com.showconfigs.showdist.measure

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette

/** With no touch and no meaningful movement for this long, Measure pauses itself (SPEC.md §2.1a). */
const val IDLE_PAUSE_MS = 60_000L

/** How often the idle time is checked; nothing runs per frame for it. */
const val IDLE_CHECK_MS = 5_000L

/**
 * When the user last did something in Measure: touched the screen or moved the phone.
 * A plain timestamp, not state, so noting activity never recomposes anything.
 */
class IdleClock {
    var lastNanos = System.nanoTime()
        private set

    fun touch() {
        lastNanos = System.nanoTime()
    }

    fun idleMs(otherActivityNanos: Long): Long = (System.nanoTime() - maxOf(lastNanos, otherActivityNanos)) / 1_000_000
}

/** Notes every touch on the content as activity, without consuming anything. */
fun Modifier.notesTouches(clock: IdleClock) = pointerInput(clock) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            clock.touch()
        }
    }
}

/**
 * Shown over all of Measure while it is paused for idleness (SPEC.md §2.1a): the frozen
 * camera view dimmed (no blur: CLAUDE.md), and one calm line. A tap anywhere resumes, and
 * does nothing else: it never reaches the controls underneath.
 */
@Composable
fun IdlePauseOverlay(visible: Boolean, onResume: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(600)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Black.copy(alpha = 0.8f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Continue measuring",
                    onClick = onResume,
                ),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "Tap to continue",
                style = BaseText.copy(color = Palette.TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Light, letterSpacing = 0.5.sp),
            )
        }
    }
}
