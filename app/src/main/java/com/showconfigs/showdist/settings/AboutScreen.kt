package com.showconfigs.showdist.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.Type

private const val DEVELOPER_NAME = "Carlos Cruz"
private const val DEVELOPER_EMAIL = "sh.run.configs@gmail.com"

/** A tool's usage guide in About: its name, then one line per task. */
private class Guide(val title: String, val entries: List<Pair<String, String>>)

private val Guides = listOf(
    Guide(
        "AR MEASURE",
        listOf(
            "Start" to "Move the phone slowly over the surface until the reticle's dots turn solid.",
            "Points" to "Tap the round button to drop a point at the reticle. Each span is labelled; the total shows at the top.",
            "Edit" to "Undo removes the last point; × clears them all. M / FT switches units.",
            "Save" to "With two or more points, hold anywhere on screen until the ring fills.",
        ),
    ),
    Guide(
        "HISTORY",
        listOf(
            "Open" to "The clock icon on the menu or in Measure, once something is saved.",
            "Share" to "Share or copy any measurement as text. Select several to export them as one PDF.",
            "View in AR" to "Tap where the first point goes, turn to line it up, and tap again.",
        ),
    ),
    Guide(
        "TUBE LEVEL",
        listOf(
            "Use" to "Hold an edge of the phone to a wall, post or beam, upright or sideways.",
            "Level" to "Within 0.3°, the bubble turns green and the reading sandstone, with a tick you can feel.",
            "Zero" to "On an uneven base, the target icon sets level here; reset returns to true level.",
        ),
    ),
    Guide(
        "BULLSEYE LEVEL",
        listOf(
            "Use" to "Lay the phone flat on the surface and centre the bubble in the ring.",
            "Read" to "X is the tilt side to side, Y front to back.",
            "Zero" to "Works as on the tube level.",
        ),
    ),
)

/**
 * About (SPEC.md §1.6): what Showdist does, a usage guide per tool, credits, and the way
 * to the licenses.
 */
@Composable
fun AboutScreen(onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    // Read from the installed package, so it can't drift from the build's versionName.
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Scaffold(topBar = { ShowdistAppBar("ABOUT", onBack) }, containerColor = Palette.Black) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("SHOWDIST", style = Type.Title.copy(color = Palette.Sandstone, letterSpacing = 3.sp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "An AR tape measure and two levels: measure distances with the camera, " +
                        "and check walls, beams and flat surfaces for level.",
                    style = Type.Body,
                )
            }
            for (guide in Guides) {
                SettingsDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                SectionTitle(guide.title)
                Column(Modifier.padding(horizontal = 16.dp)) {
                    for ((label, text) in guide.entries) GuideRow(label, text)
                }
            }
            SettingsDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SectionTitle("CREDITS")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("Version $version", style = Type.Body)
                Text(DEVELOPER_NAME, style = Type.Body)
                Text(DEVELOPER_EMAIL, style = Type.Secondary)
                Spacer(Modifier.height(8.dp))
                Text("Created with AI assistance", style = Type.Secondary.copy(fontStyle = FontStyle.Italic))
            }
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                "Licenses",
                subtitle = "ARCore, SceneView, AndroidX and JetBrains Mono",
                onClick = onLicenses,
            ) { Chevron() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideRow(label: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            modifier = Modifier.width(104.dp),
            style = Type.Secondary.copy(color = Palette.Text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start),
        )
        Text(text, modifier = Modifier.weight(1f), style = Type.Secondary)
    }
}
