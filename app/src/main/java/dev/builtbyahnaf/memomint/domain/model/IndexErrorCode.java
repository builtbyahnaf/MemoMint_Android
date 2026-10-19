package dev.builtbyahnaf.memomint.domain.model;

/**
 * Standardized, stable error codes for file indexing and health monitoring.
 *
 * <h2>Purpose & Architectural Invariants (Section 13.2, 15.3, Phase 4 Plan §4.1)</h2>
 * <p>When a background indexing job or health check encounters an issue, an error code from
 * this class is recorded in the {@code index_jobs.error_code} column. These codes allow
 * UI components, retry schedulers, and diagnostic tools to determine the precise failure mode
 * and present actionable recovery guidance without leaking private document text.</p>
 */
public final class IndexErrorCode {

    private IndexErrorCode() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /** Persistent Storage Access Framework (SAF) URI permission was revoked by the user or OS. */
    public static final String ERR_PERMISSION_REVOKED = "ERR_PERMISSION_REVOKED";

    /** Backing document file could not be found or resolved (moved, renamed, or deleted outside app). */
    public static final String ERR_FILE_NOT_FOUND = "ERR_FILE_NOT_FOUND";

    /** File size exceeds the maximum indexing safety ceiling (default 25MB). */
    public static final String ERR_FILE_TOO_LARGE = "ERR_FILE_TOO_LARGE";

    /** File size is 0 bytes; no text content available to parse. */
    public static final String ERR_EMPTY_FILE = "ERR_EMPTY_FILE";

    /** PDF document is protected with an owner or user password and cannot be read. */
    public static final String ERR_PDF_ENCRYPTED = "ERR_PDF_ENCRYPTED";

    /** PDF document contains scanned bitmap images with no embedded extractable text layer. */
    public static final String ERR_PDF_SCANNED = "ERR_PDF_SCANNED";

    /** Microsoft Word DOCX archive is malformed, missing word/document.xml, or contains invalid XML. */
    public static final String ERR_DOCX_CORRUPT = "ERR_DOCX_CORRUPT";

    /** I/O operation stalled or timed out while streaming from a remote or slow external provider. */
    public static final String ERR_IO_TIMEOUT = "ERR_IO_TIMEOUT";

    /** JVM heap pressure occurred while streaming document contents; aborted to prevent app crash. */
    public static final String ERR_OUT_OF_MEMORY = "ERR_OUT_OF_MEMORY";

    /** General or unrecognized parse failure. */
    public static final String ERR_PARSE_FAILED = "ERR_PARSE_FAILED";
}
