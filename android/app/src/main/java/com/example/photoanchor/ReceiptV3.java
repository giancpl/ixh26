package com.example.photoanchor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/** v3 byte construction only. Findings/JSON digests are not authenticated policy. */
public final class ReceiptV3 {
    public static final class Decoded {
        public final ReceiptV3 profile;
        private final byte[] signature;
        private Decoded(ReceiptV3 profile,byte[] signature) {this.profile=profile;this.signature=signature.clone();}
        public byte[] signature() {return signature.clone();}
        public Map<String,byte[]> verify(InputStream asset,InputStream credential)
                throws IOException, java.security.GeneralSecurityException {
            Map<String,byte[]> r=profile.roots(asset,credential);
            InstallationSignature.verify(profile.key,r.get("R"),signature);
            return r;
        }
    }

    public static Decoded fromBytes(byte[] raw) {
        try {
            Object[] a=(Object[])ProtocolCbor.decode(raw);
            if(a.length!=12 || !Long.valueOf(3).equals(a[0]))fail();
            Object[] certs=(Object[])a[2], e=(Object[])a[10];
            if(e.length!=7)fail();
            byte[][] chain=new byte[certs.length][];
            for(int i=0;i<chain.length;i++)chain[i]=(byte[])certs[i];
            ReceiptV3 profile=new ReceiptV3((byte[])a[1],chain,(String)a[3],(Long)a[4],
                (byte[])a[5],(String)a[6],(String)a[7],(Long)a[8],(byte[])a[9],
                small(e[0]),(byte[])e[1],small(e[2]),small(e[3]),(byte[])e[4],(byte[])e[5],(byte[])e[6]);
            byte[] sigma=(byte[])a[11];
            if(!Arrays.equals(profile.receipt(sigma),raw))fail();
            return new Decoded(profile,sigma);
        } catch(ClassCastException|NullPointerException|ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Invalid v3 field types/arity",e);
        }
    }
    private static int small(Object value) {
        long n=(Long)value;
        if(n<0||n>3)fail();
        return (int)n;
    }
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

    public void requireIdentity(byte[] expectedKey, byte[][] expectedChain, String expectedPackage,
                                long expectedVersion, byte[] expectedSigner, byte[] expectedChallenge) {
        if(!Arrays.equals(key,expectedKey)||!Arrays.deepEquals(chain,expectedChain)
            ||!packageName.equals(expectedPackage)||version!=expectedVersion||!Arrays.equals(signer,expectedSigner)
            ||!Arrays.equals(challenge,expectedChallenge))fail();
    }

    private static byte[] d(String label, Object... fields) { return ReceiptV1.protocolHash(label, fields); }
    private static byte[] optionalHash(byte[] b) { return b == null ? null : hash(b); }
    private static byte[] hash(byte[] b) { if (b == null || b.length != 32) fail(); return b.clone(); }
    private static void fail() { throw new IllegalArgumentException("Invalid v3 evidence"); }
}
