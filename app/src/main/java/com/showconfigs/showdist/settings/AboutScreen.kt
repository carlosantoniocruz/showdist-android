package com.showconfigs.showdist.settings

import com.showconfigs.showdist.ui.BackBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import com.showconfigs.showdist.ui.ShowdistAppBar
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.readingColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type

private const val DEVELOPER_NAME = "Carlos Cruz"
private const val X_HANDLE = "@shcarlosconfigs"
private const val X_URL = "https://x.com/shcarlosconfigs"
private const val PRIVACY_POLICY_URL = "https://carlosantoniocruz.github.io/showdist-privacy/"

/** A tool's usage guide in About: its name, then one line per task. */
private class Guide(val title: String, val entries: List<Pair<String, String>>)

private val Guides = listOf(
    Guide(
        "AR MEASURE",
        listOf(
            "Start" to "Move the phone slowly over the surface until the reticle's dots turn solid.",
            "Points" to "Tap the round button to drop a point at the reticle. Each span is labelled; the total shows at the top.",
            "Edit" to "Undo removes the last point; × clears them all. M / FT switches units.",
            "Save" to "With two or more points, hold anywhere on screen until the line round the edge fills.",
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
 * About (SPEC.md §1.6), its own screen, reached only from Settings: the app's name and what
 * it does, "Created with AI assistance", the developer (the only contact is X), how to use
 * each tool, the ARCore notice, and the ways to the privacy policy (Play requires an in-app
 * link) and the licenses. Back returns to Settings.
 */
@Composable
fun AboutScreen(onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    // Read from the installed package, so it can't drift from the build's versionName.
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Scaffold(topBar = { ShowdistAppBar("ABOUT") }, bottomBar = { BackBar(onBack) }, containerColor = Palette.Black) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.readingColumn().padding(vertical = 8.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("SHOWDIST", style = Type.Title.copy(color = Palette.Sandstone, letterSpacing = 3.sp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "An AR tape measure and two levels: measure distances with the camera, " +
                            "and check walls, beams and flat surfaces for level.",
                        style = Type.Body,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Created with AI assistance", style = Type.Secondary.copy(fontStyle = FontStyle.Italic))
                }

                SettingsDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                SectionTitle("DEVELOPER")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(DEVELOPER_NAME, style = Type.Body)
                    Text("Version $version", style = Type.Secondary)
                }
                XContactRow(onClick = { runCatching { uriHandler.openUri(X_URL) } })

                for (guide in Guides) {
                    SettingsDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    SectionTitle(guide.title)
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        for ((label, text) in guide.entries) GuideRow(label, text)
                    }
                }

                SettingsDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                // Required by the ARCore terms of service.
                Text(
                    "This application runs on Google Play Services for AR (ARCore), which is provided " +
                        "by Google LLC and governed by the Google Privacy Policy.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = Type.Secondary,
                )
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    "Privacy policy",
                    subtitle = "Opens in your browser",
                    // No browser to handle it: nothing to do.
                    onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                ) { Chevron() }
                SettingsRow(
                    "Licenses",
                    subtitle = "ARCore, SceneView, AndroidX and JetBrains Mono",
                    onClick = onLicenses,
                ) { Chevron() }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The developer's only contact (SPEC.md §1.6): the X logo in sandstone and the handle beside
 * it in JetBrains Mono, one tappable row (at least 48 dp tall, with a ripple when pressed)
 * that opens the profile in the X app or the browser.
 */
@Composable
private fun XContactRow(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "Open on X", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Developer on X, $X_HANDLE"
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ShowdistIcons.XLogo, contentDescription = null, tint = Palette.Sandstone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(X_HANDLE, style = Type.Label.copy(color = Palette.Sandstone))
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
