package com.showconfigs.showdist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.showconfigs.showdist.settings.HandSetting

/** The centre control's slot, and so the row's height above its margins. */
val BottomRowCentre = 68.dp

/** A small control's touch target; its slot in the row. */
val BottomRowSlot = 48.dp

/** A bare icon's drawn width, which the row's spacing is worked out from. */
private val IconWidth = 24.dp

/** The width the row is designed for; narrower screens scale it down. */
private val FullWidth = 392.dp

/** On tablets and unfolded phones the controls stay grouped round the centre. */
private val MaxWidth = 480.dp

/** The row never shrinks below this share of its full size (a 320 dp wide screen). */
private const val MIN_SCALE = 0.8f

/** The least clearance under the controls, where the system reports no gesture area. */
private val MinBottom = 16.dp

/**
 * One control in a [BottomRow]: its [content] (usually a [BarSlot]) and how wide it is
 * drawn ([visual]), which is narrower than its touch target for a bare icon.
 */
class BarItem(val visual: Dp = IconWidth, val content: @Composable () -> Unit)

/**
 * The bottom row shared by Measure and the Leveler (SPEC.md §1.1a): the main control dead
 * centre, the navigation controls on one side ([nav]) and the tools on the other ([tools]),
 * both written from the centre outward. Right-handed, navigation is on the right;
 * [HandSetting] left-handed mirrors the row. Black, with a hairline above, so the camera
 * area ends where the row begins.
 *
 * The controls are evenly spaced by what is drawn, not by their touch targets: the gap
 * between one control's visible edge and the next is the same everywhere, on both sides,
 * so a wide control (the switcher pill) stands as far from the centre button as a bare icon
 * does. The gap is the one that spreads three icons across a side.
 *
 * The row itself never moves or turns. In Measure, held sideways, each control turns in its
 * slot ([BarSlot]); the Tube level turns its whole screen and passes [systemInsets] false,
 * being inside the system's insets already.
 *
 * It fits any screen: the controls sit above the system's gesture strip (at least 16 dp
 * clear of the bottom edge), keep to a 480 dp column on wide screens, and on screens
 * narrower than 392 dp the whole row scales down evenly, to 0.8 at most.
 */
@Composable
fun BottomRow(
    modifier: Modifier = Modifier,
    systemInsets: Boolean = true,
    nav: List<BarItem>,
    centre: @Composable () -> Unit,
    tools: List<BarItem>,
) {
    val gesture = WindowInsets.mandatorySystemGestures.union(WindowInsets.safeDrawing).asPaddingValues().calculateBottomPadding()
    val navOnRight = !HandSetting.leftHanded
    val upright = systemInsets
    Column(modifier.fillMaxWidth().background(Palette.Black)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Line))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .then(if (upright) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)) else Modifier)
                .padding(top = 8.dp, bottom = if (upright) maxOf(gesture + 4.dp, MinBottom) else MinBottom),
            contentAlignment = Alignment.Center,
        ) {
            val scale = (maxWidth / FullWidth).coerceIn(MIN_SCALE, 1f)
            val density = LocalDensity.current
            // Scaling is a smaller density for the row alone, so sizes, icons and text shrink together.
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                Layout(
                    content = {
                        BarSlot(BottomRowCentre) { centre() }
                        for (item in nav) Box { item.content() }
                        for (item in tools) Box { item.content() }
                    },
                    modifier = Modifier.widthIn(max = MaxWidth).fillMaxWidth(),
                ) { measurables, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val placeables = measurables.map { it.measure(loose) }
                    val width = constraints.maxWidth
                    val height = placeables.maxOf { it.height }
                    val centreWidth = placeables[0].width
                    val side = (width - centreWidth) / 2
                    val gap = ((side - 3 * IconWidth.roundToPx()) / 4).coerceAtLeast(0)
                    layout(width, height) {
                        placeables[0].place((width - centreWidth) / 2, (height - placeables[0].height) / 2)
                        fun placeSide(items: List<BarItem>, first: Int, toRight: Boolean) {
                            val sign = if (toRight) 1 else -1
                            var edge = width / 2 + sign * centreWidth / 2
                            items.forEachIndexed { i, item ->
                                val visual = item.visual.roundToPx()
                                val middle = edge + sign * (gap + visual / 2)
                                val p = placeables[first + i]
                                p.place(middle - p.width / 2, (height - p.height) / 2)
                                edge += sign * (gap + visual)
                            }
                        }
                        placeSide(nav, 1, navOnRight)
                        placeSide(tools, 1 + nav.size, !navOnRight)
                    }
                }
            }
        }
    }
}

/**
 * A fixed square slot in a [BottomRow], turned about its centre by [rotation] degrees so its
 * control reads upright however the phone is held (SPEC.md §2.9); [rotation] is read in the
 * layer phase only. Its content stays left-to-right when the row is mirrored.
 */
@Composable
fun BarSlot(size: Dp, rotation: () -> Float = { 0f }, content: @Composable () -> Unit) {
    Box(
        Modifier.size(size).graphicsLayer { rotationZ = rotation() }.wrapContentSize(unbounded = true),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
    }
}

/** A bare icon in a round hit area; near-white at 0.9 enabled, 0.3 disabled. */
@Composable
fun BarIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    tint: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize).alpha(if (enabled) 0.9f else 0.3f))
    }
}

/**
 * The Measure/Level switcher (SPEC.md §1.1a): a small two-part pill, the ruler and the
 * level, with the current tool's half filled accent. One tap anywhere on it goes to the
 * other tool. A 56×48 target around a 56×32 pill.
 */
@Composable
fun ModeSwitcher(levelActive: Boolean, onSwitch: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val haptics = rememberHaptics()
    Box(
        Modifier
            .size(56.dp, 48.dp)
            .clip(shape)
            .clickable(role = Role.Button) {
                haptics.tick()
                onSwitch()
            }
            .semantics { contentDescription = if (levelActive) "Switch to Measure" else "Switch to Leveler" },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.size(56.dp, 32.dp).border(BorderStroke(1.dp, Palette.Line), shape).clip(shape)) {
            SwitcherHalf(ShowdistIcons.Straighten, active = !levelActive)
            SwitcherHalf(ShowdistIcons.Level, active = levelActive)
        }
    }
}

@Composable
private fun SwitcherHalf(icon: ImageVector, active: Boolean) {
    Box(
        Modifier.size(28.dp, 32.dp).background(if (active) Palette.Accent else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) Palette.Black else Palette.TextMuted, modifier = Modifier.size(18.dp))
    }
}
