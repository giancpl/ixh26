package com.example.photoanchor

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Hardware AES wrapping for software ML-DSA/Ed25519 keys. No native PQ hardware claim. */
class HardwareHybridVault(context: Context) {
    private val app = context.applicationContext
    private val installation = InstallationKeys(app)
    private val directory = File(app.noBackupFilesDir, "fingerprinto-hybrid-v2")
    private val record get() = AtomicFile(File(directory, "keys.bin"))

    class Evidence internal constructor(val publicKey: HybridSoftwareKeys.Public, binding: ByteArray) {
        private val signature = binding.copyOf()
        val hardwareBindingSignature: ByteArray get() = signature.copyOf()
    }

    /** Requires an already provisioned hardware installation key; does not accept enrollment. */
    fun create(requireStrongBox: Boolean = false): Evidence = locked {
        val hardware = installation.load()
        val store = store()
        check(!store.containsAlias(ALIAS) && directory.list()?.any { it.startsWith("keys.bin") } != true) {
            "Hybrid key state exists or needs explicit recovery"
        }
        if (requireStrongBox) check(app.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
        HybridSoftwareKeys.generate(hardware.publicKey).use { material ->
            val public = material.publicKey()
            val binding = installation.signRoot(hardware.installationId, public.bindingRoot())
            public.verifyBinding(binding)
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setIsStrongBoxBacked(requireStrongBox).build())
            val key = generator.generateKey()
            requireHardware(key)
            val payload = material.privatePayload()
            val sealed = try {
                HybridKeyEnvelope.seal(key, payload, public.deviceId(), hardware.installationId, binding)
            } finally { payload.fill(0) }
            val output = record.startWrite()
            try { output.write(sealed); record.finishWrite(output) }
            catch (error: Throwable) { record.failWrite(output); throw error }
            Evidence(public, binding)
        }
    }

    fun loadEvidence(expectedHybridDeviceId: ByteArray): Evidence = withMaterial(expectedHybridDeviceId) { material, binding ->
        Evidence(material.publicKey(), binding)
    }

    /** Both signatures are required. Caller persists their exact bytes once and reuses on retry. */
    fun signRoot(expectedHybridDeviceId: ByteArray, root: ByteArray): HybridSoftwareKeys.Signatures =
        withMaterial(expectedHybridDeviceId) { material, _ -> material.sign(root) }

    private fun <T> withMaterial(expected: ByteArray, operation: (HybridSoftwareKeys, ByteArray) -> T): T = locked {
        require(expected.size == 32)
        val hardware = installation.load()
        val key = store().getKey(ALIAS, null) as? SecretKey ?: error("Hardware wrapping key lost")
        requireHardware(key)
        val sealed = record.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                check(output.size() + count <= HybridKeyEnvelope.MAX_SIZE) { "Wrapped key record exceeds limits" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        HybridKeyEnvelope.open(key, sealed, expected, hardware.installationId).use { opened ->
            HybridSoftwareKeys.fromPrivatePayload(opened.payload()).use { material ->
                check(java.security.MessageDigest.isEqual(expected, material.publicKey().deviceId()))
                material.publicKey().verifyBinding(opened.binding())
                operation(material, opened.binding())
            }
        }
    }

    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Suppress("DEPRECATION")
    private fun requireHardware(key: SecretKey) {
        val info = SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        check(info.isInsideSecureHardware) { "Software-only wrapping keys are unsupported" }
    }
    private fun <T> locked(operation: () -> T): T = synchronized(PROCESS_LOCK) {
        check(directory.isDirectory || directory.mkdirs())
        RandomAccessFile(File(directory, "vault.lock"), "rw").use { file ->
            file.channel.lock().use { operation() }
        }
    }
    private companion object {
        const val ALIAS = "fingerprinto.hybrid.v2.aes"
        val PROCESS_LOCK = Any()
    }
}
