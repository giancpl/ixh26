package com.example.photoanchor

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import org.nostrdevkit.sdk.Keys
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try {
        stream.write(text.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(stream)
    } catch (failure: Exception) {
        atomic.failWrite(stream)
        throw failure
    }
}

/** The private scalar exists only in memory and this AES-GCM encrypted no-backup file. */
class IdentityStore(private val context: Context) {
    private val encrypted = File(context.noBackupFilesDir, "identity.enc")
    private val profileFile = File(context.noBackupFilesDir, "profile.json")
    private val alias = "photoanchor.identity.v1"

    private fun wrappingKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        require(!encrypted.exists()) { "Identity wrapping key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    fun loadOrCreate(): Keys {
        val key = wrappingKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (encrypted.exists()) {
            val data = JSONObject(AtomicFile(encrypted).readFully().toString(Charsets.UTF_8))
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(data.getString("iv"), Base64.NO_WRAP)))
            val plain = cipher.doFinal(Base64.decode(data.getString("ciphertext"), Base64.NO_WRAP))
            return try { Keys.parse(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        }
        return Keys.generate().also { keys ->
            val plain = keys.secretKey().use { it.toHex().toByteArray(Charsets.UTF_8) }
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val data = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                    .put("ciphertext", Base64.encodeToString(cipher.doFinal(plain), Base64.NO_WRAP))
                atomicWrite(encrypted, data.toString())
            } finally { plain.fill(0) }
        }
    }

    fun name(): String = if (profileFile.exists()) JSONObject(profileFile.readText()).optString("name") else ""

    fun saveName(name: String) {
        require(name.isNotBlank() && name.length <= 80)
        atomicWrite(profileFile, JSONObject().put("name", name.trim()).toString())
    }
}

/** Durable, already-signed events. Retries never reconstruct or re-sign an event. */
class Outbox(context: Context) {
    private val directory = File(context.noBackupFilesDir, "outbox").apply { mkdirs() }

    fun enqueue(json: String) {
        val id = JSONObject(json).getString("id")
        require(Regex("[0-9a-f]{64}").matches(id))
        val target = File(directory, "$id.json")
        if (target.exists()) require(target.readText() == json) else atomicWrite(target, json)
    }

    fun pending(): List<File> = directory.listFiles()?.filter { it.name.matches(Regex("[0-9a-f]{64}\\.json")) }
        ?.sortedBy { it.name } ?: emptyList()

    fun acknowledge(file: File) { require(file.parentFile == directory); AtomicFile(file).delete() }
}
