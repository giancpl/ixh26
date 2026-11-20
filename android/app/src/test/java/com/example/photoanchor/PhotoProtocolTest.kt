package com.example.photoanchor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.nostrdevkit.sdk.Event
import org.nostrdevkit.sdk.Keys

class PhotoProtocolTest {
    private val api = "http://127.0.0.1:8080"
    private fun bytes(path: String) = javaClass.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes() }
    private fun text(path: String) = bytes(path).toString(Charsets.UTF_8)
    private fun basic() = PhotoProtocol.parse(text("event.json"), api)
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Expected contract rejection") } catch (_: IllegalArgumentException) { }
    }

    @Test fun sharedPythonVectorsHaveValidSdkSignaturesAndExactPayloadBytes() {
        val vectors = JSONArray(text("vectors.json"))
        assertTrue(vectors.length() >= 4)
        for (index in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(index)
            val post = PhotoProtocol.parse(vector.getJSONObject("event").toString(), api)
            Event.fromJson(post.json).use { event ->
                assertTrue("${vector.getString("name")} ID", event.verifyId())
                assertTrue("${vector.getString("name")} signature", event.verifySignature())
            }
            val payload = PhotoProtocol.payload(post)
            assertArrayEquals(vector.getString("payload_utf8").toByteArray(Charsets.UTF_8), payload)
            assertEquals(vector.getString("payload_sha256"), PhotoProtocol.sha256(payload))
            assertEquals(vector.getString("photo_sha256"), post.photoHash)
            assertFalse(payload.last() == '\n'.code.toByte())
        }
    }

    @Test fun actualJpegAndPayloadMatchPublishedFixtureHashes() {
        val post = basic()
        assertEquals(post.photoHash, PhotoProtocol.sha256(bytes("photo.jpg")))
        assertArrayEquals(bytes("payload.bin"), PhotoProtocol.payload(post))
        assertNotEquals(post.photoHash, PhotoProtocol.sha256(bytes("negative/photo.jpg")))
    }

    @Test fun alteredSignatureAndMetadataAreRejectedByOfficialSdk() {
        assertFalse(NostrBinding.verify(text("negative/signature.json")))
        assertFalse(NostrBinding.verify(text("negative/metadata.json")))
        rejected { PhotoProtocol.parse(text("negative/signature.json")) }
        rejected { PhotoProtocol.parse(text("negative/metadata.json")) }
    }

    @Test fun aDifferentConfiguredArchiveIsRejectedBeforeAnyDownload() {
        rejected { PhotoProtocol.parse(text("event.json"), "https://external.example") }
    }

    @Test fun changingDeclaredProfileNameDoesNotChangeOldPhotoValidity() {
        val before = JSONObject(text("profile.json"))
        val after = JSONObject(text("profile-updated.json"))
        assertTrue(NostrBinding.verify(before.toString()))
        assertTrue(NostrBinding.verify(after.toString()))
        assertEquals(before.getString("pubkey"), after.getString("pubkey"))
        assertEquals(basic().pubkey, after.getString("pubkey"))
        assertTrue(NostrBinding.verify(basic().json))
    }

    @Test fun photoBuilderUsesSdkAndTheFrozenNip68Contract() {
        Keys.generate().use { keys ->
            val hash = PhotoProtocol.sha256(bytes("photo.jpg"))
            val caption = "Unicode 雪 🌍 and \"quotes\" \\ newline\n"
            val post = PhotoProtocol.signPhoto(keys, caption, hash, "$api/v1/media/$hash", 64, 48)
            assertEquals(caption, post.caption)
            assertEquals(hash, post.photoHash)
            assertTrue(NostrBinding.verify(post.json))
            assertEquals(post.id, PhotoProtocol.parse(post.json, api).id)
        }
    }

    private fun wrapper(status: String = "PENDING"): JSONObject {
        val post = basic()
        return JSONObject().put("event", JSONObject(post.json)).put("publication_status", "PUBLISHED")
            .put("settlement", JSONObject()
                .put("version", "upp-settlement/0.1").put("backend", "opentimestamps-bitcoin")
                .put("network", "regtest").put("status", status)
                .put("payload_sha256", PhotoProtocol.sha256(PhotoProtocol.payload(post)))
                .put("proof_sha256", "a".repeat(64)).put("proof_url", "$api/v1/posts/${post.id}/proof")
                .put("binding_checked", true)
                .put("bitcoin", JSONObject().put("block_height", JSONObject.NULL).put("block_time", JSONObject.NULL))
                .put("diagnostics", JSONArray()))
    }

    @Test fun pendingReceiptAndAbsentSubmissionRemainDistinct() {
        assertEquals("PENDING", PhotoProtocol.settlement(basic(), wrapper(), api)!!.getString("status"))
        val absent = wrapper().put("settlement", JSONObject.NULL)
        assertNull(PhotoProtocol.settlement(basic(), absent, api))
    }

    @Test fun confirmationRequiresBoundExpectedPayloadSelectedNetworkAndBitcoinEvidence() {
        val response = wrapper("CONFIRMED")
        val receipt = response.getJSONObject("settlement")
        receipt.put("bitcoin", JSONObject().put("block_height", 104).put("block_time", 1700000000))
        receipt.put("diagnostics", JSONArray().put(JSONObject().put("code", "regtest_only").put("message", "Local chain")))
        assertEquals("CONFIRMED", PhotoProtocol.settlement(basic(), response, api)!!.getString("status"))
        receipt.put("network", "mainnet")
        rejected { PhotoProtocol.settlement(basic(), response, api) }
        receipt.put("network", "regtest").put("payload_sha256", "b".repeat(64))
        rejected { PhotoProtocol.settlement(basic(), response, api) }
        receipt.put("payload_sha256", PhotoProtocol.sha256(PhotoProtocol.payload(basic()))).put("binding_checked", false)
        rejected { PhotoProtocol.settlement(basic(), response, api) }
        receipt.put("binding_checked", true).getJSONObject("bitcoin").put("block_height", JSONObject.NULL)
        rejected { PhotoProtocol.settlement(basic(), response, api) }
    }

    @Test fun actualOperationalFailureMayHaveNoProofAndNoBinding() {
        for (status in listOf("ERROR", "UNVERIFIABLE")) {
            val response = wrapper(status)
            val receipt = response.getJSONObject("settlement")
            listOf("payload_sha256", "proof_sha256", "proof_url").forEach { receipt.put(it, JSONObject.NULL) }
            receipt.put("binding_checked", false)
            assertEquals(status, PhotoProtocol.settlement(basic(), response, api)!!.getString("status"))
        }
    }

    @Test fun boundedHashRejectsOversizeInput() {
        rejected { PhotoProtocol.hash(ByteArray(11).inputStream(), maxBytes = 10) }
    }
}
