package com.showconfigs.showdist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared app bar (SPEC.md §1.2): darkTyrianBlue, no elevation or tint, white back arrow,
 * centred title in the Caption style in white.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowdistAppBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = CaptionText.copy(color = Palette.White)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(ShowdistIcons.ArrowBack, contentDescription = "Back", tint = Palette.White)
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Palette.DarkTyrianBlue,
            scrolledContainerColor = Palette.DarkTyrianBlue,
            navigationIconContentColor = Palette.White,
            titleContentColor = Palette.White,
            actionIconContentColor = Palette.White,
        ),
    )
}

/** An app-bar action icon with a tooltip on long press. White, or white at 0.3 when disabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarAction(icon: ImageVector, tooltip: String, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Palette.White,
                disabledContentColor = Palette.White.copy(alpha = 0.3f),
            ),
        ) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

/** Stand-in for a screen a later stage builds. */
@Composable
fun PlaceholderScreen(title: String, note: String, onBack: () -> Unit) {
    Scaffold(
        topBar = { ShowdistAppBar(title, onBack) },
        containerColor = Palette.DarkTyrianBlue,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(note, style = BaseText.copy(color = Palette.WarmGray, fontSize = 14.sp), modifier = Modifier.padding(40.dp))
        }
    }
}
