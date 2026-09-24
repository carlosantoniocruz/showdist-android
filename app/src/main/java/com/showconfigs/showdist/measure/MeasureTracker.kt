package com.showconfigs.showdist.measure

import android.opengl.Matrix
import android.util.Log
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
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.showconfigs.showdist.BuildConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Reticle period: one clockwise turn every 11 s (SPEC.md §3.1). */
const val RETICLE_PERIOD_S = 11f

/** Max points per measurement (SPEC.md §2.5). */
const val MAX_POINTS = 24

private const val NEAR_M = 0.05f
private const val FAR_M = 100f

/** A segment shorter than this horizontally can't set a projection's heading. */
private const val MIN_HEADING_M = 0.01f

/** While aligning, the reticle must be this far from the first point to turn the shape. */
private const val MIN_ALIGN_M = 0.05f

/** Hit samples per placement, and how many new images to wait for them (SPEC.md §2.4). */
private const val SAMPLES = 6
private const val MIN_SAMPLES = 3
private const val SAMPLE_WINDOW = 12

/** Warm-up: the camera must have moved this far from where tracking began (parallax). */
private const val WARM_UP_M = 0.08f

private const val TAG = "ShowdistMeasure"

/** Which kind of surface a hit came from, in order of preference (SPEC.md §2.4). */
private enum class HitKind { Plane, Depth, Point, Snap }

/** The hint line's text (SPEC.md §2.3). */
enum class Hint(val text: String) {
    Starting("Starting camera…"),
    TooDark("Too dark. Find some light"),
    SlowDown("Slow down"),
    NeedTexture("Point at a textured surface"),
    CameraUnavailable("Camera unavailable"),
    TrackingLost("Tracking lost. Move slowly"),
    Scan("Move slowly to scan surfaces"),
    Aim("Aim at a surface"),
    PlaceFirst("Tap to place a point"),
    PlaceNext("Place the next point"),
    AddOrSave("Tap to add · hold anywhere to save"),
    WarmUp("Move the phone slowly to get started"),
    ProjectAim("Tap where its first point goes"),
    ProjectAlign("Turn it to line up, then tap"),
}

/**
 * A saved measurement projected into the scene (SPEC.md §2.11). Placing it takes two taps:
 * [Aim] (the first point follows the reticle), then [Align] (the shape turns about its
 * first point to follow the reticle); after that it's [Placed].
 */
enum class ProjectionStage { None, Aim, Align, Placed }

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

    /**
     * True when a tap would place something: a surface is hit and, before the first point,
     * the session has warmed up (SPEC.md §2.4). Drives the main button.
     */
    var canPlace by mutableStateOf(false)
        private set

    /** Seconds into the current reticle turn, sampled once per new camera image. */
    var spinSeconds by mutableFloatStateOf(0f)
        private set

    var pointCount by mutableIntStateOf(0)
        private set

    /** Only changes when the hint's text would, so it recomposes rarely. */
    var hint by mutableStateOf(Hint.Starting)
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
            formatProjectionText()
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

    var projectionStage by mutableStateOf(ProjectionStage.None)
        private set

    /** Points in the projected recording; 0 when there's none. */
    var projectionCount = 0
        private set

    /** Projected points and segment labels, laid out like the user's own points above. */
    val projScreenXY = FloatArray(MAX_POINTS * 2)
    val projVisible = BooleanArray(MAX_POINTS)
    val projMidXY = FloatArray(MAX_POINTS * 2)
    val projMidInFront = BooleanArray(MAX_POINTS)
    val projText = Array(MAX_POINTS) { CharArray(DISTANCE_TEXT_CAPACITY) }
    val projTextLength = IntArray(MAX_POINTS)

    /** Offsets from the recording's first point (world axes of its own session; y is up). */
    private val projRel = FloatArray(MAX_POINTS * 3)
    private val projMetres = DoubleArray(MAX_POINTS)
    private val projWorld = FloatArray(MAX_POINTS * 3)
    private val projOrigin = FloatArray(3)
    private var projRefHeading = 0f
    private var projHasRef = false
    private var projYaw = 0f
    private var projAnchor: Anchor? = null

    private val anchors = ArrayList<Anchor>(MAX_POINTS)
    private val world = FloatArray(MAX_POINTS * 3)
    private val hitWorld = FloatArray(3)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val segmentKey = LongArray(MAX_POINTS) { Long.MIN_VALUE }
    private var liveKey = Long.MIN_VALUE
    private var totalKey = Long.MIN_VALUE
    private var addRequested = false

    // Multi-frame placement: hit positions from consecutive new images, then their median.
    private val samples = FloatArray(SAMPLES * 3)
    private val sampleKinds = IntArray(HitKind.entries.size)
    private val median = FloatArray(3)
    private val sortBuf = FloatArray(SAMPLES)
    private var sampling = false
    private var sampleCount = 0
    private var sampleImages = 0
    private var snapRequested = -1

    // Warm-up: the first tracked camera position, and whether it has moved far enough.
    private val startCam = FloatArray(3)
    private var hasStartCam = false
    private var warmedUp = false
    private var lastHitKind = HitKind.Plane

    /** A reticle within this many pixels of a point's screen position snaps to it. */
    var snapRadiusPx = 0f
    private var imageCount = 0
    private var planeCount = 0

    private var centerX = 0f
    private var centerY = 0f
    private var lastImageTimestamp = 0L
    private val openedAtNanos = System.nanoTime()

    /** Size of the camera area in pixels; the hit test runs at its exact centre. */
    fun setViewSize(width: Int, height: Int) {
        centerX = width / 2f
        centerY = height / 2f
    }

    /**
     * Place a point at the reticle (SPEC.md §2.4, §2.5): sampled over the next few new
     * camera images, or snapped to an existing point under the reticle. Ignored while a
     * placement is already sampling.
     */
    fun requestAdd() {
        if (!sampling) addRequested = true
    }

    /** Removes the user's last point; with none left, steps the projection back a stage. */
    fun undo() {
        if (anchors.isNotEmpty()) {
            anchors.removeAt(anchors.lastIndex).detach()
            pointCount = anchors.size
            return
        }
        when (projectionStage) {
            ProjectionStage.Placed -> if (projHasRef) projectionStage = ProjectionStage.Align else unplaceProjection()
            ProjectionStage.Align -> unplaceProjection()
            else -> Unit
        }
    }

    /** Removes every point, and the projection. */
    fun clear() {
        for (i in anchors.indices) anchors[i].detach()
        anchors.clear()
        pointCount = 0
        projAnchor?.detach()
        projAnchor = null
        projectionStage = ProjectionStage.None
        projectionCount = 0
    }

    /**
     * Starts projecting a saved measurement (SPEC.md §2.11), replacing any current one. Its
     * shape and heights are kept; where it sits and which way it faces are set by two taps.
     */
    fun startProjection(points: List<DoubleArray>) {
        projAnchor?.detach()
        projAnchor = null
        val n = minOf(points.size, MAX_POINTS)
        if (n < 2) return
        val first = points[0]
        projHasRef = false
        for (i in 0 until n) {
            for (a in 0..2) projRel[i * 3 + a] = (points[i][a] - first[a]).toFloat()
            if (i > 0) {
                projMetres[i] = distance(projRel, (i - 1) * 3, projRel, i * 3)
                // The first point that's horizontally clear of the first point sets the heading.
                val x = projRel[i * 3]
                val z = projRel[i * 3 + 2]
                if (!projHasRef && sqrt(x * x + z * z) > MIN_HEADING_M) {
                    projRefHeading = atan2(x, z)
                    projHasRef = true
                }
            }
        }
        projectionCount = n
        formatProjectionText()
        projectionStage = ProjectionStage.Aim
    }

    private fun unplaceProjection() {
        projAnchor?.detach()
        projAnchor = null
        projectionStage = ProjectionStage.Aim
    }

    private fun formatProjectionText() {
        for (i in 1 until projectionCount) projTextLength[i] = DistanceFormat.formatInto(projMetres[i], units, projText[i])
    }

    /**
     * Current world positions of every point, in metres, for saving a recording. Re-read
     * from the anchors so ARCore's latest corrections are included (SPEC.md §2.4).
     */
    fun snapshotPoints(): List<DoubleArray> = List(anchors.size) { i ->
        if (anchors[i].trackingState == TrackingState.TRACKING) anchors[i].pose.getTranslation(world, i * 3)
        doubleArrayOf(world[i * 3].toDouble(), world[i * 3 + 1].toDouble(), world[i * 3 + 2].toDouble())
    }

    fun onFrame(session: Session, frame: Frame) {
        val timestamp = frame.timestamp
        if (timestamp == 0L || timestamp == lastImageTimestamp) return
        lastImageTimestamp = timestamp

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

        if (tracking) updateWarmUp()

        var hit = if (tracking) centreSurfaceHit(frame) else null
        if (hit != null) {
            hit.hitPose.getTranslation(hitWorld, 0)
            // A hit that projects behind the camera counts as no hit (SPEC.md §2.4).
            if (!project(hitWorld[0], hitWorld[1], hitWorld[2], hitXY, 0)) hit = null
        }
        hasHit = hit != null
        // The warm-up only gates the first placement of a measurement or projection.
        val needsWarmUp = anchors.isEmpty() && projectionStage != ProjectionStage.Align
        canPlace = hit != null && (warmedUp || !needsWarmUp)

        if (addRequested) {
            addRequested = false
            if (hit != null && projectionStage == ProjectionStage.Align) {
                // projYaw already follows the reticle; the tap just freezes it.
                projectionStage = ProjectionStage.Placed
            } else if (canPlace && (projectionStage == ProjectionStage.Aim || anchors.size < MAX_POINTS)) {
                snapRequested = if (projectionStage == ProjectionStage.Aim) -1 else snapTarget()
                if (snapRequested >= 0) {
                    placeAt(session, anchors[snapRequested].pose, HitKind.Snap, 0f, camera.trackingState)
                } else {
                    sampling = true
                    sampleCount = 0
                    sampleImages = 0
                    sampleKinds.fill(0)
                }
            }
        }
        if (sampling) {
            // Tracking must hold for the whole placement (SPEC.md §2.4).
            if (!tracking) {
                sampling = false
            } else {
                sampleImages++
                if (hit != null && sampleCount < SAMPLES) {
                    System.arraycopy(hitWorld, 0, samples, sampleCount * 3, 3)
                    sampleKinds[lastHitKind.ordinal]++
                    sampleCount++
                }
                if (sampleCount == SAMPLES || sampleImages >= SAMPLE_WINDOW) {
                    sampling = false
                    if (sampleCount >= MIN_SAMPLES) {
                        val spread = medianOfSamples()
                        val kind = HitKind.entries[sampleKinds.indices.maxBy { sampleKinds[it] }]
                        placeAt(session, Pose.makeTranslation(median[0], median[1], median[2]), kind, spread, camera.trackingState)
                    }
                }
            }
        }

        if (projectionCount > 0) updateProjection(tracking, hit != null)

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

        // The plane count only feeds the hint, so it's recounted every 10th image (~3 Hz).
        if (imageCount++ % 10 == 0) planeCount = countPlanes(session)
        hint = hintFor(camera.trackingState, camera.trackingFailureReason, hit != null, anchors.size)
    }

    /**
     * Anchors the next point (or a projection's first point) at [pose]: a session
     * (world-space) anchor, not a plane-attached one, so plane refinement can't drag it.
     * Called on a tap, not per frame.
     */
    private fun placeAt(session: Session, pose: Pose, kind: HitKind, spreadM: Float, state: TrackingState) {
        if (projectionStage == ProjectionStage.Aim) {
            projAnchor = session.createAnchor(pose)
            projectionStage = if (projHasRef) ProjectionStage.Align else ProjectionStage.Placed
        } else if (anchors.size < MAX_POINTS) {
            pose.getTranslation(world, anchors.size * 3)
            anchors.add(session.createAnchor(pose))
            pointCount = anchors.size
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "placed: hit=$kind tracking=$state samples=$sampleCount spread=${"%.1f".format(spreadM * 1000)} mm")
        }
    }

    /**
     * Component-wise median of the samples into [median]; returns the largest distance of
     * any sample from it (the spread).
     */
    private fun medianOfSamples(): Float {
        val n = sampleCount
        for (a in 0..2) {
            for (i in 0 until n) sortBuf[i] = samples[i * 3 + a]
            sortBuf.sort(0, n)
            median[a] = if (n % 2 == 1) sortBuf[n / 2] else (sortBuf[n / 2 - 1] + sortBuf[n / 2]) * 0.5f
        }
        var spread = 0f
        for (i in 0 until n) {
            val dx = samples[i * 3] - median[0]
            val dy = samples[i * 3 + 1] - median[1]
            val dz = samples[i * 3 + 2] - median[2]
            spread = maxOf(spread, sqrt(dx * dx + dy * dy + dz * dz))
        }
        return spread
    }

    /** The index of a visible point within [snapRadiusPx] of the reticle, else −1 (nearest wins). */
    private fun snapTarget(): Int {
        if (snapRadiusPx <= 0f) return -1
        var best = -1
        var bestD = snapRadiusPx
        for (i in anchors.indices) {
            if (!visible[i] || anchors[i].trackingState != TrackingState.TRACKING) continue
            val dx = screenXY[i * 2] * centerX * 2f - centerX
            val dy = screenXY[i * 2 + 1] * centerY * 2f - centerY
            val d = sqrt(dx * dx + dy * dy)
            if (d <= bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    /**
     * Warm-up (SPEC.md §2.4): done once the camera has moved [WARM_UP_M] from where tracking
     * began, giving ARCore some parallax, and at least one plane is found. The camera's
     * position comes from the view matrix (−Rᵀt), so nothing is allocated.
     */
    private fun updateWarmUp() {
        if (warmedUp) return
        val m = viewMatrix
        val x = -(m[0] * m[12] + m[1] * m[13] + m[2] * m[14])
        val y = -(m[4] * m[12] + m[5] * m[13] + m[6] * m[14])
        val z = -(m[8] * m[12] + m[9] * m[13] + m[10] * m[14])
        if (!hasStartCam) {
            startCam[0] = x
            startCam[1] = y
            startCam[2] = z
            hasStartCam = true
            return
        }
        val dx = x - startCam[0]
        val dy = y - startCam[1]
        val dz = z - startCam[2]
        if (planeCount > 0 && sqrt(dx * dx + dy * dy + dz * dz) >= WARM_UP_M) warmedUp = true
    }

    /**
     * Positions the projected points for this frame: its first point at the reticle hit
     * while aiming, else at its anchor, turned about the vertical by [projYaw].
     */
    private fun updateProjection(tracking: Boolean, hasHit: Boolean) {
        val stage = projectionStage
        var shown = tracking
        if (stage == ProjectionStage.Aim) {
            shown = shown && hasHit
            if (shown) {
                System.arraycopy(hitWorld, 0, projOrigin, 0, 3)
                // Point the shape away from the camera: its forward is the view matrix's −z row.
                val fx = -viewMatrix[2]
                val fz = -viewMatrix[10]
                projYaw = atan2(fx, fz) - projRefHeading
            }
        } else {
            val anchor = projAnchor
            val anchorTracking = anchor != null && anchor.trackingState == TrackingState.TRACKING
            if (anchorTracking) anchor.pose.getTranslation(projOrigin, 0)
            shown = shown && anchorTracking
            if (stage == ProjectionStage.Align && hasHit) {
                val dx = hitWorld[0] - projOrigin[0]
                val dz = hitWorld[2] - projOrigin[2]
                if (sqrt(dx * dx + dz * dz) > MIN_ALIGN_M) projYaw = atan2(dx, dz) - projRefHeading
            }
        }

        // Rotation about +y by projYaw: adds projYaw to each offset's atan2(x, z) heading.
        val c = cos(projYaw)
        val sn = sin(projYaw)
        for (i in 0 until projectionCount) {
            val o = i * 3
            val rx = projRel[o]
            val rz = projRel[o + 2]
            projWorld[o] = projOrigin[0] + rx * c + rz * sn
            projWorld[o + 1] = projOrigin[1] + projRel[o + 1]
            projWorld[o + 2] = projOrigin[2] - rx * sn + rz * c
            projVisible[i] = shown && project(projWorld[o], projWorld[o + 1], projWorld[o + 2], projScreenXY, i * 2)
            if (i > 0) projMidInFront[i] = shown && projectMidpoint(projWorld, o - 3, projWorld, o, projMidXY, i * 2)
        }
    }

    /** SPEC.md §2.3, in table order. */
    private fun hintFor(state: TrackingState, reason: TrackingFailureReason, hasHit: Boolean, points: Int): Hint =
        when {
            state == TrackingState.STOPPED -> Hint.Starting
            state != TrackingState.TRACKING -> when (reason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> Hint.TooDark
                TrackingFailureReason.EXCESSIVE_MOTION -> Hint.SlowDown
                TrackingFailureReason.INSUFFICIENT_FEATURES -> Hint.NeedTexture
                TrackingFailureReason.CAMERA_UNAVAILABLE -> Hint.CameraUnavailable
                else -> Hint.TrackingLost
            }
            planeCount == 0 && !hasHit -> Hint.Scan
            !warmedUp && points == 0 && projectionStage != ProjectionStage.Align &&
                projectionStage != ProjectionStage.Placed -> if (planeCount == 0) Hint.Scan else Hint.WarmUp
            hasHit && projectionStage == ProjectionStage.Aim -> Hint.ProjectAim
            hasHit && projectionStage == ProjectionStage.Align -> Hint.ProjectAlign
            !hasHit && (points < 2 || projectionStage == ProjectionStage.Aim || projectionStage == ProjectionStage.Align) -> Hint.Aim
            points == 0 -> Hint.PlaceFirst
            points == 1 -> Hint.PlaceNext
            else -> Hint.AddOrSave
        }

    /** Planes that are tracking and not merged into another plane. */
    private fun countPlanes(session: Session): Int {
        var n = 0
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState == TrackingState.TRACKING && plane.subsumedBy == null) n++
        }
        return n
    }

    /**
     * Drop references when the camera area leaves the screen. SceneView closes the session
     * (disposed before this), which releases its anchors; detaching after close would throw.
     */
    fun release() {
        anchors.clear()
        pointCount = 0
        sampling = false
        projAnchor = null
        projectionStage = ProjectionStage.None
        projectionCount = 0
    }

    /**
     * The best hit from the screen centre, or null (SPEC.md §2.4): the nearest plane hit
     * inside its polygon, else the nearest depth hit, else the nearest feature point with a
     * surface normal. Sets [lastHitKind]. ARCore's hitTest allocates its result list and
     * poses; that's inherent to the API. Index loop avoids an extra iterator.
     */
    private fun centreSurfaceHit(frame: Frame): HitResult? {
        if (centerX == 0f || centerY == 0f) return null
        val hits = frame.hitTest(centerX, centerY)
        var depth: HitResult? = null
        var point: HitResult? = null
        for (i in hits.indices) {
            val hit = hits[i]
            when (val trackable = hit.trackable) {
                is Plane -> if (trackable.isPoseInPolygon(hit.hitPose)) {
                    lastHitKind = HitKind.Plane
                    return hit
                }
                is DepthPoint -> if (depth == null) depth = hit
                is Point -> if (point == null && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) point = hit
            }
        }
        if (depth != null) {
            lastHitKind = HitKind.Depth
            return depth
        }
        lastHitKind = HitKind.Point
        return point
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
