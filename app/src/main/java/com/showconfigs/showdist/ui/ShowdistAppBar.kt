package com.showconfigs.showdist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.showconfigs.showdist.settings.HandSetting
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared app bar (SPEC.md §1.2): black, no elevation or tint, centred title in the Caption
 * style in near-white, and nothing to tap: Back and every action live at the bottom
 * ([BackBar], [ActionBar]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowdistAppBar(title: String) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = Type.Caption.copy(color = Palette.Text)) },
        windowInsets = AppBarInsets,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Palette.Black,
            scrolledContainerColor = Palette.Black,
            navigationIconContentColor = Palette.Text,
            titleContentColor = Palette.Text,
            actionIconContentColor = Palette.Text,
        ),
    )
}

/**
 * App bars avoid the display cutout as well as the (hidden, immersive) system bars, so a
 * punch-hole camera never sits over a title or action.
 */
val AppBarInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** A bottom-bar action icon with a tooltip on long press, in [tint] (near-white by default), at 0.3 alpha when disabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarAction(
    icon: ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    tint: Color = Palette.Text,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tint,
                disabledContentColor = tint.copy(alpha = 0.3f),
            ),
        ) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

/**
 * Back, at the bottom of the screen within easy reach (SPEC.md §1.2): a [BackButton]
 * centred, clear of the gesture area, with the screen's [actions], if any, beside it on the
 * thumb side: the right, or the left when [HandSetting] is left-handed. For a Scaffold's
 * bottomBar.
 */
@Composable
fun BackBar(onBack: () -> Unit, actions: (@Composable RowScope.() -> Unit)? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Palette.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))
            .padding(top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BackButton(onBack)
        if (actions != null) {
            Row(
                Modifier
                    .align(if (HandSetting.leftHanded) AbsoluteAlignment.CenterLeft else AbsoluteAlignment.CenterRight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * A bottom bar of actions only, spread evenly (History's selection mode, SPEC.md §1.9);
 * mirrored when [HandSetting] is left-handed.
 */
@Composable
fun ActionBar(actions: @Composable RowScope.() -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides if (HandSetting.leftHanded) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))
                .padding(top = 4.dp, bottom = 4.dp)
                .heightIn(min = 56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** The back control itself: a bare accent arrow in a 56 dp round touch target. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(ShowdistIcons.ArrowBack, contentDescription = null, tint = Palette.Accent, modifier = Modifier.size(30.dp))
    }
}
