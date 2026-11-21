package com.example.photoanchor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PhotoAnchorState(
    val ready: Boolean = false, val name: String = "", val pubkey: String = "",
    val busy: Boolean = false, val message: String = "Opening local identity…",
    val feed: List<FeedEntry> = emptyList(), val queued: Int = 0,
    val captured: File? = null, val preview: Bitmap? = null,
    val selected: PhotoPost? = null, val verification: Verification? = null,
    val copyResult: String? = null,
    val deviceStatus: String = "Device enrollment not checked", val calibrationStatus: String = "No calibration session",
    val calibrationGuide: CalibrationGuide? = null,
    val privacy: String = "", val draftCaption: String = "", val originalCapture: File? = null
)

class PhotoAnchorModel(application: Application) : AndroidViewModel(application) {
    private val repository = SocialRepository(application)
    private val mutable = MutableStateFlow(PhotoAnchorState())
    val state = mutable.asStateFlow()
    private var refreshing = false
    private val draftLock = Any()
    private fun persistCapture() = synchronized(draftLock) {
        val file = File(getApplication<Application>().noBackupFilesDir, "active-capture.json")
        val value = mutable.value
        if (value.captured == null) file.delete() else atomicWrite(file, org.json.JSONObject()
            .put("path", value.captured.absolutePath).put("original", value.originalCapture?.absolutePath)
            .put("caption", value.draftCaption).toString())
    }
    fun setCaption(value: String) {
        if (value.length > 1000 || mutable.value.busy) return
        mutable.value = mutable.value.copy(draftCaption = value)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }

    init {
        viewModelScope.launch {
            try {
                val (pubkey, name) = repository.initialize()
                val cached = repository.cachedFeed()
                mutable.value = mutable.value.copy(ready = true, pubkey = pubkey, name = name,
                    feed = cached, queued = repository.pendingCount(), deviceStatus = repository.deviceStatus(), message = "Local regtest demo · USB forwarding required")
                val draft = File(application.noBackupFilesDir, "active-capture.json")
                if (draft.exists()) runCatching {
                    val saved = org.json.JSONObject(draft.readText())
                    val file = File(saved.getString("path"))
                    require(file.canonicalPath.startsWith(application.filesDir.canonicalPath + "/"))
                    val (prepared, bitmap) = repository.preparePhoto(file)
                    mutable.value = mutable.value.copy(captured = prepared, preview = bitmap, privacy = repository.privacy(prepared),
                        draftCaption = saved.optString("caption"), originalCapture = saved.optString("original").takeIf { it.isNotEmpty() }?.let { File(it) }
                            ?.takeIf { it.canonicalPath.startsWith(application.filesDir.canonicalPath + "/") } ?: file)
                }
                while (isActive) { refreshNow(); delay(5000) }
            } catch (_: Exception) {
                mutable.value = mutable.value.copy(message = "Local identity unavailable. Existing keys were preserved.")
            }
        }
    }

    private suspend fun refreshNow() {
        if (refreshing) return
        refreshing = true
        try {
            val feed = repository.refresh()
            mutable.value = mutable.value.copy(feed = feed, queued = repository.pendingCount())
            mutable.value.selected?.let { selected ->
                val verification = repository.verify(selected)
                if (mutable.value.selected?.id == selected.id) mutable.value = mutable.value.copy(verification = verification)
            }
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(queued = repository.pendingCount(), message = "Local services unavailable · saved feed and queue preserved")
        } finally { refreshing = false }
        val device = runCatching { repository.refreshDevice() }.getOrDefault("UNAVAILABLE — current device enrollment could not be checked")
        val calibration = runCatching { repository.calibrationStatus() }.getOrDefault("UNAVAILABLE — private calibration status")
        mutable.value = mutable.value.copy(deviceStatus = device, calibrationStatus = calibration,
            calibrationGuide = repository.currentCalibrationGuide())
    }

    fun refresh() { viewModelScope.launch { refreshNow() } }

    fun saveProfile(name: String) = action("Profile saved locally; signed metadata queued for the local relay") {
        repository.saveProfile(name)
        mutable.value = mutable.value.copy(name = name.trim(), queued = repository.pendingCount())
    }

    fun captured(file: File) = action("JPEG finalized. Add a caption and publish when ready.") {
        val (prepared, bitmap) = repository.preparePhoto(file)
        mutable.value = mutable.value.copy(captured = prepared, preview = bitmap, privacy = repository.privacy(prepared), originalCapture = prepared, draftCaption = "")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }

    fun discard() {
        val file = mutable.value.captured
        mutable.value = mutable.value.copy(captured = null, preview = null, originalCapture = null, draftCaption = "")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { persistCapture(); file?.delete() }
    }

    fun publish(caption: String) = publishMode(caption, false)

    fun publishEvidence(caption: String) = publishMode(caption, true)

    private fun publishMode(caption: String, evidence: Boolean) = action("Signed post saved. Publication and timestamp progress independently.") {
        var photo = mutable.value.captured ?: return@action
        if (!evidence && !photo.name.startsWith("cleaned-")) {
            photo = repository.cleanedCopy(photo)
            val (_, preview) = repository.preparePhoto(photo)
            mutable.value = mutable.value.copy(captured = photo, preview = preview, privacy = repository.privacy(photo))
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
        }
        repository.publishPhoto(photo, caption, withEvidence = evidence)
        mutable.value = mutable.value.copy(captured = null, preview = null, queued = repository.pendingCount(), originalCapture = null, draftCaption = "")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }

    fun renewPreparation() = action("New v3 preparation requested; previous evidence preserved") {
        mutable.value.captured?.let { repository.renewPreparation(it) }
    }
    fun enrollDevice() = action("Device enrollment updated") { mutable.value = mutable.value.copy(deviceStatus = repository.enrollDevice()) }
    fun refreshDevice() = action("Device status refreshed") { mutable.value = mutable.value.copy(deviceStatus = repository.refreshDevice()) }
    fun resetDevice() = action("Only the installation key was retired; Nostr identity and old receipts retained") { mutable.value = mutable.value.copy(deviceStatus = repository.resetDevice()) }
    fun startCalibration(newSession: Boolean = false, demo: Boolean = false) = action("Private calibration session ready; the current frame has not been stored yet") {
        val file = mutable.value.captured ?: error("Capture a calibration frame first")
        mutable.value = mutable.value.copy(calibrationStatus = repository.startCalibration(file, newSession, demo),
            calibrationGuide = repository.currentCalibrationGuide())
    }
    fun addCalibrationFrame(role: String) = action("Private frame stored; it was not published") {
        val file = mutable.value.captured ?: error("Capture a frame first")
        mutable.value = mutable.value.copy(calibrationStatus = repository.addCalibrationFrame(file, role),
            calibrationGuide = repository.currentCalibrationGuide(), captured = null, preview = null, originalCapture = null)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }
    fun addGuidedCalibrationFrame() = action("Private calibration frame counted; capture the next independent frame") {
        val file = mutable.value.captured ?: error("Capture a frame first")
        mutable.value = mutable.value.copy(calibrationStatus = repository.addGuidedCalibrationFrame(file),
            calibrationGuide = repository.currentCalibrationGuide(), captured = null, preview = null, originalCapture = null)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }
    fun importCalibrationFrame(uri: Uri) = action("Other-camera test frame stored privately") {
        mutable.value = mutable.value.copy(calibrationStatus = repository.importCalibrationFrame(uri), calibrationGuide = repository.currentCalibrationGuide())
    }
    fun evaluateCalibration() = action("Calibration queued; no thresholds are approved automatically") {
        mutable.value = mutable.value.copy(calibrationStatus = repository.evaluateCalibration(), calibrationGuide = repository.currentCalibrationGuide())
    }
    fun refreshCalibration() = action("Calibration refreshed") { mutable.value = mutable.value.copy(calibrationStatus = repository.calibrationStatus(), calibrationGuide = repository.currentCalibrationGuide()) }
    fun cleanCopy() = action("Cleaned copy selected; original receipt will not be reused") {
        val original = mutable.value.captured ?: error("No capture")
        val copy = repository.cleanedCopy(original)
        val (_, preview) = repository.preparePhoto(copy)
        mutable.value = mutable.value.copy(captured = copy, preview = preview, privacy = repository.privacy(copy))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }
    fun useOriginal() = action("Original selected; bytes preserved") {
        val original = mutable.value.originalCapture ?: error("Original not available")
        val (_, preview) = repository.preparePhoto(original)
        mutable.value = mutable.value.copy(captured = original, preview = preview, privacy = repository.privacy(original))
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { persistCapture() }
    }
    fun exportEvidence(uri: Uri) = action("Standard v3 bundle exported separately") { mutable.value.selected?.let { repository.exportEvidence(it, uri) } }

    fun select(post: PhotoPost) {
        mutable.value = mutable.value.copy(selected = post, verification = null, copyResult = null)
        verify()
    }

    fun dismiss() { mutable.value = mutable.value.copy(selected = null, verification = null, copyResult = null) }

    fun verify() {
        val selected = mutable.value.selected ?: return
        viewModelScope.launch {
            val result = repository.verify(selected)
            if (mutable.value.selected?.id == selected.id) mutable.value = mutable.value.copy(verification = result)
        }
    }

    fun verifyCopy(uri: Uri) = action("Local photo copy checked") {
        val post = mutable.value.selected ?: return@action
        val valid = repository.verifyCopy(post, uri)
        mutable.value = mutable.value.copy(copyResult = if (valid) "VALID — selected JPEG matches the signed SHA-256"
            else "INVALID — selected JPEG differs from the signed SHA-256")
    }

    fun export(uri: Uri) = action("Bundle exported. Use the independent CLI to verify the standard .ots proof.") {
        mutable.value.selected?.let { repository.export(it, uri) }
    }

    fun cameraError() { mutable.value = mutable.value.copy(message = "Camera unavailable. Check permissions and try again.") }

    private fun action(success: String, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true)
            try { block(); mutable.value = mutable.value.copy(message = success) }
            catch (failure: ApiFailure) {
                val message = when (failure.code) {
                    "frame_quality_insufficient" -> "Frame not counted: crop too dark, saturated or without usable noise. Retake with even light; keep the same camera and orientation."
                    "unsupported_frame_route", "unsupported_frame_geometry" -> "Frame not counted: JPEG route, dimensions, orientation or color profile differ. Keep the original; return to the session's camera and orientation."
                    else -> "Local service: ${failure.code}. Draft, keys and signed queue preserved; retry or publish without origin evidence."
                }
                mutable.value = mutable.value.copy(message = message)
            }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "Operation unavailable. Existing keys, signed queue and files were preserved.") }
            finally { mutable.value = mutable.value.copy(busy = false, queued = repository.pendingCount()) }
        }
    }
}
