package com.example.photoanchor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** FingerprinTO v1 byte construction. Does not sign or accept origin/enrollment. */
public final class ReceiptV1 {
    private final byte[] key, signer, challenge;
    private final byte[][] chain;
    private final String packageName, manufacturer, model;
    private final long version, localTime;
    private final int placement;

    public ReceiptV1(byte[] key, byte[][] chain, String packageName, long version,
                     byte[] signer, String manufacturer, String model, long localTime,
                     byte[] challenge, int placement) {
        this.key = sized(key, 91, 91);
        this.signer = sized(signer, 32, 32);
        this.challenge = challenge == null ? null : sized(challenge, 32, 32);
        if (chain == null || chain.length < 1 || chain.length > 8) fail("Invalid chain length");
        this.chain = new byte[chain.length][];
        for (int i = 0; i < chain.length; i++) this.chain[i] = sized(chain[i], 1, 16384);
        this.packageName = text(packageName);
        this.manufacturer = text(manufacturer);
        this.model = text(model);
        if (version < 0 || (placement != 0 && placement != 1)) fail("Invalid version or placement");
        this.version = version;
        this.localTime = localTime;
        this.placement = placement;
    }

    /** Hash immutable snapshots. Streams are consumed but remain owned by caller. */
    public Map<String, byte[]> roots(InputStream asset, InputStream credential) throws IOException {
        if (asset == null || (placement == 0 && credential != null)
                || (placement == 1 && credential == null)) fail("Missing or conflicting evidence");
        Map<String, byte[]> r = new LinkedHashMap<>();
        r.put("SHA256_J", hashStream(asset, 128L * 1024 * 1024, false));
        r.put("ID_I", hash(key));
        r.put("F_I", d("install", r.get("ID_I"), packageName, version, signer));
        if (credential != null) {
            r.put("SHA256_A", hashStream(credential, 16L * 1024 * 1024, true));
            r.put("F_P", d("photo", r.get("F_I"), r.get("SHA256_J"), r.get("SHA256_A")));
        } else {
            r.put("F_P", d("photo", r.get("F_I"), r.get("SHA256_J")));
        }
        String[] names = {"ID_I", "manufacturer", "model", "localTime", "sessionChallenge"};
        Object[] values = {r.get("ID_I"), manufacturer, model, localTime, challenge};
        for (int i = 0; i < names.length; i++) r.put("L" + i, d("field", names[i], values[i]));
        r.put("N01", node(r.get("L0"), r.get("L1")));
        r.put("N23", node(r.get("L2"), r.get("L3")));
        r.put("N0123", node(r.get("N01"), r.get("N23")));
        r.put("F_D", node(r.get("N0123"), r.get("L4")));
        r.put("N_IP", node(r.get("F_I"), r.get("F_P")));
        r.put("T", node(r.get("N_IP"), r.get("F_D")));
        r.put("R", d("fingerprinto/v1", r.get("T")));
        return r;
    }

    /** Encode a supplied signature verbatim. Length/schema checks are not signature verification. */
    public byte[] receipt(byte[] signature) {
        byte[] sigma = sized(signature, 8, 72);
        return encode(new Object[]{1L, key, chain, packageName, version, signer,
                manufacturer, model, localTime, challenge, (long) placement, sigma});
    }

    /** C is an anchor payload; OTS must hash these 32 bytes once as a file. */
    public static byte[] commitment(byte[] root, byte[] signature) {
        return d("receipt", sized(root, 32, 32), sized(signature, 8, 72));
    }

    public static byte[] hash(byte[] data) {
        return sha256().digest(data);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] hashStream(InputStream input, long limit, boolean nonempty) throws IOException {
        MessageDigest digest = sha256();
        byte[] buffer = new byte[65536];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IOException("Evidence exceeds profile limit");
            digest.update(buffer, 0, count);
        }
        if (nonempty && total == 0) throw new IOException("Empty detached credential");
        return digest.digest();
    }

    private static byte[] node(byte[] left, byte[] right) { return d("node", left, right); }

    private static byte[] d(String label, Object... fields) {
        Object[] items = new Object[fields.length + 1];
        items[0] = label;
        System.arraycopy(fields, 0, items, 1, fields.length);
        return hash(encode(items));
    }

    private static byte[] sized(byte[] value, int min, int max) {
        if (value == null || value.length < min || value.length > max) fail("Invalid binary field length");
        return value.clone();
    }

    private static byte[] utf8(String value) {
        if (value == null) throw new IllegalArgumentException("Missing text");
        try {
            ByteBuffer bytes = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(java.nio.CharBuffer.wrap(value));
            byte[] result = new byte[bytes.remaining()];
            bytes.get(result);
            return result;
        } catch (CharacterCodingException invalid) { throw new IllegalArgumentException("Invalid Unicode", invalid); }
    }

    private static String text(String value) {
        int size = utf8(value).length;
        if (size < 1 || size > 255) fail("Invalid text length");
        return value;
    }

    private static byte[] encode(Object value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, value);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, Object value) {
        if (value == null) out.write(0xf6);
        else if (value instanceof Long) {
            long n = (Long) value;
            head(out, n < 0 ? 1 : 0, n < 0 ? -1 - n : n);
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            head(out, 2, bytes.length);
            out.write(bytes, 0, bytes.length);
        } else if (value instanceof String) {
            byte[] bytes = utf8((String) value);
            head(out, 3, bytes.length);
            out.write(bytes, 0, bytes.length);
        } else if (value instanceof Object[]) {
            Object[] items = (Object[]) value;
            head(out, 4, items.length);
            for (Object item : items) write(out, item);
        } else fail("Unsupported CBOR value");
    }

    private static void head(ByteArrayOutputStream out, int major, long value) {
        if (value < 24) { out.write((major << 5) | (int) value); return; }
        int bytes = value <= 0xffL ? 1 : value <= 0xffffL ? 2 : value <= 0xffffffffL ? 4 : 8;
        out.write((major << 5) | (bytes == 1 ? 24 : bytes == 2 ? 25 : bytes == 4 ? 26 : 27));
        for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) out.write((int) (value >>> shift) & 255);
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
