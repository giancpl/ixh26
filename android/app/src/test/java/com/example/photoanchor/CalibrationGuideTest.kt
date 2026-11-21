package com.example.photoanchor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CalibrationGuideTest {
    private fun fixture() = JSONObject(javaClass.classLoader!!.getResourceAsStream("prnu-session.json")!!.bufferedReader().readText())
    private fun snapshot(count: Int, state: String = "COLLECTING"): JSONObject {
        val full = fixture(); val frames = full.getJSONArray("frames"); val subset = JSONArray()
        for (i in 0 until count) subset.put(frames.getJSONObject(i))
        return full.put("frames", subset).put("state", state)
    }

    @Test fun countsOnlyServerAcceptedFramesAndSeparatesPhases() {
        val stages = mapOf(0 to "enrollment", 29 to "enrollment", 30 to "development", 39 to "development", 40 to "genuine", 49 to "genuine")
        for ((count, role) in stages) {
            val progress = CalibrationGuide.fromJson(snapshot(count), true)
            assertEquals(role, progress.nextRole)
            assertEquals(count, progress.roles.size)
            assertFalse(progress.ready)
        }
        val ready = CalibrationGuide.fromJson(fixture(), true)
        assertTrue(ready.ready); assertNull(ready.nextRole)
        assertEquals(30, ready.count("enrollment")); assertEquals(10, ready.count("development")); assertEquals(10, ready.count("genuine"))
    }

    @Test fun restartAndLostUploadResponseKeepTheSameRoleAtBoundaries() {
        for (count in listOf(30, 40, 50)) {
            val response = snapshot(count)
            val restarted = CalibrationGuide.fromJson(JSONObject(response.toString()), true)
            val accepted = response.getJSONArray("frames").getJSONObject(count - 1)
            assertEquals(accepted.getString("role"), restarted.roleFor(accepted.getString("digest")))
        }
        assertThrows(IllegalArgumentException::class.java) { CalibrationGuide.fromJson(fixture(), true).roleFor("f".repeat(64)) }
    }

    @Test fun neverIssuesGuidedActionsForLegacyClosedOrUnavailableSessions() {
        val manual = CalibrationGuide.fromJson(snapshot(1), false)
        assertNull(manual.nextRole); assertFalse(manual.ready)
        assertThrows(IllegalArgumentException::class.java) { manual.roleFor("f".repeat(64)) }
        for (state in listOf("QUEUED", "PROCESSING", "COMPLETE", "UNAVAILABLE", "ERROR")) {
            val progress = CalibrationGuide.fromJson(snapshot(50, state), true)
            assertNull(progress.nextRole); assertFalse(progress.ready)
        }
    }

    @Test fun rejectsAmbiguousOrMalformedFrameLists() {
        for (role in listOf("enrollment", "genuine")) {
            val value = snapshot(1)
            value.getJSONArray("frames").put(JSONObject(value.getJSONArray("frames").getJSONObject(0).toString()).put("role", role))
            assertThrows(IllegalArgumentException::class.java) { CalibrationGuide.fromJson(value, true) }
        }
        val bad = snapshot(1); bad.getJSONArray("frames").getJSONObject(0).put("digest", "invalid")
        assertThrows(IllegalArgumentException::class.java) { CalibrationGuide.fromJson(bad, true) }
    }
}
