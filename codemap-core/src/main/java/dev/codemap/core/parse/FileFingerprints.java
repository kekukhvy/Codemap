package dev.codemap.core.parse;

import dev.codemap.core.model.FileFingerprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the identity of a source file for the incremental cache.
 */
public final class FileFingerprints {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    /** Marks a file whose content could not be hashed, so it is always reparsed. */
    private static final String UNREADABLE_HASH = "unreadable";

    private FileFingerprints() {
    }

    /**
     * Fingerprints a file by content hash, size, and modification time.
     *
     * <p>An unreadable file yields a fingerprint that can never match a previous
     * one, so it is reparsed on every run rather than being silently treated as
     * unchanged. Failing here would end the run over one bad file, which the
     * project's degradation rule forbids.
     *
     * @param file the file to fingerprint
     * @return its fingerprint, never {@code null}
     */
    public static FileFingerprint of(Path file) {
        try {
            byte[] content = Files.readAllBytes(file);
            return new FileFingerprint(
                    hash(content),
                    content.length,
                    Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            return new FileFingerprint(UNREADABLE_HASH, 0L, 0L);
        }
    }

    private static String hash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is required but unavailable", e);
        }
    }
}
