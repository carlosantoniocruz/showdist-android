package com.showconfigs.showdist.settings

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showconfigs.showdist.R
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type

/** A fact and its optional short citation (SPEC.md §1.6). */
class FunFact(val text: String, val source: String?)

/**
 * Reads `res/raw/fun_facts.txt`: blocks separated by blank lines, the fact first, then an
 * optional "source: NAME" line; lines starting with # are comments.
 */
fun loadFunFacts(context: Context): List<FunFact> {
    val raw = context.resources.openRawResource(R.raw.fun_facts).bufferedReader().use { it.readText() }
    return raw.split(Regex("\\n\\s*\\n")).mapNotNull { block ->
        val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val text = lines.firstOrNull { !it.startsWith("source:") } ?: return@mapNotNull null
        val source = lines.firstOrNull { it.startsWith("source:") }?.removePrefix("source:")?.trim()
        FunFact(text, source)
    }
}

/**
 * The pool dealt in shuffled rounds: every fact once before any repeats. A new round never
 * starts with the fact that ended the last one.
 */
class FactDeck(private val pool: List<FunFact>) {
    private var round: List<FunFact> = emptyList()

    fun shuffle(): List<FunFact> {
        val last = round.lastOrNull()
        var next = pool.shuffled()
        if (next.size > 1 && next.first() === last) next = next.drop(1) + next.first()
        round = next
        return next
    }
}

@Composable
fun rememberFactDeck(): FactDeck {
    val context = LocalContext.current
    return remember { FactDeck(loadFunFacts(context)) }
}

/** Scroll speed: a little slower than the earlier Easter egg's 40 dp a second. */
private val SPEED = 32.dp
private val EDGE_FADE = 72.dp
private val FACT_GAP = 40.dp

/** The facts scroll in this share of the page's height, from the top. */
private const val UPPER_SHARE = 0.68f

/**
 * The About page's prize (SPEC.md §1.6): measurement history and trivia rolling up the
 * upper part of the page, shuffled, fading out at its top and bottom, over [FactsSky].
 * A tap pauses or resumes it, and it holds still for as long as a finger rests on it. With
 * [reduceMotion] one fact shows at a time, still, and a tap moves to the next.
 *
 * Only the column's layer translation changes per frame, so scrolling re-records nothing.
 * It runs only while it is composed: the page shows it only once the hold completes, and
 * drops it on Back or on leaving.
 */
@Composable
fun FactsScene(deck: FactDeck, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    var round by remember { mutableStateOf(deck.shuffle()) }
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    val moving = !paused && !held && !reduceMotion

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(reduceMotion) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    held = true
                    val up = waitForUpOrCancellation()
                    held = false
                    if (up != null) {
                        if (!reduceMotion) {
                            paused = !paused
                        } else if (index < round.size - 1) {
                            index++
                        } else {
                            round = deck.shuffle()
                            index = 0
                        }
                    }
                }
            }
            .semantics { contentDescription = "Facts about measuring. Tap to pause or resume. Back closes." },
    ) {
        FactsSky(animate = moving, modifier = Modifier.fillMaxSize())
        BoxWithConstraints(
            Modifier.fillMaxWidth().fillMaxHeight(UPPER_SHARE).clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val viewport = constraints.maxHeight.toFloat()
            val column = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 28.dp)
            if (reduceMotion) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FactItem(round[index], column) }
                return@BoxWithConstraints
            }
            val speedPx = with(LocalDensity.current) { SPEED.toPx() }
            var contentHeight by remember { mutableFloatStateOf(0f) }
            // The column's top, in px from the top of the area; starts just below its bottom.
            var offset by remember { mutableFloatStateOf(viewport) }
            LaunchedEffect(round, moving) {
                if (!moving) return@LaunchedEffect
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    offset -= speedPx * (now - last) / 1e9f
                    last = now
                    // The round has scrolled off the top: deal the next one from the bottom.
                    if (contentHeight > 0f && offset < -contentHeight) {
                        offset = viewport
                        round = deck.shuffle()
                        break
                    }
                }
            }
            Column(
                column
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .onSizeChanged { contentHeight = it.height.toFloat() }
                    .graphicsLayer { translationY = offset },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                for ((i, fact) in round.withIndex()) {
                    if (i > 0) Spacer(Modifier.height(FACT_GAP))
                    FactItem(fact, Modifier.fillMaxWidth())
                }
            }
            EdgeFades(Modifier.fillMaxSize())
        }
    }
}

/** A fact in white-grey, its source small and dimmer beneath. */
@Composable
private fun FactItem(fact: FunFact, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fact.text, style = Type.Secondary.copy(color = Palette.Text, textAlign = TextAlign.Center))
        if (fact.source != null) {
            Spacer(Modifier.height(4.dp))
            Text(fact.source, style = Type.Secondary.copy(color = Palette.Hint, fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }
}

/** Black-to-clear at the area's top and bottom edges, so text fades to black there. */
@Composable
private fun EdgeFades(modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val edge = EDGE_FADE.toPx()
            val top = Brush.verticalGradient(listOf(Palette.Black, Color.Transparent), startY = 0f, endY = edge)
            val bottom = Brush.verticalGradient(listOf(Color.Transparent, Palette.Black), startY = size.height - edge, endY = size.height)
            onDrawBehind {
                drawRect(top, size = size.copy(height = edge))
                drawRect(bottom, topLeft = Offset(0f, size.height - edge), size = size.copy(height = edge))
            }
        },
    )
}
