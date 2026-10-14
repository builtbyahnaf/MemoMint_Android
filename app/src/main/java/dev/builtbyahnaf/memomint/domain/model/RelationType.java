package dev.builtbyahnaf.memomint.domain.model;

/**
 * Defines named, directional relationship types between memories and entities.
 *
 * <p>Relationship types are defined centrally here rather than scattered as inconsistent
 * string literals across the codebase (FR-07, Section 11.5). All callers must reference
 * this enum's {@link #name()} when writing to or reading from the database.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 *
 * <p><b>Directionality:</b> A relation always points from a source (source_type, source_id)
 * to a target (target_type, target_id). The semantics of each type below assume this
 * left-to-right direction.</p>
 */
public enum RelationType {

    /**
     * The source item memory is stored at the target location memory or entity.
     * Example: [earbuds memory] STORED_AT [desk drawer memory]
     */
    STORED_AT,

    /**
     * The source memory references the target file.
     * Example: [document note memory] REFERENCES_FILE [FileReference for notes_final.docx]
     */
    REFERENCES_FILE,

    /**
     * The source memory has an attachment (a secondary file or document).
     * Example: [research memory] HAS_ATTACHMENT [FileReference for diagram.pdf]
     */
    HAS_ATTACHMENT,

    /**
     * The source memory is associated with the target reminder.
     * Example: [earbuds location memory] ASSOCIATED_WITH_REMINDER [8 PM tomorrow reminder]
     */
    ASSOCIATED_WITH_REMINDER,

    /**
     * A generic bidirectional relationship between two memories that are conceptually linked
     * but do not fit a more specific type.
     */
    RELATED_TO,

    /**
     * The source record supersedes the target record.
     * Used when a user records a new location for an item, creating a newer record while
     * preserving the history of the older record (Section 15.4).
     * Example: [new location memory] SUPERSEDES [old location memory]
     */
    SUPERSEDES
}
