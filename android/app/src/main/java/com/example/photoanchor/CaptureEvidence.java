package com.example.photoanchor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Diagnostic capture export, not a signed FingerprinTO receipt or origin claim. */
public final class CaptureEvidence {
    private CaptureEvidence() {}

    public static void write(File original, String expectedSha256, long expectedSize,
                             String reportJson, OutputStream destination) throws IOException {
        if (!expectedSha256.matches("[0-9a-f]{64}") || expectedSize < 0
                || expectedSize > 128L * 1024 * 1024 || !original.isFile()) {
            throw new IOException("Invalid capture evidence");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        // Close the ZIP/destination on success or failure. Callers discard any
        // partial destination if this operation throws.
        try (ZipOutputStream zip = new ZipOutputStream(destination);
             FileInputStream input = new FileInputStream(original)) {
            zip.putNextEntry(new ZipEntry("original.jpg"));
            byte[] buffer = new byte[65536];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > expectedSize) throw new IOException("Capture changed during export");
                digest.update(buffer, 0, count);
                zip.write(buffer, 0, count);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
            if (total != expectedSize || !hash.toString().equals(expectedSha256)) {
                throw new IOException("Capture changed during export");
            }
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("capture-report.json"));
            zip.write(reportJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
