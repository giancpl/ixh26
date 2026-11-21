package com.example.photoanchor;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Authenticated wrapping format; Android adapter requires a hardware Keystore AES key. */
public final class HybridKeyEnvelope {
    public static final int MAX_SIZE = 6400;
    private static final byte[] MAGIC = {0x46,0x50,0x56,0x02};
    private HybridKeyEnvelope() {}

    private static byte[] aad(byte[] device, byte[] hardware, byte[] binding) {
        if (device.length != 32 || hardware.length != 32) throw new IllegalArgumentException("Invalid ID size");
        return ReceiptV1.protocolEncode(new Object[]{"fingerprinto/hardware-wrapped-keys/v2",
                HybridSoftwareKeys.SUITE, device, hardware, binding});
    }

    public static byte[] seal(SecretKey key, byte[] payload, byte[] device, byte[] hardware, byte[] binding)
            throws GeneralSecurityException {
        if (payload.length != HybridSoftwareKeys.PRIVATE_PAYLOAD_SIZE) throw new IllegalArgumentException("Invalid payload");
        InstallationSignature.validateDer(binding);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key); // Provider generates a fresh nonce.
        cipher.updateAAD(aad(device, hardware, binding));
        byte[] ciphertext = cipher.doFinal(payload);
        byte[] iv = cipher.getIV();
        if (iv.length != 12) throw new GeneralSecurityException("Unexpected AES-GCM nonce size");
        return ByteBuffer.allocate(4 + 32 + 32 + 1 + binding.length + 12 + ciphertext.length)
                .put(MAGIC).put(device).put(hardware).put((byte)binding.length).put(binding).put(iv).put(ciphertext).array();
    }

    public static final class Opened implements AutoCloseable {
        private final byte[] payload, binding;
        private Opened(byte[] payload, byte[] binding) { this.payload = payload; this.binding = binding; }
        public byte[] payload() { return payload; } // Sensitive scoped buffer; erased by close().
        public byte[] binding() { return binding.clone(); }
        public void close() { Arrays.fill(payload, (byte)0); }
    }

    public static Opened open(SecretKey key, byte[] record, byte[] expectedDevice, byte[] expectedHardware)
            throws GeneralSecurityException {
        if (record.length < 6248 || record.length > MAX_SIZE) throw new GeneralSecurityException("Invalid wrapped record size");
        ByteBuffer input = ByteBuffer.wrap(record);
        byte[] magic = new byte[4], device = new byte[32], hardware = new byte[32];
        input.get(magic).get(device).get(hardware);
        if (!Arrays.equals(magic, MAGIC) || !java.security.MessageDigest.isEqual(device, expectedDevice)
                || !java.security.MessageDigest.isEqual(hardware, expectedHardware))
            throw new GeneralSecurityException("Wrapped key identity mismatch");
        int size = input.get() & 255;
        if (size < 8 || size > 72 || input.remaining() != size + 12 + HybridSoftwareKeys.PRIVATE_PAYLOAD_SIZE + 16)
            throw new GeneralSecurityException("Invalid wrapped record fields");
        byte[] binding = new byte[size], iv = new byte[12], ciphertext = new byte[HybridSoftwareKeys.PRIVATE_PAYLOAD_SIZE + 16];
        input.get(binding).get(iv).get(ciphertext);
        InstallationSignature.validateDer(binding);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad(device, hardware, binding));
        return new Opened(cipher.doFinal(ciphertext), binding);
    }
}
