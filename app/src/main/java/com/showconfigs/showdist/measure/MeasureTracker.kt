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
 * Per-point data lives in preallocated arrays. The overlay reads the snapshot state below
 * only inside its draw lambda, so new frames trigger a redraw, never a recomposition.
 */
class MeasureTracker {
    /** True when the screen-centre hit test found a usable surface. */
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

    /** Units used to decide when [totalMetres] needs updating. Fixed until the unit toggle exists. */
    var units = UnitSystem.Imperial

    /** Normalised (0..1, top-left origin) screen position of each point: x at 2i, y at 2i+1. */
    val screenXY = FloatArray(MAX_POINTS * 2)

    /** Whether each point is drawn this frame (SPEC.md §2.4 "anchor visibility"). */
    val visible = BooleanArray(MAX_POINTS)

    /** Anchors ARCore still holds for this session; debug only, so it may allocate. */
    val sessionAnchorCount: Int get() = session?.allAnchors?.size ?: 0

    private val anchors = ArrayList<Anchor>(MAX_POINTS)
    private val world = FloatArray(MAX_POINTS * 3)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private var session: Session? = null
    private var addRequested = false
    private var totalKey = Long.MIN_VALUE

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
        val hit = if (tracking) centreSurfaceHit(frame) else null
        hasHit = hit != null

        if (addRequested) {
            addRequested = false
            if (hit != null && anchors.size < MAX_POINTS) {
                // A session (world-space) anchor, not a plane-attached one, so plane
                // refinement can't drag the point.
                val pose = hit.hitPose
                pose.getTranslation(world, anchors.size * 3)
                anchors.add(session.createAnchor(pose))
                pointCount = anchors.size
            }
        }

        if (tracking) {
            camera.getProjectionMatrix(projMatrix, 0, NEAR_M, FAR_M)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)
        }

        var total = 0.0
        for (i in anchors.indices) {
            val anchorTracking = anchors[i].trackingState == TrackingState.TRACKING
            // Live pose every frame so points follow ARCore's refinements. While an anchor
            // isn't tracking, its last known position is kept.
            if (anchorTracking) anchors[i].pose.getTranslation(world, i * 3)
            visible[i] = tracking && anchorTracking && project(i)
            if (i > 0) total += distance(i - 1, i)
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
     * A hit from the screen centre is always in front of the camera, so the §2.4
     * behind-camera check can't fail here.
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

    /** Projects point [i] into [screenXY]; false if it's behind the camera (SPEC.md §2.4). */
    private fun project(i: Int): Boolean {
        val x = world[i * 3]
        val y = world[i * 3 + 1]
        val z = world[i * 3 + 2]
        val m = viewProj // column-major
        val w = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (w <= 1e-4f) return false
        val cx = m[0] * x + m[4] * y + m[8] * z + m[12]
        val cy = m[1] * x + m[5] * y + m[9] * z + m[13]
        screenXY[i * 2] = cx / w * 0.5f + 0.5f
        screenXY[i * 2 + 1] = 1f - (cy / w * 0.5f + 0.5f)
        return true
    }

    private fun distance(a: Int, b: Int): Double {
        val dx = (world[a * 3] - world[b * 3]).toDouble()
        val dy = (world[a * 3 + 1] - world[b * 3 + 1]).toDouble()
        val dz = (world[a * 3 + 2] - world[b * 3 + 2]).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
