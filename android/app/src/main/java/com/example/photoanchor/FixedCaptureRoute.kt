package com.example.photoanchor

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/** Observable capture route, not cryptographic proof of a physical sensor. */
internal data class FixedCaptureRoute(val logical: String, val physical: String?) {
    val cameraId: String get() = physical ?: logical
    companion object {
        fun requireCompatible(route: org.json.JSONObject, geometry: org.json.JSONObject,
                              width: Int, height: Int, orientation: Int) {
            require(route.getBoolean("fixed")) { "PRNU requires a fixed capture route" }
            require(route.getString("camera") == geometry.getString("camera") &&
                route.getString("mode") == geometry.getString("mode")) { "Calibration camera or settings changed" }
            require(width == geometry.getInt("source_width") && height == geometry.getInt("source_height")) {
                "Calibration source geometry changed"
            }
            require(orientation in 1..8 && geometry.getString("decoder") ==
                "pillow-13.0.0/rgb-green-stored-exif-$orientation/1") { "Calibration orientation changed" }
        }

        fun select(context: Context, back: Boolean): FixedCaptureRoute {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val facing = if (back) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
            val logical = manager.cameraIdList.sorted().first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing }
            val physical = manager.getCameraCharacteristics(logical).physicalCameraIds.sorted().firstOrNull()
            return FixedCaptureRoute(logical, physical)
        }
    }
}
