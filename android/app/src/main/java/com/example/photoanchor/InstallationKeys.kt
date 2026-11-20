package com.example.photoanchor

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec

/** Keystore mechanism only. No local boolean or key property accepts enrollment. */
class InstallationKeys(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "fingerprinto-installation")
    private val record get() = AtomicFile(File(directory, "enrollment.bin"))

    class Evidence internal constructor(key: ByteArray, challenge: ByteArray, chain: List<ByteArray>) {
        private val keyBytes = key.copyOf()
        private val challengeBytes = challenge.copyOf()
        private val certificates = chain.map { it.copyOf() }
        val publicKey: ByteArray get() = keyBytes.copyOf()
        val installationId: ByteArray get() = ReceiptV1.hash(keyBytes)
        val enrollmentChallenge: ByteArray get() = challengeBytes.copyOf()
        val certificateChain: List<ByteArray> get() = certificates.map { it.copyOf() }
    }

    /** The challenge must come from the independent enrollment verifier, not this phone. */
    fun provision(enrollmentChallenge: ByteArray, requireStrongBox: Boolean = false): Evidence = locked {
        require(enrollmentChallenge.size == 32) { "Expected external 32-byte enrollment challenge" }
        val challenge = enrollmentChallenge.copyOf()
        val store = store()
        check(!store.containsAlias(ALIAS) && directory.list()?.any { it.startsWith("enrollment.bin") } != true) {
            "Installation already provisioned or recovery required; never overwrite a key"
        }
        if (requireStrongBox) {
            check(app.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                "Required StrongBox unavailable"
            }
        }
        val specification = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .setIsStrongBoxBacked(requireStrongBox)
            .build()
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(specification)
        // No fallback after failure; any residual alias requires explicit recovery.
        val pair = generator.generateKeyPair()
        val key = pair.public.encoded
        InstallationSignature.publicKey(key)
        val evidence = evidence(store(), key, challenge)
        val output = record.startWrite()
        try {
            output.write(MAGIC)
            output.write(key)
            output.write(challenge)
            record.finishWrite(output)
        } catch (error: Throwable) {
            record.failWrite(output)
            throw error
        }
        evidence
    }

    /** Re-export the original enrollment evidence; does not mint a new challenge. */
    fun load(): Evidence = locked { loadLocked() }

    /** Low-level primitive; caller must validate origin/enrollment before receipt issuance.
     * Persist the returned signature exactly once; retries reuse it instead of signing again.
     */
    fun signRoot(expectedInstallationId: ByteArray, root: ByteArray): ByteArray = locked {
        require(expectedInstallationId.size == 32 && root.size == 32)
        val evidence = loadLocked()
        check(java.security.MessageDigest.isEqual(expectedInstallationId, evidence.installationId)) {
            "Installation key changed; new enrollment required"
        }
        val key = store().getKey(ALIAS, null) as? PrivateKey
            ?: error("Installation private key unavailable; new enrollment required")
        InstallationSignature.sign(key, evidence.publicKey, root)
    }

    private fun loadLocked(): Evidence {
        // noBackupFilesDir prevents stale active enrollment being restored by backup.
        val bytes = record.openRead().use { input ->
            val buffer = ByteArray(MAGIC.size + 91 + 32)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                check(read > 0) { "Incomplete enrollment record" }
                count += read
            }
            check(input.read() == -1) { "Trailing enrollment record bytes" }
            buffer
        }
        check(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Unknown enrollment record" }
        val key = bytes.copyOfRange(MAGIC.size, MAGIC.size + 91)
        InstallationSignature.publicKey(key)
        return evidence(store(), key, bytes.copyOfRange(MAGIC.size + 91, bytes.size))
    }

    private fun evidence(store: KeyStore, key: ByteArray, challenge: ByteArray): Evidence {
        check(store.containsAlias(ALIAS)) { "Installation key lost; new enrollment required" }
        val chain = store.getCertificateChain(ALIAS)?.map { it.encoded }
            ?: error("Missing enrollment certificate chain")
        check(chain.size in 1..8 && chain.all { it.size in 1..16384 }) { "Unsupported certificate chain" }
        check(store.getCertificate(ALIAS).publicKey.encoded.contentEquals(key)) {
            "Recorded SPKI differs from live key; new enrollment required"
        }
        return Evidence(key, challenge, chain)
    }

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun <T> locked(operation: () -> T): T = synchronized(PROCESS_LOCK) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create installation storage" }
        RandomAccessFile(File(directory, "installation.lock"), "rw").use { file ->
            file.channel.lock().use { operation() }
        }
    }

    private companion object {
        const val ALIAS = "fingerprinto.installation.v1"
        val PROCESS_LOCK = Any()
        val MAGIC = byteArrayOf(0x46, 0x50, 0x49, 0x01)
    }
}
