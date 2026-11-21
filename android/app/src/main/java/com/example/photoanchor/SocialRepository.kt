package com.example.photoanchor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.nostrdevkit.sdk.Client
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Filter
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.RelayUrl
import org.nostrdevkit.sdk.ReqTarget
import java.io.File
import java.time.Duration

data class FeedEntry(val post: PhotoPost, val name: String, val preview: Bitmap?)
data class Verification(
    val event: String = "Not checked", val integrity: String = "Not checked",
    val timestamp: String = "Not checked", val publication: String = "Unknown",
    val receipt: String = "No v3 receipt", val origin: String = "Origin not verified",
    val device: String = "Not checked", val evidenceTimestamp: String = "No v3 timestamp"
)

class SocialRepository(private val context: Context) {
    private val mutex = Mutex()
    private val identities = IdentityStore(context)
    private val outbox = Outbox(context)
    private val api = LocalApi()
    private val cache = File(context.filesDir, "media").apply { mkdirs() }
    private val feedFile = File(context.noBackupFilesDir, "feed.json")
    private lateinit var keys: Keys
    private lateinit var device: DeviceEvidenceRepository
    private var client: Client? = null
    private val profiles = mutableMapOf<String, Pair<Long, String>>()

    suspend fun initialize(): Pair<String, String> = withContext(Dispatchers.IO) {
        keys = identities.loadOrCreate()
        device = DeviceEvidenceRepository(context, keys, api)
        keys.publicKey().use { it.toHex() } to identities.name()
    }

    suspend fun saveProfile(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            identities.saveName(name)
            val content = JSONObject().put("name", name.trim()).put("display_name", name.trim()).toString()
            EventBuilder(Kind(0u), content).finalize(keys).use { outbox.enqueue(it.asJson()) }
        }
    }

    private suspend fun relay(): Client {
        require(BuildConfig.DEBUG && BuildConfig.RELAY_URL == "ws://127.0.0.1:7777")
        return (client ?: Client().also { client = it }).also {
            it.addRelay(RelayUrl.parse(BuildConfig.RELAY_URL), andConnect = true)
            it.connect(Duration.ofSeconds(2))
        }
    }

    /** Finalization occurs before hashing; neither upload nor retry edits the JPEG. */
    suspend fun preparePhoto(file: File): Pair<File, Bitmap?> = withContext(Dispatchers.IO) {
        require(file.length() in 1..PhotoProtocol.MAX_PHOTO_BYTES.toLong())
        file to thumbnail(file)
    }

    suspend fun publishPhoto(file: File, caption: String, withEvidence: Boolean = false): PhotoPost = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(caption.length <= 1000)
            require(!withEvidence || !file.name.startsWith("cleaned-")) { "Cleaned copies use the ordinary Nostr path" }
            val digest = file.inputStream().use { PhotoProtocol.hash(it) }
            val uploaded = api.upload(file)
            require(uploaded.getString("sha256") == digest)
            require(uploaded.getString("url") == "${api.baseUrl}/v1/media/$digest")
            require(uploaded.getString("mime") == "image/jpeg" && uploaded.getLong("size") == file.length())
            // Detect any local mutation while the upload was in progress.
            require(file.inputStream().use { PhotoProtocol.hash(it) } == digest)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType == "image/jpeg")
            val commitment = if (withEvidence) device.publishCommitment(file) else null
            val postFile = File(context.noBackupFilesDir, "draft-posts/${digest}-${PhotoProtocol.sha256(caption.toByteArray())}-${withEvidence}.json")
            val post = if (postFile.exists()) PhotoProtocol.parse(postFile.readText(), api.baseUrl) else {
                PhotoProtocol.signPhoto(keys, caption, digest, uploaded.getString("url"), bounds.outWidth, bounds.outHeight, commitment)
                    .also { atomicWrite(postFile, it.json) }
            }
            outbox.enqueue(post.json)
            // Once signed, the durable queue owns this event even when HTTP is down.
            runCatching { deliver(File(context.noBackupFilesDir, "outbox/${post.id}.json")) }
            post
        }
    }

    private suspend fun deliver(file: File) {
        val json = file.readText()
        Event.fromJson(json).use { event ->
            require(event.verify())
            when (event.kind().use { it.asU16().toInt() }) {
                0 -> {
                    val result = relay().sendEvent(event, okTimeout = Duration.ofSeconds(3))
                    try { require(result.success.isNotEmpty()) } finally { result.destroy() }
                }
                20 -> {
                    val post = PhotoProtocol.parse(json, api.baseUrl)
                    val response = api.submit(json)
                    require(response.getJSONObject("event").getString("id") == post.id)
                }
                else -> error("Unsupported local outbox event")
            }
        }
        outbox.acknowledge(file)
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) { outbox.pending().size }

    suspend fun cachedFeed(): List<FeedEntry> = withContext(Dispatchers.IO) {
        if (!feedFile.exists()) emptyList() else runCatching {
            val events = JSONArray(feedFile.readText())
            feedEntries((0 until events.length()).map { events.getJSONObject(it).toString() }, download = false)
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(): List<FeedEntry> = withContext(Dispatchers.IO) {
        mutex.withLock {
            // A failed profile publication must not block photo submission.
            outbox.pending().take(10).forEach { file -> runCatching { deliver(file) } }
            val filter = Filter().kinds(listOf(Kind(0u), Kind(20u))).limit(100u)
            val events = relay().fetchEvents(
                ReqTarget.single(RelayUrl.parse(BuildConfig.RELAY_URL), listOf(filter)),
                timeout = Duration.ofSeconds(3), maxEvents = 100u
            )
            val serialized = events.map { event -> event.use { it.asJson() } }
            val photos = feedEntries(serialized, download = true)
            val saved = JSONArray()
            // Preserve verified profile events too, so offline names remain available.
            serialized.forEach { json -> if (NostrBinding.verify(json)) saved.put(JSONObject(json)) }
            atomicWrite(feedFile, saved.toString())
            photos
        }
    }

    private fun feedEntries(events: List<String>, download: Boolean): List<FeedEntry> {
        events.forEach { json -> runCatching {
            val raw = JSONObject(json)
            if (raw.getInt("kind") == 0 && NostrBinding.verify(json)) {
                val pubkey = raw.getString("pubkey")
                val time = raw.getLong("created_at")
                val meta = JSONObject(raw.getString("content"))
                val name = meta.optString("display_name").ifBlank { meta.optString("name") }.take(80)
                if (time >= (profiles[pubkey]?.first ?: -1L)) profiles[pubkey] = time to name
            }
        } }
        return events.mapNotNull { json -> runCatching {
            val post = PhotoProtocol.parse(json, api.baseUrl)
            val photo = File(cache, "${post.photoHash}.jpg")
            if (download && !photo.exists()) runCatching {
                val bytes = api.photo(post)
                require(PhotoProtocol.sha256(bytes) == post.photoHash)
                photo.writeBytes(bytes)
            }
            FeedEntry(post, profiles[post.pubkey]?.second.orEmpty(), if (photo.exists()) thumbnail(photo) else null)
        }.getOrNull() }.sortedByDescending { it.post.createdAt }.take(50)
    }

    suspend fun verify(post: PhotoPost): Verification = withContext(Dispatchers.IO) {
        val checked = runCatching { PhotoProtocol.parse(post.json, api.baseUrl) }.getOrNull()
            ?: return@withContext Verification(event = "INVALID — event ID or signature", integrity = "Not checked", timestamp = "Not requested")
        var result = Verification(event = "VALID — ID and Schnorr signature checked on this device")
        val bytes = try { api.photo(checked) } catch (_: Exception) {
            return@withContext result.copy(integrity = "UNAVAILABLE — photo download failed", timestamp = "Not requested")
        }
        if (PhotoProtocol.sha256(bytes) != checked.photoHash) {
            return@withContext result.copy(integrity = "INVALID — original JPEG hash differs", timestamp = "Not requested")
        }
        result = result.copy(integrity = "VALID — original JPEG SHA-256 checked on this device")
        if (checked.evidenceCommitment != null) {
            val bundle = try { api.evidenceBundle(checked) } catch (_: Exception) { null }
            result = if (bundle == null) result.copy(receipt = "UNAVAILABLE — v3 bundle download failed") else {
                try { result.copy(receipt = device.verifyBundle(bundle, checked)) }
                catch (_: Exception) { result.copy(receipt = "INVALID — v3 signature, attachments or commitment mismatch") }
            }
            try {
                val v3 = api.verifyEvidence(checked)
                require(v3.getString("commitment") == checked.evidenceCommitment)
                val assessment = v3.optJSONObject("assessment")
                val v3Settlement = v3.optJSONObject("settlement")
                if (assessment != null && assessment.has("decision")) {
                    result = result.copy(origin = "Local server analysis — " + assessment.getJSONObject("decision").getString("label") +
                        "\nManufacturer: " + assessment.getJSONObject("manufacturer").getString("status") +
                        " · PRNU: " + assessment.getJSONObject("prnu").getString("status"),
                        device = "Local authority — " + assessment.getJSONObject("enrollment").getString("status"))
                }
                if (v3Settlement != null) {
                    require(v3Settlement.getString("network") == "regtest")
                    require(v3Settlement.getString("version") == "upp-settlement/0.1")
                    require(v3Settlement.getString("backend") == "opentimestamps-bitcoin")
                    if (!v3Settlement.isNull("proof_url"))
                        require(v3Settlement.getString("proof_url") == "${api.baseUrl}/v1/posts/${checked.id}/evidence/proof")
                    if (!v3Settlement.isNull("proof_sha256"))
                        require(PhotoProtocol.sha256(api.evidenceProof(checked)) == v3Settlement.getString("proof_sha256"))
                    val v3Status = v3Settlement.getString("status")
                    require(v3Status in setOf("PENDING", "CONFIRMED", "INVALID", "UNVERIFIABLE", "ERROR"))
                    if (v3Status !in setOf("ERROR", "UNVERIFIABLE") || !v3Settlement.isNull("payload_sha256"))
                        require(v3Settlement.getString("payload_sha256") == PhotoProtocol.sha256(AuthorityRecords.unhex(v3.getString("commitment"))))
                    if (v3Status == "CONFIRMED") {
                        require(v3Settlement.getBoolean("binding_checked"))
                        val block = v3Settlement.getJSONObject("bitcoin")
                        require(!block.isNull("block_height") && !block.isNull("block_time"))
                    }
                    result = result.copy(evidenceTimestamp = v3Settlement.getString("status") + " — v3 timestamp from local node — regtest")
                }

            } catch (_: Exception) {
                result = result.copy(origin = "UNAVAILABLE — no current local server assessment", device = "UNAVAILABLE — current enrollment not checked",
                    evidenceTimestamp = "UNAVAILABLE — no consistent fresh v3 timestamp report")
            }
            if (result.receipt.startsWith("INVALID")) result = result.copy(origin = "Not accepted — local receipt verification failed")
        }
        try {
            val snapshot = api.post(checked.id)
            result = result.copy(publication = snapshot.getString("publication_status"))
            val initial = PhotoProtocol.settlement(checked, snapshot, api.baseUrl)
                ?: return@withContext result.copy(timestamp = "NOT SUBMITTED — no settlement result yet")
            if (initial.isNull("proof_url") || initial.isNull("proof_sha256")) {
                return@withContext result.copy(timestamp = "${initial.getString("status")} — timestamp submission unavailable; publication is independent")
            }
            // Fresh request: the server must read the local proof and query Bitcoin Core.
            val response = api.post(checked.id, verify = true)
            result = result.copy(publication = response.getString("publication_status"))
            val receipt = PhotoProtocol.settlement(checked, response, api.baseUrl)
                ?: return@withContext result.copy(timestamp = "NOT SUBMITTED — no settlement result yet")
            val status = receipt.getString("status")
            val proof = api.proof(checked)
            require(PhotoProtocol.sha256(proof) == receipt.getString("proof_sha256"))
            result.copy(timestamp = when (status) {
                "CONFIRMED" -> {
                    val bitcoin = receipt.getJSONObject("bitcoin")
                    val time = java.time.Instant.ofEpochSecond(bitcoin.getLong("block_time"))
                    "CONFIRMED — verified by local node — regtest\nBlock ${bitcoin.getLong("block_height")} · $time"
                }
                "PENDING" -> "PENDING — Bitcoin timestamp awaiting explicit mining / upgrade"
                "INVALID" -> "INVALID — local node rejected the timestamp proof"
                "UNVERIFIABLE" -> "UNVERIFIABLE — local Bitcoin node cannot verify this proof"
                else -> "ERROR — timestamp operation unavailable"
            })
        } catch (_: Exception) {
            result.copy(timestamp = "UNAVAILABLE — no consistent fresh local node report")
        }
    }

    suspend fun verifyCopy(post: PhotoPost, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        PhotoProtocol.parse(post.json, api.baseUrl)
        context.contentResolver.openInputStream(uri)?.use { PhotoProtocol.hash(it) == post.photoHash }
            ?: throw ApiFailure("photo_unavailable")
    }

    suspend fun export(post: PhotoPost, uri: Uri) = withContext(Dispatchers.IO) {
        PhotoProtocol.parse(post.json, api.baseUrl)
        val bytes = api.bundle(post)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
    }

    suspend fun renewPreparation(file: File): String = withContext(Dispatchers.IO) { device.renewPreparation(file) }
    suspend fun deviceStatus(): String = withContext(Dispatchers.IO) { device.status() }
    suspend fun enrollDevice(): String = withContext(Dispatchers.IO) { device.enroll() }
    suspend fun refreshDevice(): String = withContext(Dispatchers.IO) { device.refreshEnrollment() }
    suspend fun resetDevice(): String = withContext(Dispatchers.IO) { device.resetEnrollment() }
    suspend fun calibrationStatus(): String = withContext(Dispatchers.IO) { device.calibrationStatus() }
    suspend fun evaluateCalibration(): String = withContext(Dispatchers.IO) { device.evaluateCalibration() }

    suspend fun startCalibration(file: File): String = withContext(Dispatchers.IO) {
        val exif = ExifInterface(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val routeFile = File(file.absolutePath + ".route.json")
        require(routeFile.exists()) { "A fixed app camera route is required" }
        val route = JSONObject(routeFile.readText())
        require(route.getBoolean("fixed"))
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        require(orientation in 1..8)
        val geometry = JSONObject().put("camera", route.getString("camera"))
            .put("mode", route.getString("mode")).put("width", 256).put("height", 256)
            .put("decoder", "pillow-13.0.0/rgb-green-stored-exif-$orientation/1")
            .put("source_width", bounds.outWidth).put("source_height", bounds.outHeight)
        device.startCalibration(geometry)
    }

    suspend fun addCalibrationFrame(file: File, role: String): String = withContext(Dispatchers.IO) { device.addFrame(file, role) }
    suspend fun importCalibrationFrame(uri: Uri): String = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "calibration-import.jpg")
        try {
            val bytes = context.contentResolver.openInputStream(uri)!!.use { readBounded(it, PhotoProtocol.MAX_PHOTO_BYTES) }
            file.writeBytes(bytes); device.addFrame(file, "impostor")
        } finally { file.delete() }
    }

    suspend fun privacy(file: File): String = withContext(Dispatchers.IO) {
        val exif = ExifInterface(file)
        val sensitive = listOf(ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_IMAGE_UNIQUE_ID)
            .filter { exif.getAttribute(it) != null }
        "Original JPEG preserved. " + if (sensitive.isEmpty()) "No listed sensitive EXIF fields detected; review before sharing."
            else "Sensitive EXIF present: " + sensitive.joinToString() + ". Choose a cleaned copy or cancel."
    }

    suspend fun cleanedCopy(file: File): File = withContext(Dispatchers.IO) {
        val copy = File(context.filesDir, "cleaned-${java.util.UUID.randomUUID()}.jpg")
        file.copyTo(copy, overwrite = false)
        ExifInterface(copy).apply {
            listOf(
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL, ExifInterface.TAG_BODY_SERIAL_NUMBER,
                ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_LENS_SERIAL_NUMBER,
                ExifInterface.TAG_IMAGE_UNIQUE_ID
            ).forEach { setAttribute(it, null) }
            saveAttributes()
        }
        copy
    }

    suspend fun exportEvidence(post: PhotoPost, uri: Uri) = withContext(Dispatchers.IO) {
        val bytes = api.evidenceBundle(post)
        device.verifyBundle(bytes, post)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        client?.let { runCatching { it.shutdown() }; it.close() }
        if (::keys.isInitialized) keys.close()
    }

    companion object {
        fun thumbnail(file: File): Bitmap? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var sample = 1
            while (options.outWidth / sample > 1200 || options.outHeight / sample > 1200) sample *= 2
            options.inSampleSize = sample
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(file.absolutePath, options)?.let { bitmap ->
                val angle = when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (angle == 0f) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                    android.graphics.Matrix().apply { postRotate(angle) }, true)
            }
        }
    }
}
