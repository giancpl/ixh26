package com.example.photoanchor

import org.nostrdevkit.sdk.Event

/** The official SDK verifies the NIP-01 event identifier and Schnorr signature. */
object NostrBinding {
    fun verify(json: String): Boolean = runCatching {
        Event.fromJson(json).use { it.verify() }
    }.getOrDefault(false)
}
