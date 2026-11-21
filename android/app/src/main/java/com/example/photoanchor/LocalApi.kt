package com.example.photoanchor

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApiFailure(val code: String) : Exception("Local service request failed")

/** Deliberately restricted to the USB-forwarded archive; no arbitrary event URLs. */
class LocalApi(val baseUrl: String = BuildConfig.API_URL) {
    private fun connection(path: String, method: String): HttpURLConnection {
        require(BuildConfig.DEBUG && baseUrl == "http://127.0.0.1:8080")
        require(path.startsWith("/v1/") && !path.contains(".."))
        return (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun response(connection: HttpURLConnection, limit: Int): ByteArray {
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val code = runCatching {
                    connection.errorStream?.use { stream ->
                        JSONObject(readBounded(stream, 16 * 1024).toString(Charsets.UTF_8))
                            .getJSONObject("error").getString("code")
                    }
                }.getOrNull() ?: "http_$status"
                throw ApiFailure(code)
            }
            return connection.inputStream.use { readBounded(it, limit) }
        } finally { connection.disconnect() }
    }

    fun authorized(keys: org.nostrdevkit.sdk.Keys, method: String, path: String,
                   body: ByteArray = byteArrayOf(), binary: Boolean = false): JSONObject {
        val tags = listOf(listOf("u", baseUrl + path), listOf("method", method),
            listOf("payload", PhotoProtocol.sha256(body)), listOf("nonce", java.util.UUID.randomUUID().toString()))
            .map { org.nostrdevkit.sdk.Tag.parse(it) }
        val auth = org.nostrdevkit.sdk.EventBuilder(org.nostrdevkit.sdk.Kind(27235u), "").tags(tags)
            .finalize(keys).use { android.util.Base64.encodeToString(it.asJson().toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP) }
        val c = connection(path, method)
        c.setRequestProperty("Authorization", "Nostr $auth")
        if (method != "GET") {
            c.doOutput = true
            c.setRequestProperty("Content-Type", if (binary) "application/octet-stream" else "application/json")
            c.setFixedLengthStreamingMode(body.size)
            try { c.outputStream.use { it.write(body) } } catch (failure: Exception) { c.disconnect(); throw failure }
        }
        return StrictJson.parseObject(response(c, 4 * 1024 * 1024), 4 * 1024 * 1024)
    }

    fun verifyEvidence(post: PhotoPost): JSONObject = json("/v1/posts/${post.id}/evidence/verify", JSONObject())

    fun evidenceProof(post: PhotoPost): ByteArray = response(
        connection("/v1/posts/${post.id}/evidence/proof", "GET"), 2 * 1024 * 1024)

    fun evidenceBundle(post: PhotoPost): ByteArray = response(
        connection("/v1/posts/${post.id}/evidence/bundle", "GET"), 48 * 1024 * 1024)

    fun upload(file: File): JSONObject {
        require(file.length() in 1..PhotoProtocol.MAX_PHOTO_BYTES.toLong())
        val connection = connection("/v1/media", "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "image/jpeg")
        connection.setFixedLengthStreamingMode(file.length())
        try { connection.outputStream.use { output -> file.inputStream().use { it.copyTo(output) } } }
        catch (failure: Exception) { connection.disconnect(); throw failure }
        return JSONObject(response(connection, 16 * 1024).toString(Charsets.UTF_8))
    }

    fun submit(event: String): JSONObject = json("/v1/posts", JSONObject().put("event", JSONObject(event)))

    fun post(id: String, verify: Boolean = false): JSONObject {
        require(Regex("[0-9a-f]{64}").matches(id))
        return if (verify) json("/v1/posts/$id/verify", JSONObject())
        else JSONObject(response(connection("/v1/posts/$id", "GET"), 64 * 1024).toString(Charsets.UTF_8))
    }

    private fun json(path: String, body: JSONObject): JSONObject {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val connection = connection(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(bytes.size)
        try { connection.outputStream.use { it.write(bytes) } }
        catch (failure: Exception) { connection.disconnect(); throw failure }
        return JSONObject(response(connection, 64 * 1024).toString(Charsets.UTF_8))
    }

    fun photo(post: PhotoPost): ByteArray {
        require(post.photoUrl == "$baseUrl/v1/media/${post.photoHash}")
        return response(connection("/v1/media/${post.photoHash}", "GET"), PhotoProtocol.MAX_PHOTO_BYTES)
    }

    fun proof(post: PhotoPost): ByteArray = response(connection("/v1/posts/${post.id}/proof", "GET"), 2 * 1024 * 1024)

    fun bundle(post: PhotoPost): ByteArray = response(connection("/v1/posts/${post.id}/bundle", "GET"), 24 * 1024 * 1024)
}

internal fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "Response exceeds the size limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
