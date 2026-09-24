package com.showconfigs.showdist.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.CaptionText
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistAppBar

private const val DEVELOPER_EMAIL = "sh.run.configs@gmail.com"

/** About (SPEC.md §1.6, §7.1), with the wording updated for the native build. */
@Composable
fun AboutScreen(onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    // Read from the installed package, so it can't drift from the build's versionName.
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val muted = BaseText.copy(color = Palette.WarmGrey, fontSize = 14.sp, textAlign = TextAlign.Center)

    Scaffold(topBar = { ShowdistAppBar("SHOWDIST", onBack) }, containerColor = Palette.Ink) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text("Version $version", Modifier.fillMaxWidth(), style = muted)
            Spacer(Modifier.height(20.dp))
            Text(
                "An augmented-reality tape measure for Android, built with Kotlin, Jetpack Compose and ARCore.",
                Modifier.fillMaxWidth(),
                style = muted.copy(lineHeight = 1.5.em),
            )
            Spacer(Modifier.height(28.dp))
            SettingsDivider()
            Spacer(Modifier.height(20.dp))
            Text("DEVELOPER", Modifier.fillMaxWidth(), style = CaptionText.copy(textAlign = TextAlign.Start))
            Spacer(Modifier.height(12.dp))
            SelectionContainer {
                Text(DEVELOPER_EMAIL, style = BaseText.copy(color = Palette.Peach, fontSize = 15.sp))
            }
            Spacer(Modifier.height(28.dp))
            SettingsDivider()
            // No side padding: the row lines up with the rest of the column.
            SettingsRow(
                "View licenses",
                subtitle = "ARCore, SceneView, AndroidX and JetBrains Mono (OFL-1.1)",
                horizontalPadding = 0.dp,
                onClick = onLicenses,
            ) { Chevron() }
            Spacer(Modifier.height(24.dp))
            Text(
                "Created with AI assistance",
                Modifier.fillMaxWidth(),
                style = BaseText.copy(
                    color = Palette.WarmGrey,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
