package com.showconfigs.showdist.menu

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import android.graphics.Bitmap
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** The orbit's height in sun radii: the shell (1.22) above the centre, the front moon (0.8 + 0.4) below. */
const val ORBIT_SPAN = 2.42f

/** Room kept above and below the orbit's span, in total. */
val ORBIT_MARGIN = 32.dp

/** The sun's radius for a given width, before any height limit (SPEC.md §1.4). */
fun sunRadiusFor(width: Dp): Dp = minOf(width * 0.27f, 124.dp)

/** The sun's radius in an orbit area [width] × [height]: fits the shell and front moon. */
fun sunRadiusFor(width: Dp, height: Dp): Dp = minOf(sunRadiusFor(width), (height - ORBIT_MARGIN) / ORBIT_SPAN)

/** The orbit's horizontal radius, which is also how far a drag must go to turn one moon. */
fun orbitRadiusX(width: Dp, sunR: Dp): Dp = minOf(width * 0.40f, width / 2 - sunR * 0.40f * 0.75f)

/**
 * Horizontal swipes turn the orbit (SPEC.md §1.4): dragging [stepPx] turns it one moon, and
 * a flick carries on. Used on the orbit and on the guide text beneath it. Taps and holds
 * pass through (holding the sun is the collapse's alone).
 */
fun Modifier.orbitSwipe(state: OrbitState, stepPx: Float): Modifier = pointerInput(state, stepPx) {
    val tracker = VelocityTracker()
    detectHorizontalDragGestures(
        onDragStart = { tracker.resetTracking() },
        onDragEnd = { state.release(-tracker.calculateVelocity().x / (stepPx * DRAG_PER_MOON)) },
        onDragCancel = { state.release(0f) },
    ) { change, dx ->
        tracker.addPosition(change.uptimeMillis, change.position)
        change.consume()
        // Dragging left brings the moon on the right to the front.
        state.dragBy(-dx / (stepPx * DRAG_PER_MOON))
    }
}

/** How far ahead a flick carries the orbit before it snaps, in seconds of its speed. */
private const val FLING_LOOKAHEAD = 0.12f

/** A drag must turn the orbit this far (in moons) from where it started to move on a moon. */
private const val SNAP_THRESHOLD = 0.35f

/** Drag distance per moon, in orbit radii: higher turns it less per finger movement. */
private const val DRAG_PER_MOON = 1.6f

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
    private var dragFrom = initial

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
        if (!dragging) dragFrom = if (moving) target else position.roundToInt()
        moving = false
        dragging = true
        position += moons
    }

    /**
     * Ends a drag moving at [velocity] moons per second. It resists: unless the drag (plus a
     * short look-ahead for a flick) has turned the orbit [SNAP_THRESHOLD] of a moon from where
     * it started, it springs back; otherwise it moves on one moon in that direction.
     */
    fun release(velocity: Float) {
        dragging = false
        val moved = position + velocity * FLING_LOOKAHEAD - dragFrom
        val to = when {
            moved >= SNAP_THRESHOLD -> dragFrom + 1
            moved <= -SNAP_THRESHOLD -> dragFrom - 1
            else -> dragFrom
        }
        settleAt(to, velocity)
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
            // Stiffer than before, so the moons snap firmly into place.
            animate(position, to.toFloat(), velocity, spring(dampingRatio = 0.85f, stiffness = 300f)) { v, _ ->
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
 * tapping the front moon, once settled, calls [onOpen]. Holding the sun runs the [egg].
 * [aboveSun] is centred in the space between the top of this area and the top of the sun.
 */
@Composable
fun Orbit(
    state: OrbitState,
    egg: CollapseEgg,
    tools: List<Tool>,
    onOpen: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    aboveSun: @Composable () -> Unit = {},
) {
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
        // Fits the shell above and the front moon below (2.42 sun radii) in the height given.
        val sunR = sunRadiusFor(w, h)
        val moonR = sunR * 0.40f
        val rx = orbitRadiusX(w, sunR)
        val ry = sunR * 0.8f
        val shellR = sunR * 1.22f
        // The sun's shell above and the front moon below, centred together in the space.
        val cy = (h - shellR - (ry + moonR)) / 2 + shellR
        val cx = w / 2
        val density = LocalDensity.current
        val stepPx = with(density) { rx.toPx() }
        val glowPx = with(density) { (sunR * GLOW_SCALE).roundToPx() }
        val glows = remember(glowPx) { SunGlows(glowPx) }

        Box(Modifier.fillMaxSize().orbitSwipe(state, stepPx)) {
            // The orbit's far half and the shell, behind everything.
            Canvas(Modifier.fillMaxSize().zIndex(-5f)) {
                val c = Offset(cx.toPx(), cy.toPx())
                drawCircle(Palette.Sandstone.copy(alpha = 0.45f), shellR.toPx(), c, style = Stroke(1.5.dp.toPx()))
                drawOrbitHalf(c, rx.toPx(), ry.toPx(), back = true)
            }
            // The sun's glow (SPEC.md §1.4): pre-rendered, sandstone giving way to white as
            // the moons close in; only its layer's alpha and scale change.
            Canvas(
                Modifier
                    .offset(cx - sunR * GLOW_SCALE, cy - sunR * GLOW_SCALE)
                    .size(sunR * GLOW_SCALE * 2)
                    .zIndex(-4f)
                    .graphicsLayer {
                        val g = egg.glow
                        alpha = if (g > 0f) 1f else 0f
                        val s = 0.8f + 0.4f * g
                        scaleX = s
                        scaleY = s
                    },
            ) {
                val g = egg.glow
                drawImage(glows.sandstone, alpha = (2f * g).coerceAtMost(1f) * (1f - g))
                drawImage(glows.white, alpha = g)
            }
            Sun(sunR, { egg.glow }, Modifier.offset(cx - sunR, cy - sunR).collapseOnHold(egg))
            Box(Modifier.fillMaxWidth().height(cy - sunR), contentAlignment = Alignment.Center) { aboveSun() }
            // The orbit's near half, over the sun and under the front moon.
            Canvas(Modifier.fillMaxSize().zIndex(0.5f)) {
                drawOrbitHalf(Offset(cx.toPx(), cy.toPx()), rx.toPx(), ry.toPx(), back = false)
            }

            // The moving values (position, spin, pull) are read only in the layout and draw
            // lambdas below, so turning the orbit doesn't recompose it every frame.
            val step = 2 * PI.toFloat() / tools.size
            val angleOf = { i: Int -> (i - state.position) * step + egg.spin }
            val focusedIndex by remember { derivedStateOf { state.focused } }
            tools.forEachIndexed { i, tool ->
                // In front of the sun on the near half of the orbit, behind it on the far half,
                // and ordered by depth among the moons. Discrete, so it only changes (and
                // recomposes) when a moon passes another or the sun.
                val layer by remember(i, tools.size) {
                    derivedStateOf {
                        val depth = cos(angleOf(i))
                        var below = 0
                        for (j in tools.indices) if (j != i && cos(angleOf(j)) < depth) below++
                        (if (depth >= 0f) 1f else -2f) + below * 0.1f
                    }
                }
                val focused = i == focusedIndex
                Moon(
                    tool = tool,
                    radius = moonR,
                    focused = focused,
                    light = { egg.glow },
                    // Darker the further round it is; the egg's light lifts it.
                    shade = { MOON_SHADE * (1f - (cos(angleOf(i)) + 1f) / 2f) * (1f - egg.glow) },
                    modifier = Modifier
                        .offset {
                            val angle = angleOf(i)
                            // Drawn in towards the sun by the egg (SPEC.md §1.4).
                            val reach = 1f - egg.pull
                            IntOffset(
                                (cx + rx * reach * sin(angle) - moonR).roundToPx(),
                                (cy + ry * reach * cos(angle) - moonR).roundToPx(),
                            )
                        }
                        .zIndex(layer)
                        .graphicsLayer {
                            val near = (cos(angleOf(i)) + 1f) / 2f // 1 at the front, 0 at the back
                            val scale = (0.6f + 0.4f * near) * (1f - 0.6f * egg.pull)
                            scaleX = scale
                            scaleY = scale
                            // Always opaque: depth shows in size and stacking, never by fading.
                        },
                    onClick = {
                        if (egg.active) return@Moon
                        if (focused && state.settled) onOpen(tool) else state.focus(i)
                    },
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

/** How far the wordmark sits above the sun's centre, in sun radii. */
private const val WORDMARK_LIFT = 0.04f

/** The outline that thickens the wordmark, as a share of its font size. */
private const val WORDMARK_STROKE = 0.055f

/**
 * SHOWDIST in [style], extra bold: the filled glyphs with an outline of the same colour
 * over them, which thickens every stroke evenly (JetBrains Mono's heaviest bundled face
 * is Bold).
 */
@Composable
private fun BoldWordmark(style: TextStyle, modifier: Modifier) {
    val strokePx = with(LocalDensity.current) { style.fontSize.toPx() } * WORDMARK_STROKE
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("SHOWDIST", maxLines = 1, softWrap = false, style = style)
        Text(
            "SHOWDIST",
            Modifier.clearAndSetSemantics {},
            maxLines = 1,
            softWrap = false,
            style = style.copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round)),
        )
    }
}

/** How far the light washes out the wordmark at full glow (white over black). */
private const val WORDMARK_WASH = 0.85f

/** The glow's radius, in sun radii. */
private const val GLOW_SCALE = 2f

/**
 * The sun's glow, drawn once per size into two bitmaps: a soft radial falloff in sandstone
 * and in white. No live blur; the egg only fades and scales them.
 */
private class SunGlows(radiusPx: Int) {
    val sandstone = render(radiusPx, Palette.Sandstone)
    val white = render(radiusPx, Palette.White)

    private fun render(r: Int, color: Color): ImageBitmap {
        val side = (r * 2).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = color.toArgb()
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                side / 2f, side / 2f, side / 2f,
                intArrayOf(withAlpha(c, 0.85f), withAlpha(c, 0.55f), withAlpha(c, 0.18f), withAlpha(c, 0f)),
                floatArrayOf(0.35f, 0.5f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        android.graphics.Canvas(bitmap).drawCircle(side / 2f, side / 2f, side / 2f, paint)
        return bitmap.asImageBitmap()
    }

    private fun withAlpha(argb: Int, a: Float) = (argb and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
}

/**
 * The sun: a flat sandstone disc with SHOWDIST across it in black, sized to the disc and
 * not to the system font scale, since it's the app's mark. A white disc over it fades in
 * with [glow] (SPEC.md §1.4), on its own layer, so the sun turns white without redrawing.
 */
@Composable
private fun Sun(radius: Dp, glow: () -> Float, modifier: Modifier) {
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
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = glow() }) { drawCircle(Palette.White) }
        // Eight 0.6 em glyphs with 0.12 em spacing span about 5.6 em; set that to 1.5 radii.
        val wordmark = Type.Wordmark.copy(
            fontSize = (radius.value * 1.5f / 5.64f / fontScale).sp,
            letterSpacing = 0.12.em,
        )
        // On the sun's centre, raised very slightly, and heavier than the bundled Bold: a thin
        // outline over the fill.
        val lift = Modifier.offset(y = -radius * WORDMARK_LIFT)
        BoldWordmark(wordmark, lift)
        // The light washes the wordmark out too: a white copy fades in over it with the glow,
        // leaving it a faint grey on the white sun at the end.
        BoldWordmark(
            wordmark.copy(color = Palette.White),
            lift.graphicsLayer { alpha = WORDMARK_WASH * glow() }.clearAndSetSemantics {},
        )
    }
}

/**
 * A moon: a near-black disc with a hairline black gap around it, so it reads cleanly over
 * the sun, a ring (sandstone when focused) and the tool's picture in sandstone. As the
 * collapse brings it in, a white disc fades over its body with [light] (SPEC.md §1.4), on
 * its own layer.
 */
@Composable
private fun Moon(
    tool: Tool,
    radius: Dp,
    focused: Boolean,
    light: () -> Float,
    shade: () -> Float,
    modifier: Modifier,
    onClick: () -> Unit,
) {
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
            drawCircle(Palette.Black, r)
            drawCircle(Palette.Surface, r - 3.dp.toPx())
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f * light() }) {
            drawCircle(Palette.White, size.minDimension / 2f - 3.dp.toPx())
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val gap = 3.dp.toPx()
            val ring = (if (focused) 2.5.dp else 1.5.dp).toPx()
            drawCircle(
                if (focused) Palette.Sandstone else Palette.TextMuted,
                r - gap - ring / 2f,
                style = Stroke(ring),
            )
            tool.drawPicture(this, center, r * 0.5f, Palette.Sandstone, dots = false)
        }
        // Depth shading (SPEC.md §1.4): opaque black over the body, ring and lines, on its own
        // layer so turning the orbit only changes its alpha.
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = shade() }) {
            drawCircle(Palette.Black, size.minDimension / 2f - 3.dp.toPx())
        }
        // The white dots stay bright above the shading, as on the app icon.
        Canvas(Modifier.fillMaxSize()) {
            tool.drawPicture(this, center, size.minDimension / 4f, Palette.Sandstone, dots = true)
        }
    }
}

/** The app icon's end dots, as a share of half the rule's length (6 of 20, SPEC.md §8.1). */
private const val ICON_DOT = 0.3f

/** The icon's end ticks reach this far either side of the rule (11 of 20). */
private const val ICON_TICK = 0.55f

/**
 * Each tool's picture, within [r] of [c]: a dimension line, a vial, a bullseye. Lines are in
 * [color]; the dots are always pure white, as on the app icon (SPEC.md §1.4, §8.1), whatever
 * the moon's state, each with a faint dark edge so it still reads when the collapse lights
 * the moon toward white.
 */
private fun Tool.drawPicture(scope: DrawScope, c: Offset, r: Float, color: Color, dots: Boolean) = with(scope) {
    val stroke = 2.dp.toPx()
    val edge = 0.75.dp.toPx()
    // Called twice: lines only (dots = false), then dots only, so shading can sit between.
    fun whiteDot(at: Offset, radius: Float) {
        if (!dots) return
        drawCircle(DotEdge, radius + edge, at)
        drawCircle(Palette.White, radius, at)
    }
    fun line(a: Offset, b: Offset) {
        if (!dots) drawLine(color, a, b, stroke, StrokeCap.Round)
    }
    when (this@drawPicture) {
        Tool.Measure -> {
            // The app icon's mark: a rule between two end ticks, a white point on each end.
            val cap = r * ICON_TICK
            line(Offset(c.x - r, c.y), Offset(c.x + r, c.y))
            for (x in floatArrayOf(c.x - r, c.x + r)) line(Offset(x, c.y - cap), Offset(x, c.y + cap))
            for (x in floatArrayOf(c.x - r, c.x + r)) whiteDot(Offset(x, c.y), r * ICON_DOT)
        }
        Tool.Tube -> {
            val half = r * 0.42f
            if (!dots) {
                drawRoundRect(color, Offset(c.x - r, c.y - half), Size(2 * r, 2 * half), CornerRadius(half), style = Stroke(stroke))
            }
            whiteDot(c, half * 0.6f) // the bubble
        }
        Tool.Bullseye -> {
            if (!dots) {
                drawCircle(color, r, c, style = Stroke(stroke))
                drawCircle(color, r * 0.5f, c, style = Stroke(stroke * 0.75f))
            }
            whiteDot(c, r * 0.2f) // the bubble, at centre
        }
    }
}

/** How dark a moon gets at the very back of the orbit. */
private const val MOON_SHADE = 0.75f

/** The faint dark edge round the white dots. */
private val DotEdge = Color(0x59000000)
