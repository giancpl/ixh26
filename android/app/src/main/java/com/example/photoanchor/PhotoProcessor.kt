package com.example.photoanchor

import java.io.File
import java.security.MessageDigest

/** Post-capture operations. Extend here (metadata, signing, manifest...). */
object PhotoProcessor {
    data class Result(val file: File, val sha256: String, val sizeBytes: Long)

    fun process(file: File): Result {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(8192)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return Result(file, md.digest().joinToString("") { "%02x".format(it) }, file.length())
    }
}

/** Hook for the existing Bitcoin/OpenTimestamps anchoring (Python service in ../settlement). */
interface Anchorer {
    suspend fun anchor(result: PhotoProcessor.Result): String
}
