package com.showconfigs.showdist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Placeholder() }
    }
}

/** Stage 1 placeholder; the real landing screen arrives in Stage 5. */
@androidx.compose.runtime.Composable
private fun Placeholder() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF12354E)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "SHOWDIST",
            style = TextStyle(color = Color(0xFF9A72AA), fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
    }
}
