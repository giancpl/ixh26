package com.example.photoanchor;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.MLDSAKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.signers.MLDSASigner;

/** Experimental v2 software signatures. Wrap private payload with hardware AES before persistence. */
public final class HybridSoftwareKeys implements AutoCloseable {
    public static final String SUITE = "fingerprinto/v2/ml-dsa-65+ed25519";
    public static final int PRIVATE_PAYLOAD_SIZE = 6143;
    private final Public publicKey;
    private final byte[] edSeed, mlSecret;
    private boolean closed;

    public static final class Public {
        private final byte[] hardware, ed, ml;
        public Public(byte[] hardware, byte[] ed, byte[] ml) throws GeneralSecurityException {
            InstallationSignature.publicKey(hardware);
            this.hardware = exact(hardware, 91); this.ed = exact(ed, 32); this.ml = exact(ml, 1952);
        }
        public byte[] encoded() { return ReceiptV1.protocolEncode(new Object[]{2L, SUITE, hardware, ed, ml}); }
        public byte[] hardwareSpki() { return hardware.clone(); }
        public byte[] deviceId() { return ReceiptV1.protocolHash("fingerprinto/hybrid-device/v2", encoded()); }
        public byte[] bindingRoot() { return ReceiptV1.protocolHash("fingerprinto/device-binding/v2", SUITE, encoded()); }
        public void verifyBinding(byte[] binding) throws GeneralSecurityException {
            InstallationSignature.verify(hardware, bindingRoot(), binding);
        }
        private byte[] transcript(byte[] root) {
            return ReceiptV1.protocolEncode(new Object[]{"fingerprinto/hybrid-signature/v2", SUITE, deviceId(), exact(root, 32)});
        }
        public void verify(byte[] expectedDeviceId, byte[] root, Signatures signatures) throws GeneralSecurityException {
            if (!java.security.MessageDigest.isEqual(deviceId(), exact(expectedDeviceId, 32)))
                throw new SignatureException("Hybrid device differs from enrollment");
            byte[] message = transcript(root);
            Ed25519Signer edSigner = new Ed25519Signer();
            edSigner.init(false, new Ed25519PublicKeyParameters(ed, 0));
            edSigner.update(message, 0, message.length);
            if (!edSigner.verifySignature(signatures.ed)) throw new SignatureException("Ed25519 mismatch");
            MLDSASigner mlSigner = new MLDSASigner();
            mlSigner.init(false, new MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, ml));
            mlSigner.update(message, 0, message.length);
            if (!mlSigner.verifySignature(signatures.ml)) throw new SignatureException("ML-DSA mismatch");
        }
    }

    public static final class Signatures {
        private final byte[] ed, ml;
        public Signatures(byte[] ed, byte[] ml) { this.ed = exact(ed, 64); this.ml = exact(ml, 3309); }
        public byte[] encoded() { return ReceiptV1.protocolEncode(new Object[]{2L, SUITE, ed, ml}); }
    }

    private HybridSoftwareKeys(Public publicKey, byte[] edSeed, byte[] mlSecret) {
        this.publicKey = publicKey; this.edSeed = exact(edSeed, 32); this.mlSecret = exact(mlSecret, 4032);
    }

    public static HybridSoftwareKeys generate(byte[] hardwareSpki) throws GeneralSecurityException {
        SecureRandom random = new SecureRandom();
        Ed25519PrivateKeyParameters ed = new Ed25519PrivateKeyParameters(random);
        MLDSAKeyPairGenerator generator = new MLDSAKeyPairGenerator();
        generator.init(new MLDSAKeyGenerationParameters(random, MLDSAParameters.ml_dsa_65));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        MLDSAPrivateKeyParameters secret = (MLDSAPrivateKeyParameters) pair.getPrivate();
        byte[] raw = secret.getEncoded();
        try {
            return new HybridSoftwareKeys(new Public(hardwareSpki, ed.generatePublicKey().getEncoded(),
                ((MLDSAPublicKeyParameters)pair.getPublic()).getEncoded()), ed.getEncoded(), raw);
        } finally { Arrays.fill(raw, (byte)0); secret.destroy(); }
    }

    public Public publicKey() { return publicKey; }

    public synchronized Signatures sign(byte[] root) throws GeneralSecurityException {
        checkOpen();
        byte[] message = publicKey.transcript(root);
        Ed25519Signer ed = new Ed25519Signer();
        ed.init(true, new Ed25519PrivateKeyParameters(edSeed, 0));
        ed.update(message, 0, message.length);
        MLDSAPrivateKeyParameters mlKey = new MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, mlSecret);
        try {
            MLDSASigner ml = new MLDSASigner();
            ml.init(true, new ParametersWithRandom(mlKey, new SecureRandom()));
            ml.update(message, 0, message.length);
            Signatures result = new Signatures(ed.generateSignature(), ml.generateSignature());
            publicKey.verify(publicKey.deviceId(), root, result);
            return result;
        } catch (org.bouncycastle.crypto.CryptoException error) {
            throw new SignatureException("Hybrid signature failed", error);
        } finally { mlKey.destroy(); }
    }

    /** Sensitive internal transport: never write these plaintext bytes to disk. */
    public synchronized byte[] privatePayload() {
        checkOpen();
        byte[] result = new byte[PRIVATE_PAYLOAD_SIZE];
        int offset = 0;
        for (byte[] part : new byte[][]{{0x46,0x50,0x48,0x02}, publicKey.hardware, publicKey.ed, publicKey.ml, edSeed, mlSecret}) {
            System.arraycopy(part, 0, result, offset, part.length); offset += part.length;
        }
        return result;
    }

    public static HybridSoftwareKeys fromPrivatePayload(byte[] payload) throws GeneralSecurityException {
        if (payload == null || payload.length != PRIVATE_PAYLOAD_SIZE || payload[0] != 0x46 || payload[1] != 0x50
                || payload[2] != 0x48 || payload[3] != 2) throw new IllegalArgumentException("Invalid private key payload");
        Public pub = new Public(Arrays.copyOfRange(payload, 4, 95), Arrays.copyOfRange(payload, 95, 127), Arrays.copyOfRange(payload, 127, 2079));
        HybridSoftwareKeys result = new HybridSoftwareKeys(pub, Arrays.copyOfRange(payload, 2079, 2111), Arrays.copyOfRange(payload, 2111, 6143));
        try { result.sign(new byte[32]); return result; }
        catch (GeneralSecurityException | RuntimeException error) { result.close(); throw error; }
    }

    private void checkOpen() { if (closed) throw new IllegalStateException("Hybrid keys closed"); }
    public synchronized void close() { Arrays.fill(edSeed, (byte)0); Arrays.fill(mlSecret, (byte)0); closed = true; }
    private static byte[] exact(byte[] value, int size) {
        if (value == null || value.length != size) throw new IllegalArgumentException("Invalid hybrid field size");
        return value.clone();
    }
}
