package dev.builtbyahnaf.memomint.domain.file;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import dev.builtbyahnaf.memomint.domain.model.IndexErrorCode;

/**
 * Enforces file exclusion policies based on size limits, hidden file conventions,
 * and unsupported binary/media extensions.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §4.1, §6.2)</h2>
 * <ul>
 *   <li><b>Resource Safety:</b> Rejects oversized files (> 25MB by default) before attempting
 *       to stream or parse, preventing out-of-memory errors on memory-constrained devices.</li>
 *   <li><b>Binary Filtering:</b> Excludes executable binaries, compressed archives, and media files
 *       that cannot be reasonably parsed for textual search.</li>
 *   <li><b>Hidden File Privacy:</b> Skips dot-prefixed hidden files and directories.</li>
 * </ul>
 */
public class ExclusionPolicy {

    /** Maximum file size ceiling eligible for text indexing (25 megabytes). */
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 25L * 1024L * 1024L;

    private static final Set<String> DEFAULT_EXCLUDED_EXTENSIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "bin", "exe", "apk", "zip", "tar", "gz", "7z", "rar",
                    "mp4", "mp3", "mkv", "avi", "mov", "wav", "flac",
                    "iso", "dmg", "jar", "so", "dll", "class", "dex", "o"
            ))
    );

    private final long maxFileSizeBytes;
    private final Set<String> excludedExtensions;

    public ExclusionPolicy() {
        this(DEFAULT_MAX_FILE_SIZE_BYTES, DEFAULT_EXCLUDED_EXTENSIONS);
    }

    public ExclusionPolicy(long maxFileSizeBytes, Set<String> excludedExtensions) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.excludedExtensions = new HashSet<>(
                excludedExtensions != null ? excludedExtensions : DEFAULT_EXCLUDED_EXTENSIONS
        );
    }

    /**
     * Determines whether the specified file should be excluded from full-text indexing.
     *
     * @param fileName  the file display name or path
     * @param sizeBytes the file size in bytes (-1 or null if unknown)
     * @return true if the file should be excluded; false if eligible for indexing
     */
    public boolean isExcluded(String fileName, long sizeBytes) {
        return getExclusionReason(fileName, sizeBytes) != null;
    }

    /**
     * Returns the standardized reason code or explanation if the file is excluded, or null if eligible.
     *
     * @param fileName  the file display name or path
     * @param sizeBytes the file size in bytes
     * @return error code from {@link IndexErrorCode} or descriptive reason, or null if permitted
     */
    public String getExclusionReason(String fileName, long sizeBytes) {
        if (sizeBytes > maxFileSizeBytes) {
            return IndexErrorCode.ERR_FILE_TOO_LARGE;
        }

        if (fileName == null || fileName.trim().isEmpty()) {
            return "EMPTY_FILENAME";
        }

        String name = fileName.trim();
        // Check for hidden file (starts with .)
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String simpleName = (lastSlash >= 0) ? name.substring(lastSlash + 1) : name;
        if (simpleName.startsWith(".") && simpleName.length() > 1) {
            return "HIDDEN_FILE";
        }

        // Check excluded extension
        int dotIndex = simpleName.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < simpleName.length() - 1) {
            String ext = simpleName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
            if (excludedExtensions.contains(ext)) {
                return "UNSUPPORTED_BINARY_EXTENSION";
            }
        }

        return null;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
