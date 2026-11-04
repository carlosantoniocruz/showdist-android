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
 * style in near-white, and any actions on the right. Back lives at the bottom ([BackBar]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowdistAppBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = Type.Caption.copy(color = Palette.Text)) },
        actions = actions,
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

/** An app-bar action icon with a tooltip on long press, in [tint] (near-white by default), at 0.3 alpha when disabled. */
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
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
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
 * centred, sitting the same distance above the gesture area as the menu's History and
 * Settings icons. For a Scaffold's bottomBar.
 */
@Composable
fun BackBar(onBack: () -> Unit) {
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
    }
}

/** The back control itself: a bare sandstone arrow in a 56 dp round touch target. */
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
        Icon(ShowdistIcons.ArrowBack, contentDescription = null, tint = Palette.Sandstone, modifier = Modifier.size(30.dp))
    }
}
