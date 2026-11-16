package com.showconfigs.showdist.measure

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type

/** How long tracking must stay poor before the warm-up is offered (SPEC.md §1.5a). */
const val WARM_UP_PROMPT_MS = 5_000L

/** The ring counts as full from here, so a last percent can't keep the user waiting. */
private const val FULL = 0.97f

/**
 * The tracking warm-up (SPEC.md §1.5a): one instruction, a ring that fills as tracking
 * settles ([quality], 0..1), and "Done" once it's full. Back closes it too. The camera,
 * points and session carry on underneath.
 */
@Composable
fun WarmUpOverlay(quality: () -> Float, onDone: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onDone)
    val fill by animateFloatAsState(quality(), tween(300), label = "warmUp")
    val full by remember { derivedStateOf { quality() >= FULL } }
    Column(
        modifier
            .widthIn(max = 320.dp)
            .padding(horizontal = 24.dp)
            .background(Palette.Scrim, RoundedCornerShape(24.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(
            "Move your phone slowly side to side over a textured surface",
            style = BaseText.copy(color = Palette.White, fontSize = 16.sp, textAlign = TextAlign.Center, lineHeight = 22.sp),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .size(88.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(88.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2f
                val arcSize = size.copy(width = size.width - stroke, height = size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(Palette.White.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                drawArc(
                    if (full) Palette.Accent else Palette.Accent,
                    -90f,
                    360f * (if (full) 1f else fill),
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .heightIn(min = 48.dp)
                .clip(shape)
                .border(BorderStroke(1.5.dp, if (full) Palette.Accent else Palette.Accent.copy(alpha = 0.35f)), shape)
                .clickable(enabled = full, role = Role.Button, onClick = onDone)
                .padding(horizontal = 32.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                "Done",
                style = Type.Label.copy(
                    color = if (full) Palette.Accent else Palette.Accent.copy(alpha = 0.35f),
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Offered over the status pill when tracking has stayed poor (SPEC.md §1.5a). */
@Composable
fun WarmUpPrompt(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(Palette.Scrim, shape)
            .border(BorderStroke(1.dp, Palette.Accent), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Tracking is struggling. Warm up tracking" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Warm up tracking", style = BaseText.copy(color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
