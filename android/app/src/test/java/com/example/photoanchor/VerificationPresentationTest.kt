package com.example.photoanchor

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class VerificationPresentationTest {
    @Test fun checkingAndMissingResultsNeverClaimVerification() {
        for (message in listOf(null, "", "Not checked", "Checking Bitcoin status…")) {
            val badge = timestampBadge(message)
            assertEquals(VerificationTone.NEUTRAL, badge.tone)
            assertEquals("Timestamp not checked", badge.label)
        }
    }

    @Test fun unavailableAndUnsubmittedEvidenceRemainDistinctFromPendingAndConfirmation() {
        val cases = mapOf(
            "UNAVAILABLE — no consistent fresh local node report" to "Timestamp unavailable",
            "UNVERIFIABLE — local Bitcoin node cannot verify this proof" to "Timestamp unavailable",
            "NOT SUBMITTED — no settlement result yet" to "Timestamp not submitted",
            "Not requested" to "Timestamp not requested",
        )
        for ((message, label) in cases) {
            assertEquals(TimestampBadge(label, VerificationTone.NEUTRAL), timestampBadge(message))
        }
    }

    @Test fun pendingAndFailuresNeverGetAConfirmationBadge() {
        assertEquals(VerificationTone.PENDING, timestampBadge("PENDING — awaiting mining").tone)
        for (message in listOf("INVALID — rejected", "ERROR — operation unavailable")) {
            assertEquals(VerificationTone.FAILURE, timestampBadge(message).tone)
        }
    }

    @Test fun confirmationAlwaysIdentifiesTheDevelopmentChainAndVerificationSource() {
        val badge = timestampBadge("CONFIRMED — verified by local node — regtest\nBlock 102 · 2026-01-01T00:00:00Z")
        assertEquals(VerificationTone.SUCCESS, badge.tone)
        assertTrue(badge.label.contains("regtest"))
        assertTrue(badge.label.contains("Local node"))
        assertFalse(badge.label.contains("Authentic"))
    }

    @Test fun unfamiliarTokensAndEmbeddedSuccessWordsFailClosed() {
        for (message in listOf("CONFIRMEDISH", "VALIDATED", "error: CONFIRMED", "UNAVAILABLE — CONFIRMED")) {
            assertEquals(VerificationTone.NEUTRAL, verificationTone(message))
            assertEquals(VerificationTone.NEUTRAL, timestampBadge(message).tone)
        }
        // A valid signature or a published post is not a verified timestamp.
        for (message in listOf("VALID — Schnorr signature", "PUBLISHED")) {
            assertEquals(VerificationTone.SUCCESS, verificationTone(message))
            assertEquals(VerificationTone.NEUTRAL, timestampBadge(message).tone)
        }
    }

    @Test fun allFourChecksKeepTheirOwnResult() {
        val checks = listOf("VALID — ID and signature", "INVALID — JPEG differs", "PENDING — mining", "PUBLISHED")
        assertEquals(listOf(VerificationTone.SUCCESS, VerificationTone.FAILURE, VerificationTone.PENDING,
            VerificationTone.SUCCESS), checks.map(::verificationTone))
        assertEquals(VerificationTone.FAILURE, verificationTone("ERROR — export unavailable"))
        assertEquals(VerificationTone.NEUTRAL, verificationTone("UNAVAILABLE — download failed"))
    }

    @Test fun statusParsingDoesNotDependOnTheDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(VerificationTone.FAILURE, verificationTone("invalid — JPEG differs"))
            assertEquals(VerificationTone.PENDING, timestampBadge("pending — mining").tone)
        } finally {
            Locale.setDefault(original)
        }
    }
}
