package com.showconfigs.showdist.menu

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.showconfigs.showdist.ui.Palette
import com.showconfigs.showdist.ui.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far ahead a flick carries the orbit before it snaps, in seconds of its speed. */
private const val FLING_LOOKAHEAD = 0.25f

/**
 * Where the moons are on their orbit, and the motion between moons (SPEC.md §1.4).
 * [position] counts moons: at a whole number n, moon n mod [count] sits at the front.
 * A drag turns the orbit directly; on release, or on a tap, a soft spring carries it to
 * the nearest (or tapped) moon. With [reduceMotion] it jumps there instead.
 */
@Stable
class OrbitState(
    initial: Int,
    private val count: Int,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    var position by mutableFloatStateOf(initial.toFloat())
        private set
    var dragging by mutableStateOf(false)
        private set
    private var moving by mutableStateOf(false)
    private var target = initial
    private var job: Job? = null

    /** The moon at the front, or nearest it while the orbit turns. */
    val focused: Int get() = Math.floorMod(position.roundToInt(), count)

    /** At rest with a moon at the front. */
    val settled: Boolean get() = !dragging && !moving

    /** Turns the shortest way round until [index] is at the front. */
    fun focus(index: Int) {
        val from = if (moving) target else position.roundToInt()
        var delta = Math.floorMod(index - from, count)
        if (delta > count / 2) delta -= count
        settleAt(from + delta, 0f)
    }

    /** One moon on: +1 brings the next moon round from the right. */
    fun step(by: Int) = settleAt((if (moving) target else position.roundToInt()) + by, 0f)

    fun dragBy(moons: Float) {
        job?.cancel()
        moving = false
        dragging = true
        position += moons
    }

    /** Ends a drag moving at [velocity] moons per second: snaps to the nearest moon, a flick carrying one further. */
    fun release(velocity: Float) {
        dragging = false
        val here = position.roundToInt()
        val ahead = (position + velocity * FLING_LOOKAHEAD).roundToInt().coerceIn(here - 1, here + 1)
        settleAt(ahead, velocity)
    }

    private fun settleAt(to: Int, velocity: Float) {
        target = to
        job?.cancel()
        if (reduceMotion) {
            position = to.toFloat()
            moving = false
            return
        }
        moving = true
        val launched = scope.launch {
            animate(position, to.toFloat(), velocity, spring(dampingRatio = 0.82f, stiffness = 180f)) { v, _ ->
                position = v
            }
        }
        job = launched
        launched.invokeOnCompletion { if (job === launched) moving = false }
    }
}

/**
 * The orbital selector (SPEC.md §1.4): the dying-star sun, flat sandstone with one shed
 * shell ring and the wordmark across it, and a moon per tool on a tilted elliptical
 * orbit. The front moon sits low over the sun; moons further round are smaller, dimmer
 * and pass behind it. Swiping turns the orbit; tapping a moon brings it to the front, and
 * tapping the front moon, once settled, calls [onOpen].
 */
@Composable
fun Orbit(state: OrbitState, tools: List<Tool>, onOpen: (Tool) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Next tool") { state.step(1); true },
                CustomAccessibilityAction("Previous tool") { state.step(-1); true },
            )
        },
    ) {
        val w = maxWidth
        val h = maxHeight
        val sunR = minOf(w * 0.27f, h * 0.30f, 124.dp)
        val moonR = sunR * 0.40f
        val rx = minOf(w * 0.40f, w / 2 - moonR * 0.75f)
        val ry = sunR * 0.8f
        val shellR = sunR * 1.22f
        // The sun's shell above and the front moon below, centred together in the space.
        val cy = (h - shellR - (ry + moonR)) / 2 + shellR
        val cx = w / 2
        val density = LocalDensity.current
        val stepPx = with(density) { rx.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state, stepPx) {
                    val tracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = { state.release(-tracker.calculateVelocity().x / stepPx) },
                        onDragCancel = { state.release(0f) },
                    ) { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        // Dragging left brings the moon on the right to the front.
                        state.dragBy(-dx / stepPx)
                    }
                },
        ) {
            // The orbit's far half and the shell, behind everything.
            Canvas(Modifier.fillMaxSize().zIndex(-5f)) {
                val c = Offset(cx.toPx(), cy.toPx())
                drawCircle(Palette.Sandstone.copy(alpha = 0.45f), shellR.toPx(), c, style = Stroke(1.5.dp.toPx()))
                drawOrbitHalf(c, rx.toPx(), ry.toPx(), back = true)
            }
            Sun(sunR, Modifier.offset(cx - sunR, cy - sunR))
            // The orbit's near half, over the sun and under the front moon.
            Canvas(Modifier.fillMaxSize().zIndex(0.5f)) {
                drawOrbitHalf(Offset(cx.toPx(), cy.toPx()), rx.toPx(), ry.toPx(), back = false)
            }

            tools.forEachIndexed { i, tool ->
                val angle = (i - state.position) * (2 * PI.toFloat() / tools.size)
                val depth = cos(angle) // 1 at the front, -1 at the back
                val near = (depth + 1f) / 2f
                val focused = i == state.focused
                Moon(
                    tool = tool,
                    radius = moonR,
                    focused = focused,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (cx + rx * sin(angle) - moonR).roundToPx(),
                                (cy + ry * depth - moonR).roundToPx(),
                            )
                        }
                        // In front of the sun on the near half of the orbit, behind it on the far half.
                        .zIndex(if (depth >= 0f) 1f + depth else depth - 1f)
                        .graphicsLayer {
                            val scale = 0.6f + 0.4f * near
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.5f + 0.5f * near
                        },
                    onClick = { if (focused && state.settled) onOpen(tool) else state.focus(i) },
                )
            }
        }
    }
}

/** The near (lower) or far (upper) half of the orbit's ellipse, as a faint line. */
private fun DrawScope.drawOrbitHalf(c: Offset, rx: Float, ry: Float, back: Boolean) {
    drawArc(
        color = Palette.Line,
        startAngle = if (back) 180f else 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(c.x - rx, c.y - ry),
        size = Size(2 * rx, 2 * ry),
        style = Stroke(1.dp.toPx()),
    )
}

/**
 * The sun: a flat sandstone disc with SHOWDIST across it in black, sized to the disc and
 * not to the system font scale, since it's the app's mark.
 */
@Composable
private fun Sun(radius: Dp, modifier: Modifier) {
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier
            .size(radius * 2)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = "Showdist"
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(Palette.Sandstone) }
        // Eight 0.6 em glyphs with 0.12 em spacing span about 5.6 em; set that to 1.5 radii.
        Text(
            "SHOWDIST",
            modifier = Modifier.offset(y = -radius * 0.12f),
            maxLines = 1,
            softWrap = false,
            style = Type.Wordmark.copy(
                fontSize = (radius.value * 1.5f / 5.64f / fontScale).sp,
                letterSpacing = 0.12.em,
            ),
        )
    }
}

/**
 * A moon: a near-black disc with a hairline black gap around it, so it reads cleanly over
 * the sun, a ring (sandstone when focused) and the tool's picture in sandstone.
 */
@Composable
private fun Moon(tool: Tool, radius: Dp, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(radius * 2)
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (focused) "Open" else "Show guide",
                onClick = onClick,
            )
            .semantics {
                contentDescription = tool.spoken
                selected = focused
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val gap = 3.dp.toPx()
            drawCircle(Palette.Black, r)
            drawCircle(Palette.Surface, r - gap)
            val ring = (if (focused) 2.5.dp else 1.5.dp).toPx()
            drawCircle(
                if (focused) Palette.Sandstone else Palette.TextMuted,
                r - gap - ring / 2f,
                style = Stroke(ring),
            )
            tool.drawPicture(this, center, r * 0.5f, Palette.Sandstone)
        }
    }
}

/** Each tool's picture, within [r] of [c]: a dimension line, a vial, a bullseye. */
private fun Tool.drawPicture(scope: DrawScope, c: Offset, r: Float, color: Color) = with(scope) {
    val stroke = 2.dp.toPx()
    when (this@drawPicture) {
        Tool.Measure -> {
            val cap = r * 0.45f
            drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), stroke, StrokeCap.Round)
            for (x in floatArrayOf(c.x - r, c.x + r)) {
                drawLine(color, Offset(x, c.y - cap), Offset(x, c.y + cap), stroke, StrokeCap.Round)
                drawCircle(color, r * 0.16f, Offset(x, c.y))
            }
        }
        Tool.Tube -> {
            val half = r * 0.42f
            drawRoundRect(
                color,
                Offset(c.x - r, c.y - half),
                Size(2 * r, 2 * half),
                CornerRadius(half),
                style = Stroke(stroke),
            )
            drawCircle(color, half * 0.6f, c)
        }
        Tool.Bullseye -> {
            drawCircle(color, r, c, style = Stroke(stroke))
            drawCircle(color, r * 0.5f, c, style = Stroke(stroke * 0.75f))
            drawCircle(color, r * 0.2f, c)
        }
    }
}
