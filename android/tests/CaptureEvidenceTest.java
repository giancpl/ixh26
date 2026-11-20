import com.example.photoanchor.CaptureEvidence;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Standalone JVM checks; does not require Android or a camera. */
public class CaptureEvidenceTest {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("capture-export-test");
        Path original = folder.resolve("original.jpg");
        try {
            byte[] bytes = new byte[200000];
            new Random(42).nextBytes(bytes);
            Files.write(original, bytes);
            StringBuilder hash = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(String.format("%02x", b & 255));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CaptureEvidence.write(original.toFile(), hash.toString(), bytes.length, "{\"origin\":\"indeterminate\"}", output);
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
                require(zip.getNextEntry().getName().equals("original.jpg"));
                require(Arrays.equals(zip.readAllBytes(), bytes));
                require(zip.getNextEntry().getName().equals("capture-report.json"));
                require(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("indeterminate"));
                require(zip.getNextEntry() == null);
            }
            require(Arrays.equals(Files.readAllBytes(original), bytes));
            byte[] changed = bytes.clone();
            changed[123] ^= 1;
            Files.write(original, changed);
            reject(original, hash.toString(), bytes.length);
            Files.write(original, bytes);
            reject(original, hash.toString(), bytes.length - 1);
            reject(original, hash.toString(), bytes.length + 1);
            reject(original, "00", bytes.length);
            boolean failed = false;
            try {
                CaptureEvidence.write(original.toFile(), hash.toString(), bytes.length, "{}", new OutputStream() {
                    public void write(int b) throws IOException { throw new IOException("disk failure"); }
                });
            } catch (IOException expected) { failed = true; }
            require(failed);
            System.out.println("CaptureEvidence: ZIP byte preservation, hash/size mismatch and write failure checks passed");
        } finally {
            Files.deleteIfExists(original);
            Files.deleteIfExists(folder);
        }
    }
    static void reject(Path original, String hash, long size) throws Exception {
        try {
            CaptureEvidence.write(original.toFile(), hash, size, "{}", new ByteArrayOutputStream());
        } catch (IOException expected) { return; }
        throw new AssertionError("Modified input was accepted");
    }
    static void require(boolean value) {
        if (!value) throw new AssertionError();
    }
}
