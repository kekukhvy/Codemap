package dev.codemap.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * Identity of an indexed source file, used to decide whether it must be reparsed.
 *
 * <p>All three fields are kept because none is sufficient alone. Size and mtime
 * are cheap and reject most unchanged files without reading them; the content hash
 * is the tiebreaker for the case they miss — two writes inside the same filesystem
 * timestamp granularity, which on APFS is common enough to matter.
 *
 * @param hash content hash of the file
 * @param size size in bytes
 * @param modifiedAtMillis last-modified time, in epoch milliseconds
 */
public record FileFingerprint(String hash, long size, long modifiedAtMillis) {

    public FileFingerprint {
        Objects.requireNonNull(hash, "hash");
    }

    /**
     * Whether the file appears unchanged relative to another fingerprint.
     *
     * @param other fingerprint recorded on a previous run
     * @return {@code true} when the content is known to be identical
     */
    @JsonIgnore
    public boolean matches(FileFingerprint other) {
        return other != null && hash.equals(other.hash);
    }
}
