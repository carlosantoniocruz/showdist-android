package com.showconfigs.showdist.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared app bar (SPEC.md §1.2): darkTyrianBlue, no elevation or tint, white back arrow,
 * centred title in the Caption style in white.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowdistAppBar(title: String, onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = CaptionText.copy(color = Palette.White)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(ShowdistIcons.ArrowBack, contentDescription = "Back", tint = Palette.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Palette.DarkTyrianBlue,
            scrolledContainerColor = Palette.DarkTyrianBlue,
            navigationIconContentColor = Palette.White,
            titleContentColor = Palette.White,
        ),
    )
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
