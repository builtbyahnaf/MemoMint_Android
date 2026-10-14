package dev.builtbyahnaf.memomint.domain.model;

/**
 * Lifecycle state of a Memory record.
 *
 * <p>Memories are never hard-deleted from this status field alone. Deletion is a separate
 * explicit operation that cascades through context records, relationships, and FTS entries.
 * Setting status to DELETED marks the record as pending cleanup, not as already erased.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum MemoryStatus {

    /**
     * The memory is current, searchable, and shown in normal result sets.
     */
    ACTIVE,

    /**
     * The memory has been archived by the user. It is retained in the database and
     * searchable when explicitly requested, but excluded from default search results.
     */
    ARCHIVED,

    /**
     * The memory is marked for deletion. It should be excluded from all search results
     * immediately. Cascade cleanup (context records, FTS entries, relationships) may
     * complete asynchronously.
     */
    DELETED
}
