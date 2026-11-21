package com.example.photoanchor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/** v3 byte construction only. Findings/JSON digests are not authenticated policy. */
public final class ReceiptV3 {
    private final ReceiptV1 metadata;
    private final byte[] key, signer, challenge;
    private final byte[][] chain;
    private final String packageName, manufacturer, model;
    private final long version, time;
    private final Object[] evidence;
    private final int placement;

    public ReceiptV3(byte[] key, byte[][] chain, String packageName, long version,
                     byte[] signer, String manufacturer, String model, long time,
                     byte[] challenge, int placement, byte[] detachedHash,
                     int manufacturerResult, int prnuResult, byte[] refHash,
                     byte[] policyHash, byte[] reportHash) {
        metadata = new ReceiptV1(key, chain, packageName, version, signer,
                manufacturer, model, time, challenge, 0);
        if (placement < 0 || placement > 2 || manufacturerResult < 0 || manufacturerResult > 3
                || prnuResult < 0 || prnuResult > 3) fail();
        if ((placement == 1) != (detachedHash != null)) fail();
        if (prnuResult >= 2 && refHash == null) fail();
        if (placement == 2 && manufacturerResult >= 2) fail();
        this.key = key.clone(); this.signer = signer.clone();
        this.challenge = challenge == null ? null : challenge.clone();
        this.chain = new byte[chain.length][];
        for (int i = 0; i < chain.length; i++) this.chain[i] = chain[i].clone();
        this.packageName = packageName; this.version = version;
        this.manufacturer = manufacturer; this.model = model; this.time = time;
        this.placement = placement;
        evidence = new Object[]{(long) placement, optionalHash(detachedHash),
                (long) manufacturerResult, (long) prnuResult, optionalHash(refHash),
                hash(policyHash), hash(reportHash)};
    }

    public Map<String, byte[]> roots(InputStream asset, InputStream credential) throws IOException {
        if ((placement == 1) != (credential != null)) fail();
        Map<String, byte[]> r = metadata.roots(asset, null);
        if (credential != null) {
            byte[] a = ReceiptV1.protocolHashStream(credential, 16L * 1024 * 1024, true);
            if (!Arrays.equals(a, (byte[]) evidence[1])) fail();
            r.put("SHA256_A", a);
        }
        r.put("F_P", d("photo/evidence-v3", r.get("F_I"), r.get("SHA256_J"), evidence));
        r.put("N_IP", d("node", r.get("F_I"), r.get("F_P")));
        r.put("T", d("node", r.get("N_IP"), r.get("F_D")));
        r.put("R", d("fingerprinto/evidence-v3", r.get("T")));
        return r;
    }

    public byte[] receipt(byte[] signature) {
        metadata.receipt(signature); // inherited byte-size checks only
        return ReceiptV1.protocolEncode(new Object[]{3L, key, chain, packageName, version,
                signer, manufacturer, model, time, challenge, evidence, signature.clone()});
    }

    private static byte[] d(String label, Object... fields) { return ReceiptV1.protocolHash(label, fields); }
    private static byte[] optionalHash(byte[] b) { return b == null ? null : hash(b); }
    private static byte[] hash(byte[] b) { if (b == null || b.length != 32) fail(); return b.clone(); }
    private static void fail() { throw new IllegalArgumentException("Invalid v3 evidence"); }
}
