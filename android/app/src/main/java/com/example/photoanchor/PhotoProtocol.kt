package com.example.photoanchor

import org.json.JSONObject
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.EventBuilder
import org.nostrdevkit.sdk.Keys
import org.nostrdevkit.sdk.Kind
import org.nostrdevkit.sdk.Tag
import java.io.InputStream
import java.security.MessageDigest

data class PhotoPost(
    val json: String, val id: String, val pubkey: String, val signature: String,
    val photoHash: String, val photoUrl: String, val width: Int, val height: Int,
    val caption: String, val createdAt: Long, val evidenceCommitment: String? = null
)

/** Application contract. All Nostr cryptography is delegated to the official SDK. */
object PhotoProtocol {
    const val MAX_PHOTO_BYTES = 20 * 1024 * 1024
    private val hex64 = Regex("[0-9a-f]{64}")
    private val hex128 = Regex("[0-9a-f]{128}")

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    fun hash(input: InputStream, maxBytes: Int = MAX_PHOTO_BYTES): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Photo exceeds the size limit" }
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun parse(json: String, apiUrl: String? = null): PhotoPost {
        val raw = JSONObject(json)
        require(raw.keys().asSequence().toSet() == setOf("id", "pubkey", "sig", "kind", "created_at", "tags", "content"))
        val id = raw.getString("id")
        val pubkey = raw.getString("pubkey")
        val signature = raw.getString("sig")
        require(hex64.matches(id) && hex64.matches(pubkey) && hex128.matches(signature))
        Event.fromJson(json).use { event -> require(event.verifyId() && event.verifySignature()) }
        require(raw.getInt("kind") == 20)
        val tags = raw.getJSONArray("tags")
        val entries = (0 until tags.length()).map { index ->
            val tag = tags.getJSONArray(index)
            (0 until tag.length()).map { tag.getString(it) }.also { require(it.isNotEmpty()) }
        }
        fun tag(name: String): List<String> = entries.filter { it.first() == name }.single()
        val evidence = entries.filter { it.first() == "photo-evidence" }
        require(evidence.isEmpty() || (evidence.size == 1 && evidence[0].size == 3 &&
            evidence[0][1] == "fingerprinto/evidence-v3" && hex64.matches(evidence[0][2])))
        val caption = raw.getString("content")
        require(tag("title") == listOf("title", caption.ifEmpty { "Photo" }))
        require(tag("m") == listOf("m", "image/jpeg"))
        val hash = tag("x").also { require(it.size == 2) }[1]
        require(hex64.matches(hash))
        val imeta = tag("imeta").drop(1).map { field ->
            val parts = field.split(' ', limit = 2)
            require(parts.size == 2 && parts[0] in setOf("url", "m", "x", "dim"))
            parts[0] to parts[1]
        }
        require(imeta.size == 4 && imeta.map { it.first }.distinct().size == 4)
        val meta = imeta.toMap()
        require(meta["m"] == "image/jpeg" && meta["x"] == hash)
        val dim = Regex("([1-9][0-9]*)x([1-9][0-9]*)").matchEntire(meta.getValue("dim"))!!
        val width = dim.groupValues[1].toInt()
        val height = dim.groupValues[2].toInt()
        val url = meta.getValue("url")
        require(url.isNotEmpty())
        if (apiUrl != null) require(url == "$apiUrl/v1/media/$hash")
        return PhotoPost(json, id, pubkey, signature, hash, url, width, height, caption, raw.getLong("created_at"), evidence.firstOrNull()?.get(2))
    }

    fun payload(post: PhotoPost): ByteArray {
        // Revalidate at the trust boundary, including the SDK ID/signature checks.
        val checked = parse(post.json)
        return "[\"upp-nostr-photo/1\",\"${checked.id}\",\"${checked.pubkey}\",\"${checked.signature}\"]"
            .toByteArray(Charsets.UTF_8)
    }

    fun signPhoto(keys: Keys, caption: String, hash: String, url: String, width: Int, height: Int, evidenceCommitment: String? = null): PhotoPost {
        require(caption.length <= 1000 && hex64.matches(hash) && width > 0 && height > 0)
        val tags = (listOf(
            listOf("title", caption.ifEmpty { "Photo" }),
            listOf("imeta", "url $url", "m image/jpeg", "x $hash", "dim ${width}x$height"),
            listOf("m", "image/jpeg"), listOf("x", hash)
        ) + if (evidenceCommitment != null) {
            require(hex64.matches(evidenceCommitment))
            listOf(listOf("photo-evidence", "fingerprinto/evidence-v3", evidenceCommitment))
        } else emptyList()).map { Tag.parse(it) }
        return EventBuilder(Kind(20u), caption).tags(tags).finalize(keys).use { parse(it.asJson()) }
    }

    /** Receipt consistency only. This is a report from the configured local node. */
    fun settlement(post: PhotoPost, wrapper: JSONObject, apiUrl: String): JSONObject? {
        val responsePost = parse(wrapper.getJSONObject("event").toString(), apiUrl)
        require(responsePost.id == post.id && responsePost.pubkey == post.pubkey && responsePost.signature == post.signature)
        if (wrapper.isNull("settlement")) return null
        val receipt = wrapper.getJSONObject("settlement")
        require(receipt.getString("network") == "regtest")
        require(receipt.getString("version") == "upp-settlement/0.1")
        require(receipt.getString("backend") == "opentimestamps-bitcoin")
        val status = receipt.getString("status")
        require(status in setOf("PENDING", "CONFIRMED", "INVALID", "UNVERIFIABLE", "ERROR"))
        // Operational failure may precede proof creation. It is never evidence
        // of binding or confirmation, and nullable fields remain nullable.
        val operational = status == "ERROR" || status == "UNVERIFIABLE"
        if (!operational || !receipt.isNull("payload_sha256")) {
            require(receipt.getString("payload_sha256") == sha256(payload(post)))
        }
        if (!operational || !receipt.isNull("proof_url")) {
            require(receipt.getString("proof_url") == "$apiUrl/v1/posts/${post.id}/proof")
        }
        if (!operational || !receipt.isNull("proof_sha256")) {
            require(hex64.matches(receipt.getString("proof_sha256")))
        }
        if (status == "CONFIRMED") {
            require(receipt.getBoolean("binding_checked"))
            val bitcoin = receipt.getJSONObject("bitcoin")
            require(!bitcoin.isNull("block_height") && bitcoin.getLong("block_height") >= 0)
            require(!bitcoin.isNull("block_time") && bitcoin.getLong("block_time") > 0)
            val diagnostics = receipt.getJSONArray("diagnostics")
            require((0 until diagnostics.length()).any { diagnostics.getJSONObject(it).getString("code") == "regtest_only" })
        }
        return receipt
    }
}
