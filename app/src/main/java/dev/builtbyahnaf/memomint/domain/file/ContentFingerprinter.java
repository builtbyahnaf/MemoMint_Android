package dev.builtbyahnaf.memomint.domain.file;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes deterministic cryptographic content fingerprints (SHA-256) for document streams.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §2.2, §6.2)</h2>
 * <ul>
 *   <li><b>Incremental Indexing:</b> Allows the background worker to detect whether a file's
 *       contents have truly changed before undertaking expensive text extraction.</li>
 *   <li><b>Streaming Digestion:</b> Reads data in fixed 8KB buffers to ensure zero JVM heap bloat,
 *       regardless of whether the input file is 100KB or 25MB.</li>
 * </ul>
 */
public class ContentFingerprinter {

    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Computes the hex-encoded SHA-256 fingerprint of the provided input stream.
     *
     * @param inputStream the stream to digest (caller retains responsibility for closing)
     * @return 64-character lowercase hexadecimal hash string
     * @throws IOException on I/O error during stream reading
     */
    public String computeFingerprint(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in environment", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }
}
