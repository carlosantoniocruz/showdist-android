package com.showconfigs.showdist.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.BaseText
import com.showconfigs.showdist.ui.CaptionText
import com.showconfigs.showdist.ui.NightSky
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.ToolCard
import com.showconfigs.showdist.ui.ToolTile

private const val DEVELOPER_NAME = "Carlos Cruz"
private const val DEVELOPER_EMAIL = "sh.run.configs@gmail.com"

/**
 * The main menu (SPEC.md §1.4): a header block in the upper left (wordmark, what the app
 * does, tips, about), then the tools centred in the space below. It never touches the
 * camera or the AR session. [savedCount] drives History and Export, shown only when
 * something has been saved.
 */
@Composable
fun MainMenu(
    savedCount: Int,
    onMeasure: () -> Unit,
    onTube: () -> Unit,
    onBullseye: () -> Unit,
    onHistory: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        // Where the menu's text is, in window coordinates, so no star sits behind it.
        val text = remember { mutableStateMapOf<String, Rect>() }
        fun Modifier.text(key: String) = onGloballyPositioned { text[key] = it.boundsInRoot() }
        NightSky(text.values, Modifier.fillMaxSize())

        // Scrolls only if the text outgrows the screen (large font sizes); otherwise the tools
        // sit centred in the space under the header.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = HeaderThenCentred,
            ) {
                Header(onSettings, Modifier.text("header"))
                Column(Modifier.fillMaxWidth().padding(top = 16.dp).text("tools")) {
                    ToolCard(
                        label = "MEASURE",
                        description = "Distances with the camera.",
                        accent = Palette.Green,
                        onClick = onMeasure,
                    ) { DimensionMark() }
                    if (savedCount > 0) {
                        SubLink(ShowdistIcons.History, "HISTORY", "$savedCount saved", onHistory)
                        SubLink(ShowdistIcons.PictureAsPdf, "EXPORT PDF", if (savedCount == 1) "1 measurement" else "all $savedCount", onExport)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToolTile(
                            label = "TUBE",
                            description = "Walls, posts, beams.",
                            accent = Palette.Violet,
                            onClick = onTube,
                            modifier = Modifier.weight(1f),
                        ) { TubePicture() }
                        ToolTile(
                            label = "BULLSEYE",
                            description = "Flat surfaces.",
                            accent = Palette.Violet,
                            onClick = onBullseye,
                            modifier = Modifier.weight(1f),
                        ) { BullseyePicture() }
                    }
                }
            }
        }
    }
}

/** The header at the top, the tools centred in whatever height is left below it. */
private object HeaderThenCentred : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        outPositions[0] = 0
        val below = totalSize - sizes[0]
        outPositions[1] = sizes[0] + maxOf(0, (below - sizes[1]) / 2)
    }
}

/**
 * The header block (SPEC.md §1.4), read as one section: the wordmark and its dimension
 * line, what the app is, how to use it, then about.
 */
@Composable
private fun Header(onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Read from the installed package, so it can't drift from the build's versionName.
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val body = BaseText.copy(color = Palette.Text, fontSize = 13.sp, lineHeight = 1.5.em)
    val muted = BaseText.copy(color = Palette.TextMuted, fontSize = 12.sp, lineHeight = 1.5.em)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "SHOWDIST",
                style = BaseText.copy(color = Palette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp),
            )
            Spacer(Modifier.width(14.dp))
            DimensionMark(Modifier.width(56.dp).height(16.dp))
            Spacer(Modifier.weight(1f))
            SettingsButton(onSettings)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "An AR tape measure and a level.",
            style = body,
        )
        Spacer(Modifier.height(14.dp))
        HowToUse()
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append("v$version · $DEVELOPER_NAME · $DEVELOPER_EMAIL\n")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Created with AI assistance") }
            },
            style = muted.copy(fontSize = 11.sp),
        )
    }
}

/** How to use the app (SPEC.md §1.4): one entry per task, labelled in the tool's accent. */
private val Usage = listOf(
    Triple(
        "MEASURE", Palette.Green,
        "Move slowly until the reticle's dots turn solid, then tap the green button to drop points. " +
            "Each span is labelled; the total shows at the top. Undo, × clears, M / FT switches units.",
    ),
    Triple("SAVE", Palette.Green, "With two or more points, hold anywhere until the ring fills."),
    Triple(
        "HISTORY", Palette.Green,
        "Share, copy, or View in AR: tap where the first point goes, turn to line up, tap. Select several for one PDF.",
    ),
    Triple("TUBE", Palette.Violet, "Hold an edge to a wall or beam, upright or sideways. Green within 0.3° of level."),
    Triple("BULLSEYE", Palette.Violet, "Lay flat. X is side to side, Y front to back."),
    Triple("ZERO", Palette.Violet, "On an uneven base, the target icon sets level here; reset returns to true level."),
)

/** How to use, in the header (SPEC.md §1.4). */
@Composable
private fun HowToUse() {
    Column(Modifier.fillMaxWidth()) {
        Text("HOW TO USE", style = CaptionText.copy(fontSize = 11.sp, textAlign = TextAlign.Start))
        Spacer(Modifier.height(6.dp))
        for ((label, color, text) in Usage) UsageRow(label, color, text)
    }
}

@Composable
private fun UsageRow(label: String, color: Color, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(76.dp),
            style = BaseText.copy(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 1.6.em),
        )
        Text(text, modifier = Modifier.weight(1f), style = BaseText.copy(color = Palette.TextMuted, fontSize = 12.sp, lineHeight = 1.45.em))
    }
}

/**
 * History and Export under Measure (SPEC.md §1.4), joined to it by a thin line. History
 * opens without starting the camera; Export shares every saved measurement as a PDF.
 */
@Composable
private fun SubLink(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(44.dp).background(Palette.Green.copy(alpha = 0.6f)))
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Palette.Green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = BaseText.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp))
            Spacer(Modifier.width(10.dp))
            Text(detail, modifier = Modifier.weight(1f), style = BaseText.copy(color = Palette.TextMuted, fontSize = 12.sp))
            Icon(ShowdistIcons.ChevronRight, contentDescription = null, tint = Palette.TextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text("Settings") } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(ShowdistIcons.SettingsOutlined, contentDescription = "Settings", tint = Palette.TextMuted)
        }
    }
}
