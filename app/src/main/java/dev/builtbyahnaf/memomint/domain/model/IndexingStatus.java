package dev.builtbyahnaf.memomint.domain.model;

/**
 * Tracks the text-indexing state of a FileReference.
 *
 * <p>Indexing status is separate from access state. A file may be accessible but not yet
 * indexed (PENDING), successfully indexed (INDEXED), or excluded from indexing by the user
 * or by the application's sensitive-directory rules (EXCLUDED). A failed indexing attempt
 * is recorded as FAILED so that the background job can retry without reprocessing files
 * that already have a valid index entry (NFR-06, Section 13.3).</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum IndexingStatus {

    /**
     * The file is queued for indexing but has not been processed yet.
     */
    PENDING,

    /**
     * The file was successfully indexed. Content is searchable.
     */
    INDEXED,

    /**
     * The last indexing attempt failed (parse error, out-of-memory, etc.).
     * The error details are stored in the associated IndexJob record.
     * The previous valid index entry, if any, is preserved.
     */
    FAILED,

    /**
     * The file is excluded from indexing — either because the user excluded it,
     * or because it matches a sensitive-directory rule. Exclusion is intentional
     * and should not be silently overridden by background work.
     */
    EXCLUDED
}
