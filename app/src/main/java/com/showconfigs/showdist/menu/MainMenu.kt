package com.showconfigs.showdist.menu

import androidx.compose.ui.unit.em
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type
import com.showconfigs.showdist.ui.rememberHaptics
import com.showconfigs.showdist.ui.rememberReduceMotion

/** The tools, one moon each, with their quick guides (SPEC.md §1.4). */
enum class Tool(val title: String, val spoken: String, val about: String) {
    Measure(
        "AR MEASURE",
        "AR Measure",
        "Measure distances with the camera. Move slowly until the reticle turns solid, tap to drop points, hold to save.",
    ),
    Tube(
        "TUBE LEVEL",
        "Tube Level",
        "Level or plumb walls and beams. Hold an edge of the phone to the surface until the bubble turns green.",
    ),
    Bullseye(
        "BULLSEYE LEVEL",
        "Bullseye Level",
        "Level a flat surface both ways. Lay the phone on it and centre the bubble; X is side to side, Y front to back.",
    ),
}

/**
 * The main menu (SPEC.md §1.4): History (once something is saved) and Settings on the
 * right of a slim top row, the orbital selector, and the focused tool's
 * quick guide sliding up beneath it. It never touches the camera or the AR session.
 * [focused] is held by the caller, so the tool last used is still in front on return.
 */
@Composable
fun MainMenu(
    savedCount: Int,
    focused: Tool,
    onFocused: (Tool) -> Unit,
    onTool: (Tool) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val orbit = remember { OrbitState(focused.ordinal, Tool.entries.size, scope, reduceMotion) }
    val deck = rememberFactDeck()
    var factsShowing by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val egg = remember { CollapseEgg(scope, haptics, reduceMotion, onCollide = { factsShowing = true }) }
    // A light tick each time the orbit settles on a different moon (SPEC.md §1.4).
    var lastSettled by remember { mutableIntStateOf(orbit.focused) }
    LaunchedEffect(orbit.settled) {
        if (!orbit.settled) return@LaunchedEffect
        if (orbit.focused != lastSettled) {
            lastSettled = orbit.focused
            haptics.tick()
        }
        onFocused(Tool.entries[orbit.focused])
    }

    Box(Modifier.fillMaxSize().background(Palette.Black)) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Thumb reach (SPEC.md §1.4): the orbit takes only the height its span needs
        // (shrinking on short screens), and the space left over is shared out so the whole
        // group sits low: half above the orbit, and equal gaps above and below the guide,
        // whose text is centred in its fixed slot, so it's evenly spaced between the moons
        // and the buttons whichever tool is in front.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val orbitHeight = minOf(sunRadiusFor(maxWidth) * ORBIT_SPAN + ORBIT_MARGIN, maxHeight - GuideEstimate)
                .coerceAtLeast(MIN_ORBIT_HEIGHT)
            val areaWidth = maxWidth
            Column(Modifier.fillMaxSize()) {
                // The backdrop (SPEC.md §1.4a) lives only in this band above the sun; still
                // while covered or with reduced motion.
                MenuSky(animate = !reduceMotion && !factsShowing, modifier = Modifier.weight(TOP_SHARE).fillMaxWidth())
                Orbit(orbit, egg, Tool.entries, onOpen = onTool, modifier = Modifier.fillMaxWidth().height(orbitHeight))
                Spacer(Modifier.weight(GAP_SHARE))
                // Swiping anywhere on the guide turns the orbit too, at the same rate.
                val stepPx = with(LocalDensity.current) { orbitRadiusX(areaWidth, sunRadiusFor(areaWidth, orbitHeight)).toPx() }
                QuickGuide(
                    tool = if (orbit.settled) Tool.entries[orbit.focused] else null,
                    reduceMotion = reduceMotion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .orbitSwipe(orbit, stepPx),
                )
                // The closing instruction, on its own and evenly spaced: the same gap above
                // it as below it, and as between the moons and the guide. The fixed part
                // matches the clear margin under the orbit and above the icons, so the gaps
                // look equal.
                Spacer(Modifier.height(CAPTION_BALANCE))
                Spacer(Modifier.weight(GAP_SHARE))
                Text(
                    "TAP THE MOON AGAIN TO OPEN",
                    Modifier.fillMaxWidth().orbitSwipe(orbit, stepPx),
                    style = Type.Caption.copy(
                        color = Palette.Hint,
                        fontSize = Type.Caption.fontSize * 0.86f,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(Modifier.weight(GAP_SHARE))
            }
        }
        // One-handed reach (SPEC.md §1.4): History and Settings in a row near the bottom,
        // clear of the gesture area by 20 dp.
        MenuButtons(
            showHistory = savedCount > 0,
            savedCount = savedCount,
            pulsing = !factsShowing,
            reduceMotion = reduceMotion,
            onHistory = onHistory,
            onSettings = onSettings,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.mandatorySystemGestures.only(WindowInsetsSides.Bottom))
                .padding(top = 4.dp, bottom = 4.dp),
        )
    }
    // The collapse's prize (SPEC.md §1.4), over everything.
    if (factsShowing) FunFactsScene(deck, reduceMotion, flash = !reduceMotion, onClose = { factsShowing = false })
    }
}

/** Roughly the tallest guide, used only to keep room for it when shrinking the orbit on short screens. */
private val GuideEstimate = 180.dp

/** The orbit's smallest height on very short screens. */
private val MIN_ORBIT_HEIGHT = 160.dp

/** How the menu's spare height is shared: above the orbit, and each gap around the guide. */
private const val TOP_SHARE = 0.7f
private const val GAP_SHARE = 0.1f

/** Evens out the visible gaps around the closing instruction (see its Spacer). */
private val CAPTION_BALANCE = 12.dp

/** The guide's name and paragraph, scaled down evenly from the Title and Body styles. */
private const val GUIDE_SCALE = 0.86f

/** The guide block's width, padding included: the same on every phone and tablet. */
private val GuideTextWidth = 388.dp

/**
 * The focused tool's quick guide (SPEC.md §1.4): its name, what it's for, and how to use
 * it. It slides up once the orbit settles and fades while it turns; TalkBack reads it out
 * when it changes.
 */
@Composable
private fun QuickGuide(tool: Tool?, reduceMotion: Boolean, modifier: Modifier) {
    // Sized to the tallest tool's guide, laid out invisibly, so the slot fits the text
    // exactly with no reserved space, and nothing moves as tools change.
    Box(modifier) {
        for (t in Tool.entries) GuideText(t, Modifier.alpha(0f).clearAndSetSemantics {})
        AnimatedContent(
            targetState = tool,
            modifier = Modifier.matchParentSize(),
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
            GuideText(shown, Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite })
        }
    }
}

/**
 * One tool's guide (SPEC.md §1.4, §6.5): its name and a short paragraph, both centred in a
 * block of even width on the sun's axis, so the margins match on both sides. Sizes are the
 * Title and Body styles scaled evenly by [GUIDE_SCALE].
 */
@Composable
private fun GuideText(tool: Tool, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = GuideTextWidth)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            tool.title,
            style = Type.Title.copy(color = Palette.Sandstone, letterSpacing = 2.sp, fontSize = Type.Title.fontSize * GUIDE_SCALE),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tool.about,
            style = Type.Body.copy(fontSize = Type.Body.fontSize * GUIDE_SCALE, lineHeight = 1.45.em, textAlign = TextAlign.Center),
        )
    }
}
