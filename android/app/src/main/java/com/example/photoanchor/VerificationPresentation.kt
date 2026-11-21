package com.example.photoanchor

import java.util.Locale

/** Presentation of repository results, never a substitute for protocol verification. */
internal enum class VerificationTone(val badgeKind: String) {
    SUCCESS("ok"), FAILURE("err"), PENDING("pending"), NEUTRAL("default")
}

internal data class TimestampBadge(val label: String, val tone: VerificationTone)

// Repository messages start with an exact status token, optionally followed by
// an em dash and explanation. Fail closed when an unfamiliar message is added.
private fun statusToken(value: String?): String =
    value.orEmpty().trim().substringBefore(" —").uppercase(Locale.ROOT)

internal fun verificationTone(value: String): VerificationTone = when (statusToken(value)) {
    "VALID", "CONFIRMED", "PUBLISHED" -> VerificationTone.SUCCESS
    "INVALID", "ERROR" -> VerificationTone.FAILURE
    "PENDING", "QUEUED" -> VerificationTone.PENDING
    else -> VerificationTone.NEUTRAL
}

internal fun timestampBadge(value: String?): TimestampBadge = when (statusToken(value)) {
    "CONFIRMED" -> TimestampBadge("Local node verified · regtest", VerificationTone.SUCCESS)
    "PENDING" -> TimestampBadge("Regtest timestamp pending", VerificationTone.PENDING)
    "INVALID" -> TimestampBadge("Timestamp invalid", VerificationTone.FAILURE)
    "ERROR" -> TimestampBadge("Timestamp operation failed", VerificationTone.FAILURE)
    "UNVERIFIABLE", "UNAVAILABLE" -> TimestampBadge("Timestamp unavailable", VerificationTone.NEUTRAL)
    "NOT SUBMITTED" -> TimestampBadge("Timestamp not submitted", VerificationTone.NEUTRAL)
    "NOT REQUESTED" -> TimestampBadge("Timestamp not requested", VerificationTone.NEUTRAL)
    else -> TimestampBadge("Timestamp not checked", VerificationTone.NEUTRAL)
}
