package com.example.photoanchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Post-capture operations. Computes canonical SHA-256 and capture metadata before anchoring. */
object PhotoProcessor {
    data class Result(
        val file: File,
        val sha256: String,
        val sizeBytes: Long,
        val capturedAtSec: Long = System.currentTimeMillis() / 1000L,
    )

    fun process(file: File): Result {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(8192)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return Result(
            file = file,
            sha256 = md.digest().joinToString("") { "%02x".format(it) },
            sizeBytes = file.length(),
        )
    }
}

/** Hook for the Bitcoin/OpenTimestamps anchoring service (Python backend + settlement/). */
interface Anchorer {
    suspend fun anchor(result: PhotoProcessor.Result): String
}

/**
 * Sends the exact captured JPEG bytes + SHA-256 to the PhotoAnchor Nostr web backend,
 * which stores the immutable image, pairs it to the user's Nostr inbox, and triggers
 * OpenTimestamps Bitcoin anchoring via settlement/.
 */
class WebBridgeAnchorer(
    private val serverUrl: String,
    private val pairingOrPubkey: String,
) : Anchorer {
    override suspend fun anchor(result: PhotoProcessor.Result): String = withContext(Dispatchers.IO) {
        val base = serverUrl.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "URL server non valido (usa http:// o https://)"
        }

        val boundary = "----PhotoAnchor${UUID.randomUUID()}"
        val url = URL("$base/api/photos")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8000
            readTimeout = 20000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(conn.outputStream).use { out ->
                fun writeField(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }

                writeField("sha256", result.sha256)
                writeField("source", "android-photoanchor")
                writeField("captured_at", result.capturedAtSec.toString())
                writeField("auto_anchor", "1")

                val target = pairingOrPubkey.trim()
                if (target.isNotEmpty()) {
                    if (target.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                        writeField("pubkey", target.lowercase())
                    } else {
                        writeField("pairing_code", target.uppercase())
                    }
                }

                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"${result.file.name}\"\r\n"
                )
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n")
                result.file.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = conn.responseCode
            val bodyText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                .orEmpty()

            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code: ${bodyText.take(120)}")
            }

            val json = JSONObject(bodyText)
            val anchor = json.optJSONObject("anchor")
            val status = anchor?.optString("status") ?: "OK"
            when (status) {
                "CONFIRMED" -> "Inviata al sito · Bitcoin CONFIRMED"
                "PENDING" -> "Inviata al sito · OTS PENDING"
                else -> "Inviata al sito · $status"
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        suspend fun ping(serverUrl: String): String = withContext(Dispatchers.IO) {
            val base = serverUrl.trim().trimEnd('/')
            val conn = (URL("$base/api/info").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val net = json.optString("network", "mainnet")
                "Connesso ($net)"
            } finally {
                conn.disconnect()
            }
        }
    }
}
