package com.showconfigs.showdist.menu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.AppBarAction
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.ShowdistIcons
import com.showconfigs.showdist.ui.Type
import com.showconfigs.showdist.ui.rememberReduceMotion

/** The tools, one moon each, with their quick guides (SPEC.md §1.4). */
enum class Tool(val title: String, val spoken: String, val purpose: String, val steps: List<String>) {
    Measure(
        "AR MEASURE",
        "AR Measure",
        "Distances with the camera.",
        listOf(
            "Move slowly until the reticle's dots turn solid.",
            "Tap the round button to drop each point.",
            "Hold anywhere to save; saved ones go to History.",
        ),
    ),
    Tube(
        "TUBE LEVEL",
        "Tube Level",
        "Level or plumb on walls, posts and beams.",
        listOf(
            "Hold an edge of the phone to the surface.",
            "Upright or sideways; the bubble turns green within 0.3°.",
            "On an uneven base, the target icon zeroes it.",
        ),
    ),
    Bullseye(
        "BULLSEYE LEVEL",
        "Bullseye Level",
        "Level on a flat surface, both ways at once.",
        listOf(
            "Lay the phone flat on the surface.",
            "Centre the bubble in the ring.",
            "X is side to side, Y front to back.",
        ),
    ),
}

/**
 * The main menu (SPEC.md §1.4): About on the left and History (once something is saved)
 * and Settings on the right of a slim top row, the orbital selector, and the focused tool's
 * quick guide sliding up beneath it. It never touches the camera or the AR session.
 */
@Composable
fun MainMenu(
    savedCount: Int,
    onTool: (Tool) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    var lastFocused by rememberSaveable { mutableIntStateOf(0) }
    val orbit = remember { OrbitState(lastFocused, Tool.entries.size, scope, reduceMotion) }
    LaunchedEffect(orbit.settled) { if (orbit.settled) lastFocused = orbit.focused }

    Column(Modifier.fillMaxSize().background(Palette.Black).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBarAction(ShowdistIcons.InfoOutlined, "About", tint = Palette.Sandstone, onClick = onAbout)
            Spacer(Modifier.weight(1f))
            if (savedCount > 0) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = Palette.Sandstone, contentColor = Palette.Black) { Text("$savedCount") }
                    },
                ) {
                    AppBarAction(ShowdistIcons.History, "History, $savedCount saved", tint = Palette.Sandstone, onClick = onHistory)
                }
                Spacer(Modifier.width(8.dp))
            }
            AppBarAction(ShowdistIcons.SettingsOutlined, "Settings", tint = Palette.Sandstone, onClick = onSettings)
        }
        Orbit(orbit, Tool.entries, onOpen = onTool, modifier = Modifier.weight(1f).fillMaxWidth())
        QuickGuide(
            tool = if (orbit.settled) Tool.entries[orbit.focused] else null,
            reduceMotion = reduceMotion,
            modifier = Modifier.fillMaxWidth().heightIn(min = GuideHeight),
        )
    }
}

/** Reserved for the guide even while it's hidden, so the orbit doesn't jump. */
private val GuideHeight = 236.dp

/**
 * The focused tool's quick guide (SPEC.md §1.4): its name, what it's for, and how to use
 * it. It slides up once the orbit settles and fades while it turns; TalkBack reads it out
 * when it changes.
 */
@Composable
private fun QuickGuide(tool: Tool?, reduceMotion: Boolean, modifier: Modifier) {
    AnimatedContent(
        targetState = tool,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (slideInVertically(spring(dampingRatio = 0.9f, stiffness = 300f)) { it / 3 } + fadeIn(tween(220))) togetherWith
                    fadeOut(tween(120))
            }
        },
        contentAlignment = Alignment.TopCenter,
        label = "guide",
    ) { shown ->
        if (shown == null) {
            Box(Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 24.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(shown.title, style = Type.Title.copy(color = Palette.Sandstone, letterSpacing = 2.sp))
            Spacer(Modifier.height(6.dp))
            Text(shown.purpose, style = Type.Body.copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) {
                for (step in shown.steps) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("›", Modifier.width(20.dp), style = Type.Secondary.copy(color = Palette.Sandstone, fontWeight = FontWeight.Bold))
                        Text(step, Modifier.weight(1f), style = Type.Secondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("TAP THE MOON AGAIN TO OPEN", style = Type.Caption.copy(fontSize = Type.Caption.fontSize * 0.86f))
        }
    }
}
