package com.showconfigs.showdist.measure

import android.util.Log
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Session
import java.util.EnumSet

/** Shortest side the camera texture should keep, so the feed stays sharp on screen. */
private const val MIN_TEXTURE_SHORT_SIDE = 720

/**
 * A modest camera configuration for low-power phones (SPEC.md §2.1): back camera, a steady
 * 30 fps, no depth sensor, and the smallest GPU texture that's at least 720p (else the
 * largest available), then the smallest CPU image. Falls back to the session's current one.
 */
fun modestCameraConfig(session: Session): CameraConfig = runCatching {
    val filter = CameraConfigFilter(session)
        .setFacingDirection(CameraConfig.FacingDirection.BACK)
        .setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
        .setDepthSensorUsage(EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE))
    val configs = session.getSupportedCameraConfigs(filter)
    val area = { c: CameraConfig -> c.textureSize.width.toLong() * c.textureSize.height }
    val cpuArea = { c: CameraConfig -> c.imageSize.width.toLong() * c.imageSize.height }
    val pick = configs
        .filter { minOf(it.textureSize.width, it.textureSize.height) >= MIN_TEXTURE_SHORT_SIDE }
        .minWithOrNull(compareBy(area).thenBy(cpuArea))
        ?: configs.maxWithOrNull(compareBy(area).thenByDescending(cpuArea))
    pick?.let {
        Log.i("ShowdistMeasure", "camera config: texture ${it.textureSize} cpu ${it.imageSize} fps ${it.fpsRange} of ${configs.size}")
    }
    pick
}.getOrNull() ?: session.cameraConfig
