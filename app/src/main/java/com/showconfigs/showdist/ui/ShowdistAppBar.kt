package com.showconfigs.showdist.ui

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
 * Shared app bar (SPEC.md §1.2): black, no elevation or tint, near-white back arrow,
 * centred title in the Caption style in near-white.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowdistAppBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = CaptionText.copy(color = Palette.Text)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(ShowdistIcons.ArrowBack, contentDescription = "Back", tint = Palette.Text)
            }
        },
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
