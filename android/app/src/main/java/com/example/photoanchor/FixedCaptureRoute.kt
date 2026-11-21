package com.example.photoanchor

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Observable capture route, not cryptographic proof of a physical sensor. */
internal data class FixedCaptureRoute(val logical: String, val physical: String?) {
    val cameraId: String get() = physical ?: logical
    companion object {
        fun select(context: Context, back: Boolean): FixedCaptureRoute {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val facing = if (back) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            val logical = manager.cameraIdList.sorted().first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
            val physical = manager.getCameraCharacteristics(logical).physicalCameraIds.sorted().firstOrNull()
            return FixedCaptureRoute(logical, physical)
        }
    }
}
