package com.showconfigs.showdist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.showconfigs.showdist.measure.MeasureScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stage 2: open straight into Measure. The main menu arrives in Stage 5.
        setContent { MeasureScreen() }
    }
}
