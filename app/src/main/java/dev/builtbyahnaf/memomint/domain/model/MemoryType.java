package dev.builtbyahnaf.memomint.domain.model;

/**
 * Classifies the kind of information a Memory record holds.
 *
 * <p>These types share a common memory table rather than separate storage systems (FR-02).
 * They guide context extraction, retrieval ranking, and the UI presentation layer, but they
 * do not alter the core persistence contract.</p>
 *
 * <p><b>NOTE:</b> Stored in the database as the enum name string, not the ordinal, so that
 * adding new values or reordering them never silently corrupts existing rows.</p>
 */
public enum MemoryType {

    /**
     * A free-text observation, thought, or note without a more specific type.
     * This is the default when the capture input does not imply a more specific category.
     */
    NOTE,

    /**
     * A record about a physical object and, optionally, its last known location.
     * Freshness checks and location-confirmation policies apply most naturally to this type.
     */
    PHYSICAL_ITEM,

    /**
     * A named place, room, address, or spatial description not tied to a specific item.
     * Can be the target of a STORED_AT relationship from PHYSICAL_ITEM memories.
     */
    LOCATION,

    /**
     * A reference to a file or document on the device.
     * Associated with a FileReference record for health tracking.
     */
    FILE_REFERENCE,

    /**
     * A memory created from indexed document content.
     * Derived from the document-processing pipeline; the original file is the authoritative source.
     */
    DOCUMENT_CONTEXT,

    /**
     * A scheduled action or future intention.
     * Associated with a Reminder record for scheduling details.
     */
    REMINDER
}
