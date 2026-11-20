package com.example.photoanchor;

import java.math.BigInteger;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/** Raw signing primitive. Origin and enrollment authorization belong to the caller. */
public final class InstallationSignature {
    private static final byte[] PREFIX = {
        0x30,0x59,0x30,0x13,0x06,0x07,0x2a,(byte)0x86,0x48,(byte)0xce,0x3d,0x02,0x01,
        0x06,0x08,0x2a,(byte)0x86,0x48,(byte)0xce,0x3d,0x03,0x01,0x07,0x03,0x42,0x00,0x04
    };
    private static final BigInteger ORDER = new BigInteger(
        "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);

    private InstallationSignature() {}

    public static PublicKey publicKey(byte[] spki) throws GeneralSecurityException {
        if (spki == null || spki.length != 91 || !Arrays.equals(PREFIX, Arrays.copyOf(spki, PREFIX.length)))
            throw new InvalidKeyException("Expected exact v1 P-256 SPKI");
        BigInteger prime = new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16);
        BigInteger b = new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16);
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(spki, 27, 59));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(spki, 59, 91));
        if (x.compareTo(prime) >= 0 || y.compareTo(prime) >= 0
                || !y.multiply(y).mod(prime).equals(x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(b).mod(prime)))
            throw new InvalidKeyException("Invalid P-256 point");
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki.clone()));
    }

    public static void validateDer(byte[] signature) throws SignatureException {
        if (signature == null || signature.length < 8 || signature.length > 72
                || signature[0] != 0x30 || (signature[1] & 255) != signature.length - 2)
            throw new SignatureException("Invalid signature DER sequence");
        int offset = 2;
        for (int i = 0; i < 2; i++) {
            if (offset + 2 > signature.length || signature[offset++] != 2)
                throw new SignatureException("Expected DER INTEGER");
            int length = signature[offset++] & 255;
            if (length < 1 || length > 33 || offset + length > signature.length
                    || (signature[offset] & 128) != 0
                    || (length > 1 && signature[offset] == 0 && (signature[offset + 1] & 128) == 0))
                throw new SignatureException("Invalid DER INTEGER encoding");
            BigInteger scalar = new BigInteger(1, Arrays.copyOfRange(signature, offset, offset + length));
            if (scalar.signum() <= 0 || scalar.compareTo(ORDER) >= 0)
                throw new SignatureException("P-256 scalar out of range");
            offset += length;
        }
        if (offset != signature.length) throw new SignatureException("Trailing signature bytes");
    }

    public static byte[] sign(PrivateKey privateKey, byte[] spki, byte[] root) throws GeneralSecurityException {
        byte[] message = root(root);
        // Validate the public profile before invoking the installation key.
        publicKey(spki);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(message);
        byte[] signature = signer.sign();
        verify(spki, message, signature);
        return signature; // Preserve original DER bytes and s representation.
    }

    public static void verify(byte[] spki, byte[] root, byte[] signature) throws GeneralSecurityException {
        byte[] message = root(root);
        validateDer(signature);
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey(spki));
        verifier.update(message);
        if (!verifier.verify(signature)) throw new SignatureException("Signature mismatch");
    }

    private static byte[] root(byte[] value) throws SignatureException {
        if (value == null || value.length != 32) throw new SignatureException("Expected 32 raw bytes of R");
        return value.clone();
    }
}
