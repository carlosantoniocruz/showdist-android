package com.showconfigs.showdist.menu

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons

/** The History pulse's soft white, a touch warm so it reads as the sandstone lit up. */
private val SoftWhite = Color(0xFFFFF8EE)

/** One full pulse, tan to white and back (SPEC.md §1.4). */
private const val PULSE_MS = 3_600

/** The steady tint shown instead of the pulse when animations are off. */
private const val STILL_TINT = 0.6f

/** Touch target (a circle) and the icon in it. */
private val TARGET = 56.dp
private val ICON = 30.dp
private val GAP = 40.dp
private val GLOW = 6.dp

/**
 * History and Settings in one centred row near the bottom (SPEC.md §1.4), within easy
 * reach of either thumb: just the icons, no containers. History shows only once something
 * is saved, and then pulses gently ([pulsing]); with [reduceMotion] it holds a steady
 * soft white tint instead.
 */
@Composable
fun MenuButtons(
    showHistory: Boolean,
    savedCount: Int,
    pulsing: Boolean,
    reduceMotion: Boolean,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        if (showHistory) {
            val glow = if (reduceMotion || !pulsing) {
                remember { mutableFloatStateOf(STILL_TINT) }
            } else {
                rememberInfiniteTransition(label = "historyPulse").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(PULSE_MS / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "historyGlow",
                )
            }
            IconOnlyButton(ShowdistIcons.History, "History, $savedCount saved", glow, onHistory)
        }
        IconOnlyButton(ShowdistIcons.SettingsOutlined, "Settings", null, onSettings)
    }
}

/**
 * A bare sandstone icon in a round touch target (a ripple shows when pressed). With [glow],
 * a soft-white copy of the icon and a pre-rendered halo of the icon's own shape fade in over
 * it as the value rises; only those layers' alpha changes, so the pulse redraws nothing.
 */
@Composable
private fun IconOnlyButton(icon: ImageVector, spoken: String, glow: State<Float>?, onClick: () -> Unit) {
    val painter = rememberVectorPainter(icon)
    Box(
        Modifier
            .size(TARGET)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glow != null) {
            // The halo: the icon drawn white, blurred once per size into a bitmap.
            Spacer(
                Modifier
                    .size(TARGET)
                    .graphicsLayer {
                        alpha = 0.7f * glow.value
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .drawWithCache {
                        val iconPx = ICON.toPx()
                        val image = ImageBitmap(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                        CanvasDrawScope().draw(this, layoutDirection, Canvas(image), size) {
                            translate((size.width - iconPx) / 2f, (size.height - iconPx) / 2f) {
                                with(painter) { draw(Size(iconPx, iconPx), colorFilter = ColorFilter.tint(SoftWhite)) }
                            }
                        }
                        val offset = IntArray(2)
                        val halo = image.asAndroidBitmap().extractAlpha(
                            Paint().apply { maskFilter = BlurMaskFilter(GLOW.toPx(), BlurMaskFilter.Blur.NORMAL) },
                            offset,
                        )
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SoftWhite.toArgb() }
                        onDrawBehind {
                            drawIntoCanvas { it.nativeCanvas.drawBitmap(halo, offset[0].toFloat(), offset[1].toFloat(), paint) }
                        }
                    },
            )
        }
        Icon(icon, contentDescription = null, tint = Palette.Sandstone, modifier = Modifier.size(ICON))
        if (glow != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = SoftWhite,
                modifier = Modifier.size(ICON).graphicsLayer {
                    alpha = glow.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        }
    }
}
