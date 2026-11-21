import com.example.photoanchor.ReceiptV1;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Shared official vectors plus Python-generated integer/Unicode boundary cases. */
public class ReceiptV1Test {
    public static void main(String[] args) throws Exception {
        int cases = 0;
        try (java.util.stream.Stream<Path> paths = Files.list(Paths.get(args[0]))) {
            for (Path file : (Iterable<Path>) paths.sorted()::iterator) {
                Properties p = new Properties();
                try (InputStream input = Files.newInputStream(file)) { p.load(input); }
                byte[] key = hex(p.getProperty("pk_I"));
                byte[][] chain = {hex(p.getProperty("chain"))};
                String packageName = p.getProperty("packageName");
                long version = Long.parseLong(p.getProperty("versionCode"));
                byte[] signer = hex(p.getProperty("signerDigest"));
                String manufacturer = p.getProperty("manufacturer");
                String model = p.getProperty("model");
                long time = Long.parseLong(p.getProperty("localTime"));
                byte[] challenge = optional(p.getProperty("sessionChallenge"));
                int placement = Integer.parseInt(p.getProperty("placement"));
                ReceiptV1 receipt = new ReceiptV1(key, chain, packageName, version, signer, manufacturer, model, time, challenge, placement);
                // Caller mutations must not change the snapshotted fields.
                key[0] ^= 1;
                chain[0][0] ^= 1;
                signer[0] ^= 1;
                if (challenge != null) challenge[0] ^= 1;
                byte[] asset = hex(p.getProperty("J"));
                byte[] credential = optional(p.getProperty("A"));
                Map<String, byte[]> roots = receipt.roots(new ByteArrayInputStream(asset), stream(credential));
                Map<String, byte[]> changed = receipt.roots(new ByteArrayInputStream(new byte[]{1, 2, 3}), stream(credential));
                if (Arrays.equals(roots.get("R"), changed.get("R"))) throw new AssertionError("Altered asset has same root");
                for (String name : roots.keySet()) check(roots.get(name), hex(p.getProperty(name)), name);
                byte[] sigma = hex(p.getProperty("sigma"));
                check(receipt.receipt(sigma), hex(p.getProperty("receipt_cbor")), "receipt CBOR");
                byte[] commitment = ReceiptV1.commitment(roots.get("R"), sigma);
                check(commitment, hex(p.getProperty("C")), "C");
                check(ReceiptV1.hash(commitment), hex(p.getProperty("OTS_SHA256_C")), "SHA256(C)");
                if (p.getProperty("signature_valid").equals("true")) {
                    PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(hex(p.getProperty("pk_I"))));
                    Signature verifier = Signature.getInstance("SHA256withECDSA");
                    verifier.initVerify(publicKey);
                    verifier.update(roots.get("R"));
                    if (!verifier.verify(sigma)) throw new AssertionError("JCA signature mismatch");
                    verifier.initVerify(publicKey);
                    verifier.update(roots.get("T"));
                    if (verifier.verify(sigma)) throw new AssertionError("Bare T accepted");
                }
                try {
                    receipt.roots(new ByteArrayInputStream(asset), placement == 0 ? new ByteArrayInputStream(new byte[1]) : null);
                    throw new AssertionError("Conflicting placement accepted");
                } catch (IllegalArgumentException expected) { /* correct */ }
                cases++;
            }
        }
        rejectText("\ud800");
        rejectText("");
        rejectText(String.join("", Collections.nCopies(128, "é")));
        ReceiptV1 detached = new ReceiptV1(new byte[91], new byte[][]{new byte[1]}, "p", 0,
                new byte[32], "m", "m", 0, null, 1);
        try {
            detached.roots(new ByteArrayInputStream(new byte[0]), new ByteArrayInputStream(new byte[0]));
            throw new AssertionError("Empty credential accepted");
        } catch (IOException expected) { /* correct */ }
        try {
            detached.roots(new InputStream() {
                public int read() throws IOException { throw new IOException("source failure"); }
            }, new ByteArrayInputStream(new byte[]{1}));
            throw new AssertionError("Source error ignored");
        } catch (IOException expected) { /* correct */ }
        System.out.println("ReceiptV1: " + cases + " shared/boundary vectors, JCA signatures and rejection checks passed");
    }
    static void rejectText(String text) {
        try {
            new ReceiptV1(new byte[91], new byte[][]{new byte[1]}, text, 0, new byte[32], "m", "m", 0, null, 0);
            throw new AssertionError("Invalid text accepted");
        } catch (IllegalArgumentException expected) { /* correct */ }
    }
    static InputStream stream(byte[] value) { return value == null ? null : new ByteArrayInputStream(value); }
    static byte[] optional(String value) { return value.equals("null") ? null : hex(value); }
    static byte[] hex(String text) {
        byte[] data = new byte[text.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte) Integer.parseInt(text.substring(i*2, i*2+2), 16);
        return data;
    }
    static void check(byte[] actual, byte[] expected, String field) {
        if (!Arrays.equals(actual, expected)) throw new AssertionError(field + " mismatch");
    }
}
