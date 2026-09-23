package com.showconfigs.showdist.measure

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.TrackingState

/** Reticle period: one clockwise turn every 11 s (SPEC.md §3.1). */
const val RETICLE_PERIOD_S = 11f

/**
 * Per-frame reticle state (SPEC.md §2.4, §3.1). [onFrame] is called on every render frame
 * but only does work when ARCore delivers a new camera image.
 *
 * Both fields are snapshot state read only inside the reticle's draw lambda, so a change
 * triggers a redraw of the overlay, never a recomposition.
 */
class ReticleTracker {
    /** True when the screen-centre hit test found a usable surface. */
    var hasHit by mutableStateOf(false)
        private set

    /** Seconds into the current reticle turn, sampled once per new camera image. */
    var spinSeconds by mutableFloatStateOf(0f)
        private set

    private var centerX = 0f
    private var centerY = 0f
    private var lastImageTimestamp = 0L
    private val openedAtNanos = System.nanoTime()

    /** Size of the camera area in pixels; the hit test runs at its exact centre. */
    fun setViewSize(width: Int, height: Int) {
        centerX = width / 2f
        centerY = height / 2f
    }

    fun onFrame(frame: Frame) {
        val timestamp = frame.timestamp
        if (timestamp == 0L || timestamp == lastImageTimestamp) return
        lastImageTimestamp = timestamp

        // Modulo in Long/Double keeps Float precision stable however long the screen is open.
        val periodNanos = (RETICLE_PERIOD_S * 1_000_000_000L).toLong()
        spinSeconds = ((System.nanoTime() - openedAtNanos) % periodNanos / 1e9).toFloat()

        hasHit = frame.camera.trackingState == TrackingState.TRACKING && centreHitsSurface(frame)
    }

    /**
     * First qualifying hit from the screen centre. ARCore's hitTest allocates its result list
     * and poses; that's inherent to the API. Index loop avoids an extra iterator.
     * A hit from the screen centre is always in front of the camera, so the §2.4
     * behind-camera check can't fail here.
     */
    private fun centreHitsSurface(frame: Frame): Boolean {
        if (centerX == 0f || centerY == 0f) return false
        val hits = frame.hitTest(centerX, centerY)
        for (i in hits.indices) {
            val hit = hits[i]
            val qualifies = when (val trackable = hit.trackable) {
                is Plane -> trackable.isPoseInPolygon(hit.hitPose)
                is DepthPoint -> true
                is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
            if (qualifies) return true
        }
        return false
    }
}
