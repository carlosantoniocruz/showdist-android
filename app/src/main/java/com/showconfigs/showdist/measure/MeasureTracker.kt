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

/**
 * Reticle-hit smoothing (SPEC.md §2.4): each new hit moves the displayed hit this fraction
 * of the way (an exponential moving average, ~1.5 images of lag at 30 fps). A jump larger
 * than [SMOOTH_RESET_M], e.g. onto a different surface, is taken at once.
 */
private const val SMOOTH_ALPHA = 0.5f
private const val SMOOTH_RESET_M = 0.25f

/** Camera images to wait for before uncovering the feed; the first may not be drawn yet. */
private const val LIVE_AFTER_IMAGES = 2

/** Which kind of surface a hit came from, in order of preference (SPEC.md §2.4). */
private enum class HitKind { Plane, Point, Snap }

/**
 * The hint line's text (SPEC.md §2.3). Hints about tracking or aiming ([tracking] true) are
 * left to the status pill ([TrackingStatus]), so the hint line shows only what to do next.
 */
enum class Hint(val text: String, val tracking: Boolean = false) {
    Starting("Starting camera…", tracking = true),
    TooDark("Too dark. Find some light", tracking = true),
    SlowDown("Slow down", tracking = true),
    NeedTexture("Point at a textured surface", tracking = true),
    CameraUnavailable("Camera unavailable", tracking = true),
    TrackingLost("Tracking lost. Move slowly", tracking = true),
    Scan("Move slowly to scan surfaces", tracking = true),
    Aim("Aim at a surface", tracking = true),
    PlaceFirst("Tap to place a point"),
    PlaceNext("Place the next point"),
    AddOrSave("Tap to add · hold anywhere to save"),
    WarmUp("Move the phone slowly to get started", tracking = true),
    ProjectAim("Tap where its first point goes"),
    ProjectAlign("Turn it to line up, then tap"),
}

/**
 * The tracking status pill's state (SPEC.md §2.3a). Everything but [Ready] means a tap
 * can't place a point yet, and says why.
 */
enum class TrackingStatus(val text: String) {
    MoveSlowly("Move phone slowly"),
    Scanning("Scanning surface"),
    AimAtSurface("Aim at a surface"),
    NeedDetail("Aim at more detail"),
    TooDark("Too dark"),
    Lost("Tracking lost"),
    CameraUnavailable("Camera unavailable"),
    Ready("Ready"),
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

    /**
     * Bumped on each new camera image while the overlay has marks to move (points, a
     * projection, or ones just removed), so the overlay redraws then and only then.
     */
    var drawSerial by mutableIntStateOf(0)
        private set
    private var hadMarks = false

    /** Seconds into the current reticle turn, sampled once per new camera image. */
    var spinSeconds by mutableFloatStateOf(0f)
        private set

    var pointCount by mutableIntStateOf(0)
        private set

    /**
     * False until ARCore has delivered [LIVE_AFTER_IMAGES] camera images. Before that the
     * camera texture holds uninitialised memory, which slow devices show as static.
     */
    var cameraLive by mutableStateOf(false)
        private set
    private var imagesSeen = 0

    /** Only changes when the status does, so it recomposes rarely. */
    var status by mutableStateOf(TrackingStatus.MoveSlowly)
        private set
    private var everTracked = false

    /** Whether the camera is TRACKING; gates saving (SPEC.md §2.6). Changes rarely. */
    var tracking by mutableStateOf(false)
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

    /**
     * Measurement calibration (SPEC.md §1.5a): every displayed length is the anchors' distance
     * times this. 1 while calibrating, so the raw reading is what's compared.
     */
    var scale = 1.0
        set(value) {
            if (field == value) return
            field = value
            totalKey = Long.MIN_VALUE
            segmentKey.fill(Long.MIN_VALUE)
            liveKey = Long.MIN_VALUE
        }

    /**
     * How settled tracking is, 0..1 (SPEC.md §1.5a warm-up): camera travel while tracking,
     * planes found and seconds of steady tracking. Read only while drawing the warm-up ring.
     */
    var warmQuality by mutableFloatStateOf(0f)
        private set
    private var warmTravel = 0f
    private var warmSeconds = 0f
    private val lastCam = FloatArray(3)
    private var hasLastCam = false
    private var lastQualityTimestamp = 0L

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
    /** The raw hit of this image (used for placement samples). */
    private val hitWorld = FloatArray(3)

    /** The smoothed hit, used for everything drawn: live line, live label, projection aim. */
    private val smoothHit = FloatArray(3)
    private var hasSmoothHit = false

    /** The camera's world position this image, from the view matrix (−Rᵀt). */
    private val camPos = FloatArray(3)
    private val normal = FloatArray(3)

    // Logged only when they change (SPEC.md §2.4 verification).
    private var loggedState: TrackingState? = null
    private var loggedTrackingAnchors = -1
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
        // A placement still sampling is what the user means to undo.
        if (sampling || addRequested) {
            cancelPlacement()
            return
        }
        if (anchors.isNotEmpty()) {
            anchors.removeAt(anchors.lastIndex).detach()
            pointCount = anchors.size
            logAnchors("undo")
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
        // Otherwise a placement still sampling would add a point right after the clear.
        cancelPlacement()
        for (i in anchors.indices) anchors[i].detach()
        anchors.clear()
        pointCount = 0
        projAnchor?.detach()
        projAnchor = null
        projectionStage = ProjectionStage.None
        projectionCount = 0
        logAnchors("clear")
    }

    private fun cancelPlacement() {
        addRequested = false
        sampling = false
    }

    private fun logAnchors(why: String) {
        Log.i(TAG, "anchors: ${anchors.size} ($why)" + if (projAnchor != null) " +projection" else "")
    }

    /**
     * Starts projecting a saved measurement (SPEC.md §2.11), replacing any current one. Its
     * shape and heights are kept; where it sits and which way it faces are set by two taps.
     */
    fun startProjection(points: List<DoubleArray>, recordingScale: Double) {
        projScale = recordingScale
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

    private var projScale = 1.0

    private fun formatProjectionText() {
        for (i in 1 until projectionCount) projTextLength[i] = DistanceFormat.formatInto(projMetres[i] * projScale, units, projText[i])
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
        if (!cameraLive && ++imagesSeen >= LIVE_AFTER_IMAGES) cameraLive = true

        // Modulo in Long/Double keeps Float precision stable however long the screen is open.
        val periodNanos = (RETICLE_PERIOD_S * 1_000_000_000L).toLong()
        spinSeconds = ((System.nanoTime() - openedAtNanos) % periodNanos / 1e9).toFloat()

        val camera = frame.camera
        val state = camera.trackingState
        val tracking = state == TrackingState.TRACKING
        if (tracking) {
            camera.getProjectionMatrix(projMatrix, 0, NEAR_M, FAR_M)
            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(viewProj, 0, projMatrix, 0, viewMatrix, 0)
        }

        if (state != loggedState) {
            loggedState = state
            Log.i(TAG, "tracking: $state" + if (state != TrackingState.TRACKING) " (${camera.trackingFailureReason})" else "")
        }

        if (tracking) {
            cameraPosition()
            updateWarmUp()
        }
        updateWarmQuality(tracking, timestamp)

        var hit = if (tracking) centreSurfaceHit(frame) else null
        if (hit != null) {
            hit.hitPose.getTranslation(hitWorld, 0)
            smoothHitTowardRaw()
            // A hit that projects behind the camera counts as no hit (SPEC.md §2.4).
            if (!project(smoothHit[0], smoothHit[1], smoothHit[2], hitXY, 0)) hit = null
        }
        if (hit == null) hasSmoothHit = false
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
                    placeAt(session, anchors[snapRequested].pose, HitKind.Snap, 0f, state)
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
                        placeAt(session, Pose.makeTranslation(median[0], median[1], median[2]), kind, spread, state)
                    }
                }
            }
        }

        if (projectionCount > 0) updateProjection(tracking, hit != null)

        var total = 0.0
        var trackingAnchors = 0
        for (i in anchors.indices) {
            val anchorTracking = anchors[i].trackingState == TrackingState.TRACKING
            if (anchorTracking) trackingAnchors++
            // Live pose every frame so points follow ARCore's refinements. While an anchor
            // isn't tracking, its last known position is kept.
            if (anchorTracking) anchors[i].pose.getTranslation(world, i * 3)
            val o = i * 3
            visible[i] = tracking && anchorTracking && project(world[o], world[o + 1], world[o + 2], screenXY, i * 2)
            if (i > 0) {
                val d = distance(world, o - 3, world, o) * scale
                total += d
                segmentMidInFront[i] = tracking && projectMidpoint(world, o - 3, world, o, segmentMidXY, i * 2)
                val key = DistanceFormat.displayKey(d, units)
                if (key != segmentKey[i]) {
                    segmentKey[i] = key
                    segmentTextLength[i] = DistanceFormat.formatInto(d, units, segmentText[i])
                }
            }
        }

        if (trackingAnchors != loggedTrackingAnchors) {
            loggedTrackingAnchors = trackingAnchors
            Log.i(TAG, "anchors tracking: $trackingAnchors of ${anchors.size}")
        }

        val last = anchors.lastIndex
        liveVisible = hit != null && last >= 0 && visible[last]
        if (liveVisible) {
            val d = distance(world, last * 3, smoothHit, 0) * scale
            liveMidInFront = projectMidpoint(world, last * 3, smoothHit, 0, liveMidXY, 0)
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
        val marks = anchors.isNotEmpty() || projectionCount > 0
        if (marks || hadMarks) drawSerial++
        hadMarks = marks

        val reason = camera.trackingFailureReason
        hint = hintFor(state, reason, hit != null, anchors.size)
        if (tracking) everTracked = true
        this.tracking = tracking
        status = statusFor(state, reason, hit != null, needsWarmUp)
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
        logAnchors("placed")
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

    /** The camera's world position from the view matrix (−Rᵀt), so nothing is allocated. */
    private fun cameraPosition() {
        val m = viewMatrix
        camPos[0] = -(m[0] * m[12] + m[1] * m[13] + m[2] * m[14])
        camPos[1] = -(m[4] * m[12] + m[5] * m[13] + m[6] * m[14])
        camPos[2] = -(m[8] * m[12] + m[9] * m[13] + m[10] * m[14])
    }

    /**
     * The warm-up ring's fill (SPEC.md §1.5a): 45% from camera travel while tracking (full at
     * 60 cm, each image's step capped at 5 cm so a jump can't cheat it), 35% from planes found
     * (full at 2), 20% from steady tracking (full at 4 s). Losing tracking halves the travel
     * and restarts the steady time.
     */
    private fun updateWarmQuality(tracking: Boolean, timestamp: Long) {
        val dt = if (lastQualityTimestamp == 0L) 0f else ((timestamp - lastQualityTimestamp) / 1e9f).coerceIn(0f, 0.2f)
        lastQualityTimestamp = timestamp
        if (!tracking) {
            if (hasLastCam) warmTravel *= 0.5f
            hasLastCam = false
            warmSeconds = 0f
        } else {
            if (hasLastCam) {
                val dx = camPos[0] - lastCam[0]
                val dy = camPos[1] - lastCam[1]
                val dz = camPos[2] - lastCam[2]
                warmTravel += sqrt(dx * dx + dy * dy + dz * dz).coerceAtMost(0.05f)
            }
            System.arraycopy(camPos, 0, lastCam, 0, 3)
            hasLastCam = true
            warmSeconds += dt
        }
        val q = if (!tracking) 0f else
            0.45f * (warmTravel / 0.6f).coerceAtMost(1f) +
                0.35f * (planeCount / 2f).coerceAtMost(1f) +
                0.20f * (warmSeconds / 4f).coerceAtMost(1f)
        // Rounded to whole percent, so the state only changes when the ring would move.
        val rounded = (q * 100f).toInt() / 100f
        if (rounded != warmQuality) warmQuality = rounded
    }

    /** Straight-line distance between points [a] and [b], uncorrected, from their live anchors. */
    fun rawDistance(a: Int, b: Int): Double {
        if (a >= anchors.size || b >= anchors.size) return 0.0
        for (i in intArrayOf(a, b)) {
            if (anchors[i].trackingState == TrackingState.TRACKING) anchors[i].pose.getTranslation(world, i * 3)
        }
        return distance(world, a * 3, world, b * 3)
    }

    /** Moves [smoothHit] toward this image's raw hit (SPEC.md §2.4); see [SMOOTH_ALPHA]. */
    private fun smoothHitTowardRaw() {
        if (hasSmoothHit) {
            val dx = hitWorld[0] - smoothHit[0]
            val dy = hitWorld[1] - smoothHit[1]
            val dz = hitWorld[2] - smoothHit[2]
            if (dx * dx + dy * dy + dz * dz <= SMOOTH_RESET_M * SMOOTH_RESET_M) {
                smoothHit[0] += SMOOTH_ALPHA * dx
                smoothHit[1] += SMOOTH_ALPHA * dy
                smoothHit[2] += SMOOTH_ALPHA * dz
                return
            }
        }
        System.arraycopy(hitWorld, 0, smoothHit, 0, 3)
        hasSmoothHit = true
    }

    /**
     * Warm-up (SPEC.md §2.4): done once the camera has moved [WARM_UP_M] from where tracking
     * began, giving ARCore some parallax, and at least one plane is found.
     */
    private fun updateWarmUp() {
        if (warmedUp) return
        val x = camPos[0]
        val y = camPos[1]
        val z = camPos[2]
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
                System.arraycopy(smoothHit, 0, projOrigin, 0, 3)
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
                val dx = smoothHit[0] - projOrigin[0]
                val dz = smoothHit[2] - projOrigin[2]
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

    /** SPEC.md §2.3a, in table order. */
    private fun statusFor(state: TrackingState, reason: TrackingFailureReason, hasHit: Boolean, needsWarmUp: Boolean) =
        when {
            state != TrackingState.TRACKING -> when (reason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingStatus.TooDark
                TrackingFailureReason.EXCESSIVE_MOTION -> TrackingStatus.MoveSlowly
                TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingStatus.NeedDetail
                TrackingFailureReason.CAMERA_UNAVAILABLE -> TrackingStatus.CameraUnavailable
                else -> if (everTracked) TrackingStatus.Lost else TrackingStatus.MoveSlowly
            }
            canPlace -> TrackingStatus.Ready
            planeCount == 0 && !hasHit -> TrackingStatus.Scanning
            needsWarmUp && !warmedUp -> if (planeCount == 0) TrackingStatus.Scanning else TrackingStatus.MoveSlowly
            else -> TrackingStatus.AimAtSurface
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
     * inside its polygon and seen from its front, else the nearest feature point with an
     * estimated surface normal. Sets [lastHitKind]. ARCore's hitTest allocates its result
     * list and poses; that's inherent to the API, and it runs once per camera image.
     */
    private fun centreSurfaceHit(frame: Frame): HitResult? {
        if (centerX == 0f || centerY == 0f) return null
        val hits = frame.hitTest(centerX, centerY)
        var point: HitResult? = null
        for (i in hits.indices) {
            val hit = hits[i]
            when (val trackable = hit.trackable) {
                is Plane -> if (hit.hitPose.let { trackable.isPoseInPolygon(it) && seenFromFront(it) }) {
                    lastHitKind = HitKind.Plane
                    return hit
                }
                is Point -> if (point == null && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) point = hit
            }
        }
        lastHitKind = HitKind.Point
        return point
    }

    /** True if the camera is on the side a plane hit's normal (its pose's +y) points to. */
    private fun seenFromFront(pose: Pose): Boolean {
        pose.getTransformedAxis(1, 1f, normal, 0)
        return (camPos[0] - pose.tx()) * normal[0] +
            (camPos[1] - pose.ty()) * normal[1] +
            (camPos[2] - pose.tz()) * normal[2] > 0f
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
