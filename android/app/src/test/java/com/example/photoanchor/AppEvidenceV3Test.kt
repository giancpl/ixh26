package com.example.photoanchor

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class AppEvidenceV3Test {
    private fun vector() = JSONObject(javaClass.classLoader!!.getResourceAsStream("app-v3.json")!!.bufferedReader().readText())
    private fun bytes(v: JSONObject, name: String) = Base64.getDecoder().decode(v.getString(name))

    @Test fun validatesSharedReceiptAndBothIdentities() {
        val v = vector(); val raw = bytes(v, "receipt"); val photo = bytes(v, "photo")
        val decoded = ReceiptV3.fromBytes(raw)
        ReceiptV3Checks.validateAttachments(raw, bytes(v, "policy"), bytes(v, "report"))
        val roots = decoded.verify(ByteArrayInputStream(photo), null)
        assertEquals(v.getString("root"), AuthorityRecords.hex(roots.getValue("R")))
        val c = ReceiptV1.protocolHash("receipt", roots.getValue("R"), decoded.signature())
        assertEquals(v.getString("commitment"), AuthorityRecords.hex(c))
        val event = PhotoProtocol.parse(v.getJSONObject("event").toString())
        assertEquals(event.evidenceCommitment, AuthorityRecords.hex(c))
        assertEquals(v.getString("photo_sha256"), PhotoProtocol.sha256(photo))
        assertTrue(NostrBinding.verify(v.getJSONObject("http_auth_event").toString()))
        val record = AuthorityRecords.verify(bytes(v, "enrollment_record"), bytes(v, "authority_spki"), "enrollment-v3/1")
        assertEquals("active", record.getString("status"))
    }

    @Test fun validatesAuthorityPreparationAndRejectsChanges() {
        val v = vector();val envelope = v.getJSONObject("envelope").toString().toByteArray()
        val body = AuthorityRecords.verify(envelope, bytes(v, "authority_spki"), "photo/evidence-preparation/1")
        assertEquals(v.getString("root"), body.getString("root"))
        assertEquals(v.getString("owner"), body.getString("owner"))
        val bad = JSONObject(envelope.toString(Charsets.UTF_8)).put("body", body.put("root", "a".repeat(64)).toString()).toString().toByteArray()
        assertThrows(Exception::class.java) { AuthorityRecords.verify(bad, bytes(v,"authority_spki"),"photo/evidence-preparation/1") }
        val receipt = ReceiptV3.fromBytes(bytes(v,"receipt"))
        assertThrows(Exception::class.java) { receipt.verify(ByteArrayInputStream(bytes(v,"photo") + byteArrayOf(1)),null) }
        assertThrows(Exception::class.java) { ReceiptV3Checks.validateAttachments(bytes(v,"receipt"),bytes(v,"policy"),bytes(v,"report")+byteArrayOf(32)) }
        // Hash consistency alone must not authorize an unsupported policy schema.
        @Suppress("UNCHECKED_CAST")
        val fields = ProtocolCbor.decode(bytes(v,"receipt")) as Array<Any?>
        @Suppress("UNCHECKED_CAST")
        val evidence = fields[10] as Array<Any?>
        val unknownPolicy = JSONObject(String(bytes(v,"policy"), Charsets.UTF_8)).put("schema","unknown/1").toString().toByteArray()
        val changedReport = JSONObject(String(bytes(v,"report"), Charsets.UTF_8))
            .put("policyHash", PhotoProtocol.sha256(unknownPolicy)).toString().toByteArray()
        evidence[5] = ReceiptV1.hash(unknownPolicy); evidence[6] = ReceiptV1.hash(changedReport)
        assertThrows(Exception::class.java) {
            ReceiptV3Checks.validateAttachments(ReceiptV1.protocolEncode(fields), unknownPolicy, changedReport)
        }
    }

    @Test fun rejectsAmbiguousAttachmentJson() {
        assertThrows(Exception::class.java) { StrictJson.parseObject("{\"x\":1,\"x\":2}".toByteArray()) }
        assertThrows(Exception::class.java) { StrictJson.parseObject("{\"x\":{\"a\":1,\"a\":2}}".toByteArray()) }
        assertThrows(Exception::class.java) { StrictJson.parseObject("{\"x\":NaN}".toByteArray()) }
        assertThrows(Exception::class.java) { StrictJson.parseObject("{} trailing".toByteArray()) }
        for (bad in listOf("{\"n\":01}", "{\"x\":TRUE}", "{/*comment*/}", "{\"x\":'text'}"))
            assertThrows(Exception::class.java) { StrictJson.parseObject(bad.toByteArray()) }
        assertThrows(Exception::class.java) { StrictJson.parseObject("{\"x\":\"\\ud800\"}".toByteArray()) }
    }
}
