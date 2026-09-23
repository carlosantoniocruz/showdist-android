package com.showconfigs.showdist.measure

import android.opengl.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

/** Reticle period: one clockwise turn every 11 s (SPEC.md §3.1). */
const val RETICLE_PERIOD_S = 11f

/** Max points per measurement (SPEC.md §2.5). */
const val MAX_POINTS = 24

private const val NEAR_M = 0.05f
private const val FAR_M = 100f

/**
 * Per-frame measuring state (SPEC.md §2.4, §2.5). [onFrame] is called on every render frame
 * but only does work when ARCore delivers a new camera image. Everything runs on the main
 * thread: SceneView's frame callback, the UI actions and the overlay's draw.
 *
 * Per-point and per-label data lives in preallocated arrays, and label text is rewritten
 * only when its displayed value changes, so a frame allocates nothing beyond what ARCore's
 * own API returns. The overlay reads the snapshot state below only inside its draw lambda,
 * so new frames trigger a redraw, never a recomposition.
 */
class MeasureTracker {
    /** True when the screen-centre hit test found a usable surface in front of the camera. */
    var hasHit by mutableStateOf(false)
        private set

    /** Seconds into the current reticle turn, sampled once per new camera image. */
    var spinSeconds by mutableFloatStateOf(0f)
        private set

    var pointCount by mutableIntStateOf(0)
        private set

    /** Sum of all segments. Only updated when the displayed value would change. */
    var totalMetres by mutableDoubleStateOf(0.0)
        private set

    /** Unit system for all distances. Fixed to Imperial until the unit toggle exists. */
    var units = UnitSystem.Imperial
        set(value) {
            if (field == value) return
            field = value
            // Keys are unit-specific; force every label and the total to reformat.
            totalKey = Long.MIN_VALUE
            segmentKey.fill(Long.MIN_VALUE)
            liveKey = Long.MIN_VALUE
        }

    // Screen positions below are normalised (0..1, top-left origin): x at 2i, y at 2i+1.

    /** Each point's screen position. */
    val screenXY = FloatArray(MAX_POINTS * 2)

    /** Whether each point is drawn this frame (SPEC.md §2.4 "anchor visibility"). */
    val visible = BooleanArray(MAX_POINTS)

    /** Segment i runs from point i−1 to point i; its label sits at its projected world midpoint. */
    val segmentMidXY = FloatArray(MAX_POINTS * 2)
    val segmentMidInFront = BooleanArray(MAX_POINTS)
    val segmentText = Array(MAX_POINTS) { CharArray(DISTANCE_TEXT_CAPACITY) }
    val segmentTextLength = IntArray(MAX_POINTS)

    /** The dashed live line from the last point to the reticle hit (SPEC.md §3.2). */
    var liveVisible = false
        private set
    val hitXY = FloatArray(2)
    val liveMidXY = FloatArray(2)
    var liveMidInFront = false
        private set
    val liveText = CharArray(DISTANCE_TEXT_CAPACITY)
    var liveTextLength = 0
        private set

    /** Anchors ARCore still holds for this session; debug only, so it may allocate. */
    val sessionAnchorCount: Int get() = session?.allAnchors?.size ?: 0

    private val anchors = ArrayList<Anchor>(MAX_POINTS)
    private val world = FloatArray(MAX_POINTS * 3)
    private val hitWorld = FloatArray(3)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val segmentKey = LongArray(MAX_POINTS) { Long.MIN_VALUE }
    private var liveKey = Long.MIN_VALUE
    private var totalKey = Long.MIN_VALUE
    private var session: Session? = null
    private var addRequested = false

    private var centerX = 0f
    private var centerY = 0f
    private var lastImageTimestamp = 0L
    private val openedAtNanos = System.nanoTime()

    /** Size of the camera area in pixels; the hit test runs at its exact centre. */
    fun setViewSize(width: Int, height: Int) {
        centerX = width / 2f
        centerY = height / 2f
    }

    /** Place a point at the reticle on the next new camera image (SPEC.md §2.5). */
    fun requestAdd() {
        addRequested = true
    }

    fun undo() {
        if (anchors.isEmpty()) return
        anchors.removeAt(anchors.lastIndex).detach()
        pointCount = anchors.size
    }

    fun clear() {
        for (i in anchors.indices) anchors[i].detach()
        anchors.clear()
        pointCount = 0
    }

    fun onFrame(session: Session, frame: Frame) {
        val timestamp = frame.timestamp
        if (timestamp == 0L || timestamp == lastImageTimestamp) return
        lastImageTimestamp = timestamp
        this.session = session

        // Modulo in Long/Double keeps Float precision stable however long the screen is open.
        val periodNanos = (RETICLE_PERIOD_S * 1_000_000_000L).toLong()
        spinSeconds = ((System.nanoTime() - openedAtNanos) % periodNanos / 1e9).toFloat()

        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING
        if (tracking) {
            camera.getProjectionMatrix(projMatrix, 0, NEAR_M, FAR_M)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)
        }

        var hit = if (tracking) centreSurfaceHit(frame) else null
        if (hit != null) {
            hit.hitPose.getTranslation(hitWorld, 0)
            // A hit that projects behind the camera counts as no hit (SPEC.md §2.4).
            if (!project(hitWorld[0], hitWorld[1], hitWorld[2], hitXY, 0)) hit = null
        }
        hasHit = hit != null

        if (addRequested) {
            addRequested = false
            if (hit != null && anchors.size < MAX_POINTS) {
                // A session (world-space) anchor, not a plane-attached one, so plane
                // refinement can't drag the point.
                System.arraycopy(hitWorld, 0, world, anchors.size * 3, 3)
                anchors.add(session.createAnchor(hit.hitPose))
                pointCount = anchors.size
            }
        }

        var total = 0.0
        for (i in anchors.indices) {
            val anchorTracking = anchors[i].trackingState == TrackingState.TRACKING
            // Live pose every frame so points follow ARCore's refinements. While an anchor
            // isn't tracking, its last known position is kept.
            if (anchorTracking) anchors[i].pose.getTranslation(world, i * 3)
            val o = i * 3
            visible[i] = tracking && anchorTracking && project(world[o], world[o + 1], world[o + 2], screenXY, i * 2)
            if (i > 0) {
                val d = distance(world, o - 3, world, o)
                total += d
                segmentMidInFront[i] = tracking && projectMidpoint(world, o - 3, world, o, segmentMidXY, i * 2)
                val key = DistanceFormat.displayKey(d, units)
                if (key != segmentKey[i]) {
                    segmentKey[i] = key
                    segmentTextLength[i] = DistanceFormat.formatInto(d, units, segmentText[i])
                }
            }
        }

        val last = anchors.lastIndex
        liveVisible = hit != null && last >= 0 && visible[last]
        if (liveVisible) {
            val d = distance(world, last * 3, hitWorld, 0)
            liveMidInFront = projectMidpoint(world, last * 3, hitWorld, 0, liveMidXY, 0)
            val key = DistanceFormat.displayKey(d, units)
            if (key != liveKey) {
                liveKey = key
                liveTextLength = DistanceFormat.formatInto(d, units, liveText)
            }
        }

        val key = DistanceFormat.displayKey(total, units)
        if (key != totalKey) {
            totalKey = key
            totalMetres = total
        }
    }

    /**
     * Drop references when the camera area leaves the screen. SceneView closes the session
     * (disposed before this), which releases its anchors; detaching after close would throw.
     */
    fun release() {
        anchors.clear()
        pointCount = 0
        session = null
    }

    /**
     * First qualifying hit from the screen centre, or null. ARCore's hitTest allocates its
     * result list and poses; that's inherent to the API. Index loop avoids an extra iterator.
     */
    private fun centreSurfaceHit(frame: Frame): HitResult? {
        if (centerX == 0f || centerY == 0f) return null
        val hits = frame.hitTest(centerX, centerY)
        for (i in hits.indices) {
            val hit = hits[i]
            val qualifies = when (val trackable = hit.trackable) {
                is Plane -> trackable.isPoseInPolygon(hit.hitPose)
                is DepthPoint -> true
                is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
            if (qualifies) return hit
        }
        return null
    }

    /**
     * Projects a world point into [out] at [at] (SPEC.md §2.4). Returns false, leaving [out]
     * untouched, if it's behind the camera.
     */
    private fun project(x: Float, y: Float, z: Float, out: FloatArray, at: Int): Boolean {
        val m = viewProj // column-major
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (w <= 1e-4f) return false
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[at] = cx / w * 0.5f + 0.5f
        out[at + 1] = 1f - (cy / w * 0.5f + 0.5f)
        return true
    }

    /** Projects the world midpoint of two points (not the midpoint of their projections). */
    private fun projectMidpoint(a: FloatArray, ai: Int, b: FloatArray, bi: Int, out: FloatArray, at: Int) =
        project(
            (a[ai] + b[bi]) * 0.5f,
            (a[ai + 1] + b[bi + 1]) * 0.5f,
            (a[ai + 2] + b[bi + 2]) * 0.5f,
            out,
            at,
        )

    private fun distance(a: FloatArray, ai: Int, b: FloatArray, bi: Int): Double {
        val dx = (a[ai] - b[bi]).toDouble()
        val dy = (a[ai + 1] - b[bi + 1]).toDouble()
        val dz = (a[ai + 2] - b[bi + 2]).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
