package com.example.photoanchor

import android.content.Context
import android.os.Build
import org.json.JSONObject

/** Unauthenticated observations sampled at capture completion, not attestations. */
object CaptureReport {
    fun create(context: Context, result: PhotoProcessor.Result, receivedAtMillis: Long,
               lens: String, flashMode: Int): String = JSONObject().apply {
        put("format", "fingerprinto-capture-diagnostic/v1")
        put("asset", "original.jpg")
        put("sha256", result.sha256)
        put("size_bytes", result.sizeBytes)
        put("received_at_unix_ms", receivedAtMillis)
        put("device_time_trusted", false)
        put("manufacturer", Build.MANUFACTURER)
        put("model", Build.MODEL)
        put("os_build", Build.FINGERPRINT)
        put("sdk_int", Build.VERSION.SDK_INT)
        put("package_name", context.packageName)
        put("app_version_code", BuildConfig.VERSION_CODE)
        put("app_version_name", BuildConfig.VERSION_NAME)
        put("capture_route", "CameraX ImageCapture.OutputFileOptions")
        put("camera_library_version", "1.4.1")
        put("lens_selection", lens)
        put("flash_mode", flashMode)
        put("origin", "indeterminate")
        put("reason", "credential_not_evaluated")
        put("detached_credential_collected", false)
    }.toString(2)
}
