package com.example.photoanchor;

/** Enrollment possession proof is separate from photo receipt issuance. */
public final class EnrollmentProofV3 {
    private EnrollmentProofV3() {}
    public static byte[] root(byte[] key,String packageName,long version,byte[] signer,byte[] challenge) {
        // Reuse metadata size/UTF-8 checks without claiming a certificate path.
        new ReceiptV1(key,new byte[][]{{0}},packageName,version,signer,"enrollment","enrollment",0,challenge,0);
        if(challenge==null)throw new IllegalArgumentException("Enrollment challenge required");
        return ReceiptV1.protocolHash("enrollment-possession/evidence-v3",ReceiptV1.hash(key),
            packageName,version,signer,challenge);
    }
}
