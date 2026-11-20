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
    val copyResult: String? = null
)

class PhotoAnchorModel(application: Application) : AndroidViewModel(application) {
    private val repository = SocialRepository(application)
    private val mutable = MutableStateFlow(PhotoAnchorState())
    val state = mutable.asStateFlow()
    private var refreshing = false

    init {
        viewModelScope.launch {
            try {
                val (pubkey, name) = repository.initialize()
                val cached = repository.cachedFeed()
                mutable.value = mutable.value.copy(ready = true, pubkey = pubkey, name = name,
                    feed = cached, queued = repository.pendingCount(), message = "Local regtest demo · USB forwarding required")
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
    }

    fun refresh() { viewModelScope.launch { refreshNow() } }

    fun saveProfile(name: String) = action("Profile saved locally; signed metadata queued for the local relay") {
        repository.saveProfile(name)
        mutable.value = mutable.value.copy(name = name.trim(), queued = repository.pendingCount())
    }

    fun captured(file: File) = action("JPEG finalized. Add a caption and publish when ready.") {
        val (prepared, bitmap) = repository.preparePhoto(file)
        mutable.value = mutable.value.copy(captured = prepared, preview = bitmap)
    }

    fun discard() {
        val file = mutable.value.captured
        mutable.value = mutable.value.copy(captured = null, preview = null)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { file?.delete() }
    }

    fun publish(caption: String) = action("Signed post saved. Publication and timestamp progress independently.") {
        val photo = mutable.value.captured ?: return@action
        repository.publishPhoto(photo, caption)
        mutable.value = mutable.value.copy(captured = null, preview = null, queued = repository.pendingCount())
    }

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
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "Operation unavailable. Existing keys, signed queue and files were preserved.") }
            finally { mutable.value = mutable.value.copy(busy = false, queued = repository.pendingCount()) }
        }
    }
}
