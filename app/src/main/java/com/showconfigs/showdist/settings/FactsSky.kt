package com.showconfigs.showdist.settings

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The silhouettes' "faintly lighter black": barely there. */
private val Silhouette = Color(0xFF141414)

/** The asteroids' dim grey, at the head; the tail fades to nothing. */
private val Meteor = Color(0xFF2C2C2C)

private val ITEM = 56.dp
private const val ITEM_COUNT = 4

/** Shapes fade out over this distance from the band's top and bottom edges, so none is cut off. */
private val EDGE_FADE = 40.dp

/** Silhouette drift: a few dp a second, with the slowest of turns. */
private const val DRIFT_DP_S = 3.5f
private const val TURN_DEG_S = 1.2f

private val METEOR_LENGTH = 36.dp
private const val METEOR_MIN_S = 8f
private const val METEOR_MAX_S = 12f
private const val METEOR_GAP_MIN_S = 4f
private const val METEOR_GAP_MAX_S = 9f

/** How far a meteor travels, as a share of the band's width. */
private const val METEOR_TRAVEL = 0.45f

/** Layers move at most this often: smooth for things this slow, and light on low-end phones. */
private const val STEP_NANOS = 33_000_000L

/**
 * The backdrop behind the About page's facts (SPEC.md §1.6): tiny, dim line drawings of
 * measuring tools drifting very slowly, and now and then a small grey asteroid gliding
 * through in a random direction, fading out near the top and bottom edges.
 * Every shape is drawn once into a bitmap; motion only moves and fades layers, at about
 * 30 updates a second, so nothing redraws. With [animate] false (paused, or reduced motion)
 * everything holds still. It exists only while the facts are showing.
 */
@Composable
fun FactsSky(animate: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val art = remember(density) { SkyArt(density) }
    BoxWithConstraints(modifier.clipToBounds()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val state = remember(w, h) { SkyState(w, h, density) }
        LaunchedEffect(state, animate) {
            if (!animate) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                if (now - last >= STEP_NANOS) {
                    state.step((now - last) / 1e9f)
                    last = now
                }
            }
        }
        val fade = with(LocalDensity.current) { EDGE_FADE.toPx() }
        for (item in state.items) {
            Spacer(
                Modifier
                    .size(ITEM)
                    .graphicsLayer {
                        translationX = item.x
                        translationY = item.y
                        rotationZ = item.rotation
                        alpha = state.edgeAlpha(item.y + size.height / 2f, fade)
                    }
                    .drawBehind { drawImage(art.tools[item.tool]) },
            )
        }
        for (m in state.meteors) {
            Box(
                Modifier
                    .size(METEOR_LENGTH, 4.dp)
                    .graphicsLayer {
                        alpha = m.alpha * state.edgeAlpha(m.y, fade)
                        translationX = m.x
                        translationY = m.y
                        rotationZ = m.angle
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    }
                    .drawBehind { drawImage(art.meteor) },
            )
        }
    }
}

private class SkyItem(var tool: Int, x: Float, y: Float, val vx: Float, val vy: Float, rotation: Float, val turn: Float) {
    var x by mutableFloatStateOf(x)
    var y by mutableFloatStateOf(y)
    var rotation by mutableFloatStateOf(rotation)
}

private class SkyMeteor {
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(0f)
    var t = 0f
    var duration = 0f
    var active = false
    var startX = 0f
    var startY = 0f
    var angle = 0f
    var dx = 1f
    var dy = 0f
}

/** Positions only; [step] advances them. Nothing here allocates per step. */
private class SkyState(private val w: Float, private val h: Float, private val density: Float) {
    private val random = Random(1620) // Gunter's chain
    private val itemPx = ITEM.value * density
    private val meteorPx = METEOR_LENGTH.value * density
    private var untilNext = 1.5f

    val items = List(ITEM_COUNT) { i ->
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val speed = DRIFT_DP_S * density * (0.6f + 0.8f * random.nextFloat())
        // Spread across the width to start, so they don't crowd; drifting mostly sideways,
        // so they stay in the band (wrapping at its edges) rather than fading out of it.
        SkyItem(
            tool = (i * 3 + random.nextInt(3)) % TOOL_COUNT,
            x = (i + 0.5f) * w / ITEM_COUNT - itemPx / 2 + (random.nextFloat() - 0.5f) * itemPx * 0.4f,
            y = (0.2f + 0.6f * random.nextFloat()) * h - itemPx / 2,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed * 0.35f,
            rotation = random.nextFloat() * 360f,
            turn = (if (random.nextBoolean()) 1f else -1f) * TURN_DEG_S * (0.5f + random.nextFloat()),
        )
    }
    val meteors = List(2) { SkyMeteor() }

    fun step(dt: Float) {
        for (item in items) {
            item.x = wrap(item.x + item.vx * dt, w)
            item.y = wrap(item.y + item.vy * dt, h)
            item.rotation += item.turn * dt
        }
        untilNext -= dt
        if (untilNext <= 0f) {
            meteors.firstOrNull { !it.active }?.let { launch(it) }
            untilNext = METEOR_GAP_MIN_S + random.nextFloat() * (METEOR_GAP_MAX_S - METEOR_GAP_MIN_S)
        }
        for (m in meteors) {
            if (!m.active) continue
            m.t += dt
            val p = m.t / m.duration
            if (p >= 1f) {
                m.active = false
                m.alpha = 0f
                continue
            }
            val travel = w * METEOR_TRAVEL * p
            m.x = m.startX + m.dx * travel
            m.y = m.startY + m.dy * travel
            // In over the first 15%, out over the last 30%.
            m.alpha = minOf(p / 0.15f, (1f - p) / 0.3f, 1f)
        }
    }

    private fun launch(m: SkyMeteor) {
        m.active = true
        m.t = 0f
        m.duration = METEOR_MIN_S + random.nextFloat() * (METEOR_MAX_S - METEOR_MIN_S)
        // A random heading; the layer turns about its head (its right end), which starts
        // somewhere in the band and glides about half the width.
        m.angle = random.nextFloat() * 360f
        m.dx = cos(m.angle * PI / 180).toFloat()
        m.dy = sin(m.angle * PI / 180).toFloat()
        m.startX = random.nextFloat() * w - meteorPx
        m.startY = random.nextFloat() * h
        m.x = m.startX
        m.y = m.startY
    }

    /** 1 inside the band, falling to 0 within [fade] px of its top or bottom edge. */
    fun edgeAlpha(y: Float, fade: Float): Float =
        (minOf(y, h - y) / fade).coerceIn(0f, 1f)

    /** Wraps a coordinate so items leaving one edge drift back in from the other. */
    private fun wrap(v: Float, extent: Float): Float {
        val lo = -itemPx
        val span = extent + itemPx * 2
        return if (v < lo) v + span else if (v > extent + itemPx) v - span else v
    }
}

private const val TOOL_COUNT = 10

/** Every shape the sky uses, drawn once per density. */
private class SkyArt(private val d: Float) {
    val tools: List<ImageBitmap> = List(TOOL_COUNT) { renderTool(it) }
    val meteor: ImageBitmap = renderMeteor()

    private fun renderMeteor(): ImageBitmap {
        val w = (METEOR_LENGTH.value * d).toInt()
        val h = (4 * d).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap)
        val head = Meteor.toArgb()
        val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, head and 0x00FFFFFF, head, Shader.TileMode.CLAMP)
            strokeWidth = 1.5f * d
            strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(0f, h / 2f, w - 2 * d, h / 2f, tail)
        c.drawCircle(w - 2 * d, h / 2f, 1.5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = head })
        return bitmap.asImageBitmap()
    }

    /** Simple line art of modern measuring tools in a unit box, scaled to [ITEM]. */
    private fun renderTool(i: Int): ImageBitmap {
        val s = ITEM.value * d
        val bitmap = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Silhouette.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 2f * d
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fill = Paint(p).apply { style = Paint.Style.FILL }
        fun line(x0: Float, y0: Float, x1: Float, y1: Float) = c.drawLine(x0 * s, y0 * s, x1 * s, y1 * s, p)
        fun circle(x: Float, y: Float, r: Float, paint: Paint = p) = c.drawCircle(x * s, y * s, r * s, paint)
        fun rect(l: Float, t: Float, r: Float, b: Float, rad: Float = 0f) = c.drawRoundRect(RectF(l * s, t * s, r * s, b * s), rad * s, rad * s, p)
        when (i) {
            0 -> { // Tape measure: case, reel, lock button, tape and hook
                rect(0.1f, 0.3f, 0.58f, 0.76f, 0.1f)
                circle(0.34f, 0.53f, 0.11f)
                rect(0.26f, 0.24f, 0.42f, 0.3f, 0.02f)
                line(0.58f, 0.7f, 0.94f, 0.7f)
                line(0.94f, 0.7f, 0.94f, 0.62f)
                for (k in 1..4) line(0.6f + k * 0.07f, 0.7f, 0.6f + k * 0.07f, 0.67f)
            }
            1 -> { // Laser distance meter: body, screen, keys, and its beam
                rect(0.34f, 0.3f, 0.66f, 0.94f, 0.06f)
                rect(0.39f, 0.38f, 0.61f, 0.54f, 0.02f)
                for (r in 0..1) for (k in 0..1) circle(0.44f + k * 0.12f, 0.66f + r * 0.12f, 0.025f)
                for (k in 0..3) line(0.5f, 0.26f - k * 0.07f, 0.5f, 0.23f - k * 0.07f)
            }
            2 -> { // Box level with two vials
                rect(0.04f, 0.4f, 0.96f, 0.6f, 0.02f)
                rect(0.4f, 0.45f, 0.6f, 0.55f, 0.04f)
                circle(0.52f, 0.5f, 0.02f, fill)
                rect(0.12f, 0.45f, 0.2f, 0.55f, 0.03f)
            }
            3 -> { // Digital caliper: beam, fixed and sliding jaws, display
                rect(0.06f, 0.36f, 0.96f, 0.44f, 0.01f)
                line(0.1f, 0.44f, 0.1f, 0.78f)
                line(0.1f, 0.78f, 0.16f, 0.72f)
                rect(0.3f, 0.3f, 0.56f, 0.56f, 0.03f)
                rect(0.34f, 0.34f, 0.52f, 0.42f, 0.01f)
                line(0.3f, 0.56f, 0.3f, 0.78f)
                line(0.3f, 0.78f, 0.24f, 0.72f)
            }
            4 -> { // Speed square, with its markings
                val path = Path().apply {
                    moveTo(0.1f * s, 0.88f * s); lineTo(0.1f * s, 0.12f * s); lineTo(0.86f * s, 0.88f * s); close()
                }
                c.drawPath(path, p)
                line(0.04f, 0.88f, 0.1f, 0.88f)
                for (k in 1..6) line(0.1f, 0.12f + k * 0.11f, 0.15f, 0.12f + k * 0.11f)
                rect(0.2f, 0.56f, 0.34f, 0.76f, 0.02f)
            }
            5 -> { // Cross-line laser level on a short tripod, its beams
                rect(0.38f, 0.38f, 0.62f, 0.6f, 0.04f)
                circle(0.5f, 0.49f, 0.04f)
                line(0.5f, 0.6f, 0.36f, 0.86f)
                line(0.5f, 0.6f, 0.64f, 0.86f)
                line(0.5f, 0.6f, 0.5f, 0.86f)
                for (k in 0..2) {
                    line(0.3f - k * 0.1f, 0.49f, 0.25f - k * 0.1f, 0.49f)
                    line(0.7f + k * 0.1f, 0.49f, 0.75f + k * 0.1f, 0.49f)
                    line(0.5f, 0.32f - k * 0.1f, 0.5f, 0.27f - k * 0.1f)
                }
            }
            6 -> { // Measuring wheel with counter and handle
                circle(0.32f, 0.7f, 0.22f)
                circle(0.32f, 0.7f, 0.03f, fill)
                line(0.32f, 0.7f, 0.84f, 0.12f)
                line(0.78f, 0.08f, 0.92f, 0.2f)
                rect(0.52f, 0.4f, 0.64f, 0.5f, 0.02f)
            }
            7 -> { // Total station on its tripod: body, screen, telescope
                rect(0.36f, 0.26f, 0.64f, 0.5f, 0.03f)
                rect(0.41f, 0.32f, 0.59f, 0.42f, 0.01f)
                rect(0.42f, 0.14f, 0.58f, 0.26f, 0.03f)
                line(0.5f, 0.5f, 0.2f, 0.95f)
                line(0.5f, 0.5f, 0.5f, 0.95f)
                line(0.5f, 0.5f, 0.8f, 0.95f)
            }
            8 -> { // Steel rule with graduations
                rect(0.04f, 0.42f, 0.96f, 0.58f, 0.01f)
                for (k in 1..15) {
                    val x = 0.04f + k * 0.92f / 16
                    line(x, 0.42f, x, if (k % 4 == 0) 0.52f else 0.47f)
                }
                circle(0.9f, 0.5f, 0.02f)
            }
            else -> { // Digital angle finder: two arms on a hinge, with its display
                rect(0.08f, 0.62f, 0.9f, 0.72f, 0.02f)
                c.save()
                c.rotate(-32f, 0.12f * s, 0.67f * s)
                rect(0.08f, 0.62f, 0.9f, 0.72f, 0.02f)
                c.restore()
                circle(0.12f, 0.67f, 0.04f)
                rect(0.5f, 0.64f, 0.72f, 0.7f, 0.01f)
            }
        }
        return bitmap.asImageBitmap()
    }
}
