package com.example.photoanchor;

import java.io.InputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Exact v2 receipt construction. Trust checks precede this mechanism. */
public final class HybridReceiptV2 {
    private final HybridSoftwareKeys.Public publicKey;
    private final byte[][] chain;
    private final String packageName, manufacturer, model;
    private final long version, time;
    private final byte[] signer, challenge, binding;
    private final int placement;

    public HybridReceiptV2(HybridSoftwareKeys.Public publicKey, byte[][] chain,
            String packageName, long version, byte[] signer, String manufacturer, String model,
            long time, byte[] challenge, int placement, byte[] binding) throws GeneralSecurityException {
        // Shared type/size checks only; never serialize the v1 wrapper.
        new ReceiptV1(publicKey.hardwareSpki(), chain, packageName, version, signer,
            manufacturer, model, time, challenge, placement).receipt(binding);
        publicKey.verifyBinding(binding);
        this.publicKey = publicKey;
        this.chain = new byte[chain.length][];
        for (int i = 0; i < chain.length; i++) this.chain[i] = chain[i].clone();
        this.packageName = packageName; this.version = version; this.signer = signer.clone();
        this.manufacturer = manufacturer; this.model = model; this.time = time;
        this.challenge = challenge == null ? null : challenge.clone();
        this.placement = placement; this.binding = binding.clone();
    }

    public byte[] root(InputStream asset, InputStream credential) throws IOException {
        if (asset == null || (placement == 0 && credential != null) || (placement == 1 && credential == null))
            throw new IllegalArgumentException("Missing/conflicting evidence");
        byte[] id = publicKey.deviceId();
        byte[] fileHash = ReceiptV1.protocolHashStream(asset, 128L * 1024 * 1024, false);
        byte[] install = h("fingerprinto/v2/install", id, packageName, version, signer);
        byte[] photo = credential == null ? h("fingerprinto/v2/photo", install, fileHash)
            : h("fingerprinto/v2/photo", install, fileHash, ReceiptV1.protocolHashStream(credential, 16L * 1024 * 1024, true));
        String[] names = {"ID_I", "manufacturer", "model", "localTime", "sessionChallenge"};
        Object[] values = {id, manufacturer, model, time, challenge};
        byte[][] leaves = new byte[5][];
        for (int i = 0; i < 5; i++) leaves[i] = h("fingerprinto/v2/field", names[i], values[i]);
        byte[] device = node(node(node(leaves[0], leaves[1]), node(leaves[2], leaves[3])), leaves[4]);
        return h("fingerprinto/v2", HybridSoftwareKeys.SUITE, node(node(install, photo), device));
    }

    public byte[] encoded(HybridSoftwareKeys.Signatures signatures) {
        return ReceiptV1.protocolEncode(new Object[]{2L, HybridSoftwareKeys.SUITE, publicKey.encoded(), chain,
            packageName, version, signer, manufacturer, model, time, challenge, (long)placement, binding, signatures.encoded()});
    }

    public byte[] commitment(byte[] expectedDeviceId, byte[] root, HybridSoftwareKeys.Signatures signatures)
            throws GeneralSecurityException {
        publicKey.verify(expectedDeviceId, root, signatures);
        return h("fingerprinto/hybrid-receipt/v2", publicKey.encoded(), root, signatures.encoded(), binding);
    }

    private static byte[] node(byte[] left, byte[] right) { return h("fingerprinto/v2/node", left, right); }
    private static byte[] h(String label, Object... fields) { return ReceiptV1.protocolHash(label, fields); }
}
