package com.example.photoanchor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import org.nostrdevkit.sdk.Keys
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/** All methods run on Dispatchers.IO in SocialRepository. No software-key fallback. */
class DeviceEvidenceRepository(private val context: Context, private val keys: Keys, private val api: LocalApi) {
    private val root = File(context.noBackupFilesDir, "photo-v3").apply { mkdirs() }
    private val stateFile = File(root, "device.json")
    private val installation = InstallationKeys(context)
    private val journal = SignedReceiptJournal(context)
    private fun state() = if (stateFile.exists()) StrictJson.parseObject(stateFile.readBytes()) else JSONObject()
    private fun save(state: JSONObject) = atomicWrite(stateFile, state.toString())
    private fun id() = UUID.randomUUID().toString().replace("-", "")
    private fun json(method: String, path: String, body: JSONObject = JSONObject()) = api.authorized(keys, method, path,
        if (method == "GET") byteArrayOf() else body.toString().toByteArray(Charsets.UTF_8))
    private fun un64(value: String) = Base64.decode(value, Base64.DEFAULT)
    private fun b64(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun pin(): ByteArray {
        require(BuildConfig.DEBUG && BuildConfig.AUTHORITY_SPKI.isNotEmpty()) { "Authority pin is not configured" }
        return un64(BuildConfig.AUTHORITY_SPKI).also { InstallationSignature.publicKey(it) }
    }
    private fun signer(): ByteArray {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo!!.apkContentsSigners
        require(signers.size == 1)
        return ReceiptV1.hash(signers.single().toByteArray())
    }
    private fun fields(e: InstallationKeys.Evidence) = JSONObject().put("key", b64(e.publicKey))
        .put("chain", JSONArray(e.certificateChain.map { b64(it) })).put("package", context.packageName)
        .put("version", BuildConfig.VERSION_CODE).put("signer", AuthorityRecords.hex(signer()))
        .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        .put("time", System.currentTimeMillis()).put("challenge", AuthorityRecords.hex(e.enrollmentChallenge))

    @Synchronized
    fun status(): String = if (BuildConfig.AUTHORITY_SPKI.isEmpty()) "UNAVAILABLE — operator authority pin is not configured"
        else state().optString("status", "ABSENT — device enrollment not started")

    @Synchronized
    fun enroll(): String {
        pin()
        val saved = state()
        if (!saved.has("enrollment_id")) {
            val operation = saved.optString("challenge_id").ifEmpty { id().also { saved.put("challenge_id", it); save(saved) } }
            val challenge = json("POST", "/v1/enrollments/challenge", JSONObject().put("operation_id", operation))
            require(System.currentTimeMillis() / 1000 < challenge.getDouble("expires")) { "Challenge expired; explicit recovery required" }
            val bytes = AuthorityRecords.unhex(challenge.getString("challenge"))
            val evidence = runCatching { installation.load() }.getOrElse { installation.provision(bytes) }
            require(evidence.enrollmentChallenge.contentEquals(bytes)) { "Existing key needs explicit recovery" }
            val metadata = fields(evidence)
            val proofRoot = EnrollmentProofV3.root(evidence.publicKey, context.packageName, BuildConfig.VERSION_CODE.toLong(), signer(), bytes)
            val proof = installation.signRoot(evidence.installationId, proofRoot)
            saved.put("enrollment_id", id()).put("fields", metadata).put("possession", b64(proof))
                .put("status", "PENDING — enrollment queued")
            save(saved)
        }
        val body = JSONObject().put("operation_id", saved.getString("enrollment_id"))
            .put("fields", saved.getJSONObject("fields")).put("possession", saved.getString("possession"))
        json("POST", "/v1/enrollments", body)
        return refreshEnrollment()
    }

    @Synchronized
    fun refreshEnrollment(): String {
        val saved = state()
        if (!saved.has("enrollment_id")) return status()
        val response = json("GET", "/v1/enrollments/${saved.getString("enrollment_id")}")
        val stage = response.getString("state")
        if (stage == "ACCEPTED") {
            val raw = un64(response.getJSONObject("output").getString("record"))
            val record = AuthorityRecords.verify(raw, pin(), "enrollment-v3/1")
            val evidence = installation.load()
            require(record.getString("installation") == AuthorityRecords.hex(evidence.installationId))
            require(record.getString("package") == context.packageName && record.getString("signer") == AuthorityRecords.hex(signer()))
            require(record.getLong("minimumVersion") <= BuildConfig.VERSION_CODE)
            require(record.getString("status") == "active" && record.getLong("notBefore") <= System.currentTimeMillis()/1000 && record.getLong("expires") > System.currentTimeMillis()/1000)
            saved.put("record", b64(raw))
        }
        val description = "$stage — ${response.optString("error").takeIf { it != "null" && it.isNotBlank() } ?: "local authority assessment"}"
        saved.put("status", description); save(saved)
        return description
    }

    /** Explicit recovery only: old receipt files and the Nostr identity are retained. */
    @Synchronized
    fun resetEnrollment(): String {
        installation.retire()
        if (stateFile.exists()) stateFile.renameTo(File(root, "retired-${id()}.json"))
        return "ABSENT — installation retired; start a new enrollment"
    }

    @Synchronized
    fun publishCommitment(file: File): String {
        require(refreshEnrollment().startsWith("ACCEPTED"))
        val digest = file.inputStream().use { PhotoProtocol.hash(it) }
        val savedOperation = File(root, "$digest.operation.json")
        val operation = if (savedOperation.exists()) StrictJson.parseObject(savedOperation.readBytes()) else JSONObject()
            .put("operation_id", id()).put("enrollment_id", state().getString("enrollment_id"))
            .put("media_sha256", digest).put("time", System.currentTimeMillis()).also {
                val current = state()
                val routeFile = File(file.absolutePath + ".route.json")
                if (current.has("geometry") && routeFile.exists()) {
                    val route = StrictJson.parseObject(routeFile.readBytes())
                    val geometry = current.getJSONObject("geometry")
                    if (route.getString("camera") == geometry.getString("camera") && route.getString("mode") == geometry.getString("mode"))
                        current.optString("reference_hash").takeIf { r -> r.isNotEmpty() }?.let { r -> it.put("reference_hash", r) }
                }
                atomicWrite(savedOperation, it.toString())
            }
        json("POST", "/v1/evidence", operation)
        val identity = operation.getString("operation_id")
        val response = json("GET", "/v1/evidence/$identity")
        if (response.getString("state") !in setOf("READY", "COMPLETE")) throw ApiFailure("evidence_" + response.getString("state").lowercase())
        val output = response.getJSONObject("output")
        // Retain the complete proposal before invoking the key.
        atomicWrite(File(root, "$identity.proposal.json"), output.toString())
        val envelope = output.getJSONObject("envelope").toString().toByteArray(Charsets.UTF_8)
        val body = AuthorityRecords.verify(envelope, pin(), "photo/evidence-preparation/1")
        val template = un64(output.getString("template")); val policy = un64(output.getString("policy")); val report = un64(output.getString("report"))
        val owner = keys.publicKey().use { it.toHex() }
        require(body.getString("operation") == identity && body.getString("owner") == owner && body.getString("media_sha256") == digest)
        require(body.getLong("issued") <= System.currentTimeMillis()/1000 + 60)
        require(body.getString("template_sha256") == PhotoProtocol.sha256(template) && body.getString("policy_sha256") == PhotoProtocol.sha256(policy) && body.getString("report_sha256") == PhotoProtocol.sha256(report))
        val profile = ReceiptV3.fromBytes(template).profile
        val evidence = installation.load()
        profile.requireIdentity(evidence.publicKey, evidence.certificateChain.toTypedArray(), context.packageName,
            BuildConfig.VERSION_CODE.toLong(), signer(), evidence.enrollmentChallenge)
        ReceiptV3Checks.validateAttachments(template, policy, report)
        val roots = file.inputStream().use { profile.roots(it, null) }
        require(AuthorityRecords.hex(roots.getValue("R")) == body.getString("root"))
        val receipt = journal.signOnce(identity, roots.getValue("R")) {
            require(body.getLong("expires") > System.currentTimeMillis()/1000) { "Preparation expired" }
            profile.receipt(installation.signRoot(evidence.installationId, roots.getValue("R")))
        }
        val checked = ReceiptV3.fromBytes(receipt)
        file.inputStream().use { checked.verify(it, null) }
        val result = api.authorized(keys, "POST", "/v1/evidence/$identity/receipt", receipt, binary = true)
        val commitment = AuthorityRecords.hex(ReceiptV1.protocolHash("receipt", roots.getValue("R"), checked.signature()))
        require(result.getString("commitment") == commitment)
        return commitment
    }

    @Synchronized
    fun renewPreparation(file: File): String {
        val hash = file.inputStream().use { PhotoProtocol.hash(it) }
        val path = File(root, "$hash.operation.json")
        if (path.exists()) check(path.renameTo(File(root, "$hash.retired-${id()}.json")))
        return "New preparation requested; previous receipt bytes retained"
    }

    @Synchronized
    fun startCalibration(geometry: JSONObject): String {
        require(refreshEnrollment().startsWith("ACCEPTED"))
        val saved = state()
        if (saved.has("calibration_id")) {
            val current = json("GET", "/v1/calibrations/${saved.getString("calibration_id")}")
            if (current.getString("state") == "COLLECTING") {
                require(saved.getJSONObject("geometry").toString() == geometry.toString()) { "Calibration route changed" }
            } else {
                saved.remove("calibration_id"); saved.remove("reference_hash")
            }
        }
        val identity = saved.optString("calibration_id").ifEmpty { id() }
        saved.put("calibration_id", identity).put("geometry", geometry); save(saved)
        json("POST", "/v1/calibrations", JSONObject().put("operation_id", identity)
            .put("enrollment_id", saved.getString("enrollment_id")).put("geometry", geometry))
        return "COLLECTING — independent frames required; 30 enrollment frames suggested"
    }

    @Synchronized
    fun addFrame(file: File, role: String): String {
        require(role in setOf("enrollment", "development", "genuine", "impostor"))
        val saved = state(); val identity = saved.getString("calibration_id")
        require(file.length() <= PhotoProtocol.MAX_PHOTO_BYTES)
        if (role != "impostor") {
            val route = StrictJson.parseObject(File(file.absolutePath + ".route.json").readBytes())
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            val orientation = androidx.exifinterface.media.ExifInterface(file)
                .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
            FixedCaptureRoute.requireCompatible(route, saved.getJSONObject("geometry"), bounds.outWidth, bounds.outHeight, orientation)
        }
        val raw = file.readBytes(); val hash = PhotoProtocol.sha256(raw)
        api.authorized(keys, "PUT", "/v1/calibrations/$identity/frames/$role/$hash", raw, binary = true)
        return calibrationStatus()
    }

    @Synchronized
    fun calibrationStatus(): String {
        val saved = state(); if (!saved.has("calibration_id")) return "No calibration session"
        val response = json("GET", "/v1/calibrations/${saved.getString("calibration_id")}")
        if (response.getString("state") == "COMPLETE") {
            saved.put("reference_hash", response.getJSONObject("output").getString("reference_hash"));save(saved)
        }
        val out = response.optJSONObject("output")
        val scores = out?.optJSONArray("scores")
        val measured = if (scores == null) "No scores yet" else (maxOf(0, scores.length() - 6) until scores.length()).joinToString("\n") {
            val sample = scores.getJSONObject(it)
            "${sample.getString("role")}: ${sample.opt("score")} · ${sample.getString("reason") }"
        }
        val regions = out?.optJSONObject("approved_regions")?.toString() ?: "none — operator approval required"
        return "${response.getString("state")} — ${response.getJSONArray("frames").length()} private frames\n$measured\nApproved decision regions: $regions"
    }

    @Synchronized
    fun evaluateCalibration(): String {
        json("POST", "/v1/calibrations/${state().getString("calibration_id")}/evaluate")
        return calibrationStatus()
    }

    fun verifyBundle(bytes: ByteArray, post: PhotoPost): String {
        val limits = mapOf("receipt.cbor" to 262144, "asset.bin" to PhotoProtocol.MAX_PHOTO_BYTES,
            "policy.json" to 1048576, "report.json" to 1048576, "commitment.bin" to 32,
            "commitment.bin.ots" to 2097152, "anchor.json" to 4096, "credential.bin" to 16777216)
        val files = mutableMapOf<String, ByteArray>(); var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(!entry.isDirectory && entry.name in limits && entry.name !in files)
                val raw = readBounded(archive, limits.getValue(entry.name)); total += raw.size
                require(total <= 48 * 1024 * 1024);files[entry.name] = raw
            }
        }
        val asset = files.getValue("asset.bin")
        require(PhotoProtocol.sha256(asset) == post.photoHash)
        val decoded = ReceiptV3.fromBytes(files.getValue("receipt.cbor"))
        ReceiptV3Checks.validateAttachments(files.getValue("receipt.cbor"), files.getValue("policy.json"), files.getValue("report.json"))
        val roots = decoded.verify(ByteArrayInputStream(asset), files["credential.bin"]?.let { ByteArrayInputStream(it) })
        val commitment = ReceiptV1.protocolHash("receipt", roots.getValue("R"), decoded.signature())
        require(commitment.contentEquals(files.getValue("commitment.bin")) && AuthorityRecords.hex(commitment) == post.evidenceCommitment)
        return "VALID — P-256 signature, attachments, JPEG and signed post commitment checked on this device"
    }
}
