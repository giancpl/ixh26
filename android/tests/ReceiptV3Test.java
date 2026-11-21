package com.example.photoanchor;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

public final class ReceiptV3Test {
    static byte[] b(String s) { return s.equals("-") ? null : HexFormat.of().parseHex(s); }
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] x = line.split("\t");
            ReceiptV3 r = new ReceiptV3(b(x[0]), new byte[][]{b(x[1])}, x[2], Long.parseLong(x[3]),
                    b(x[4]), x[5], x[6], Long.parseLong(x[7]), b(x[8]), Integer.parseInt(x[9]),
                    b(x[10]), Integer.parseInt(x[11]), Integer.parseInt(x[12]), b(x[13]), b(x[14]), b(x[15]));
            Map<String, byte[]> roots = r.roots(new ByteArrayInputStream(b(x[16])),
                    x[17].equals("-") ? null : new ByteArrayInputStream(b(x[17])));
            byte[] enrollmentChallenge=new byte[32];java.util.Arrays.fill(enrollmentChallenge,(byte)'e');
            roots.put("enrollmentRoot",EnrollmentProofV3.root(b(x[0]),x[2],Long.parseLong(x[3]),b(x[4]),enrollmentChallenge));
            for (int i = 20; i < x.length; i++) {
                String[] pair = x[i].split("=");
                if (!java.util.Arrays.equals(roots.get(pair[0]), b(pair[1]))) throw new AssertionError(pair[0]);
            }
            if (!java.util.Arrays.equals(r.receipt(b(x[18])), b(x[19]))) throw new AssertionError("wire");
            InstallationSignature.verify(b(x[0]), roots.get("R"), b(x[18]));
            ReceiptV3.Decoded decoded=ReceiptV3.fromBytes(b(x[19]));
            decoded.verify(new ByteArrayInputStream(b(x[16])),
                x[17].equals("-") ? null : new ByteArrayInputStream(b(x[17])));
            for(byte[] bad:new byte[][]{new byte[0],{(byte)0x98,12}, {1}, {(byte)0xf5},
                    java.util.Arrays.copyOf(b(x[19]),b(x[19]).length+1)}) {
                try {ReceiptV3.fromBytes(bad);throw new AssertionError("accepted malformed CBOR");}
                catch(IllegalArgumentException expected) {}
            }
            count++;
        }
        System.out.println("ReceiptV3: " + count + " shared signed vectors passed");
    }
}
