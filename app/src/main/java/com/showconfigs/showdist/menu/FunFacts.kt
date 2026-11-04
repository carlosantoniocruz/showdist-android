package com.showconfigs.showdist.menu

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

/** A fun fact and its optional short citation (SPEC.md §1.4). */
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

/** Scroll speed, as the old ticker's. */
private val SPEED = 40.dp
private val EDGE_FADE = 112.dp
private val FACT_GAP = 44.dp
private const val FADE_IN_MS = 600
private const val FADE_OUT_MS = 350

/** The collision flash (SPEC.md §1.4): brief, so it never strobes. */
private const val FLASH_IN_MS = 120
private const val FLASH_OUT_MS = 320

/**
 * The collapse's prize (SPEC.md §1.4): the screen fades to black and the fun facts roll up
 * it, shuffled, credits style, fading out at the top and bottom edges. Each run deals a new
 * shuffled round, and the next round follows only once every fact has shown. Tapping or
 * going back fades it out and calls [onClose]. With [reduceMotion], one fact shows at a
 * time, still, and a tap moves to the next.
 *
 * With [flash] (the collapse's collision) it opens with a brief white flash over the whole
 * screen, which then fades into the black; otherwise the black simply fades in.
 *
 * Only the column's layer translation changes per frame, so scrolling re-records nothing;
 * the fades are fixed gradients drawn over it.
 */
@Composable
fun FunFactsScene(deck: FactDeck, reduceMotion: Boolean, flash: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scene = remember { Animatable(1f) }
    val alpha = remember { Animatable(0f) }
    val white = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            scene.animateTo(0f, tween(FADE_OUT_MS))
            onClose()
        }
    }
    LaunchedEffect(Unit) {
        if (flash) {
            white.animateTo(1f, tween(FLASH_IN_MS))
            alpha.snapTo(1f)
            white.animateTo(0f, tween(FLASH_OUT_MS))
        } else {
            alpha.animateTo(1f, tween(FADE_IN_MS))
        }
    }
    BackHandler { close() }

    var round by remember { mutableStateOf(deck.shuffle()) }
    var index by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = scene.value }) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Palette.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (reduceMotion && index < round.size - 1) {
                    index++
                } else if (reduceMotion) {
                    round = deck.shuffle()
                    index = 0
                } else {
                    close()
                }
            }
            .semantics { contentDescription = "Fun facts. Tap to close." },
        contentAlignment = Alignment.TopCenter,
    ) {
        val viewport = constraints.maxHeight.toFloat()
        val column = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 32.dp)
        if (reduceMotion) {
            Box(Modifier.fillMaxSize().padding(vertical = EDGE_FADE), contentAlignment = Alignment.Center) {
                FactItem(round[index], column)
            }
            return@BoxWithConstraints
        }

        val speedPx = with(LocalDensity.current) { SPEED.toPx() }
        var contentHeight by remember { mutableFloatStateOf(0f) }
        // The column's top, in px from the top of the screen; starts just below the bottom.
        var offset by remember { mutableFloatStateOf(viewport) }
        LaunchedEffect(round) {
            offset = viewport
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                offset -= speedPx * (now - last) / 1e9f
                last = now
                // The round has scrolled off the top: deal the next one from the bottom.
                if (contentHeight > 0f && offset < -contentHeight) {
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
                if (i > 0) {
                    Spacer(Modifier.height(FACT_GAP / 2))
                    Text("✦", style = Type.Secondary.copy(color = Palette.Divider))
                    Spacer(Modifier.height(FACT_GAP / 2))
                }
                FactItem(fact, Modifier.fillMaxWidth())
            }
        }
        EdgeFades(Modifier.fillMaxSize())
    }
    Spacer(Modifier.fillMaxSize().graphicsLayer { this.alpha = white.value }.background(Palette.White))
    }
}

/** A fact in the ticker's style (Secondary, sandstone), its source small and grey beneath. */
@Composable
private fun FactItem(fact: FunFact, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fact.text, style = Type.Secondary.copy(color = Palette.Sandstone, textAlign = TextAlign.Center))
        if (fact.source != null) {
            Spacer(Modifier.height(6.dp))
            Text(fact.source, style = Type.Secondary.copy(color = Palette.TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }
}

/** Black-to-clear gradients at the top and bottom edges, so text fades to black there. */
@Composable
private fun EdgeFades(modifier: Modifier) {
    Spacer(
        modifier.drawWithCache {
            val edge = EDGE_FADE.toPx()
            val top = Brush.verticalGradient(listOf(Palette.Black, Color.Transparent), startY = 0f, endY = edge)
            val bottom = Brush.verticalGradient(listOf(Color.Transparent, Palette.Black), startY = size.height - edge, endY = size.height)
            onDrawBehind {
                drawRect(top, size = size.copy(height = edge))
                drawRect(bottom, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - edge), size = size.copy(height = edge))
            }
        },
    )
}

/** The deck, made once per menu. */
@Composable
fun rememberFactDeck(): FactDeck {
    val context = LocalContext.current
    return remember { FactDeck(loadFunFacts(context)) }
}
