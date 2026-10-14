package dev.builtbyahnaf.memomint.domain.model;

/**
 * The primary user-visible record in the MemoMint system.
 *
 * <h2>Design invariants</h2>
 * <ul>
 *   <li>{@code id} is a stable UUID string assigned at creation time and never changed.</li>
 *   <li>{@code originalText} holds exactly what the user typed or spoke. It is never
 *       overwritten by context generation, summarization, or editing of derived fields.
 *       Only an explicit user edit may change it, and the system must record that change
 *       by updating {@code updatedAt}.</li>
 *   <li>{@code status} progresses through its lifecycle but a DELETED memory is still
 *       present in the database until cascade cleanup completes; it is simply excluded
 *       from search and UI.</li>
 *   <li>All timestamps are stored as epoch milliseconds (UTC). Time-zone conversion
 *       is performed in the presentation layer.</li>
 * </ul>
 *
 * <h2>Relationship to other entities</h2>
 * <ul>
 *   <li>A memory may have zero or more {@link ContextRecord} entries (derived context).</li>
 *   <li>A memory may be linked to zero or more {@link Entity} objects via {@link MemoryEntity}.</li>
 *   <li>A memory may participate in zero or more {@link MemoryRelation} records as source or target.</li>
 *   <li>A PHYSICAL_ITEM or FILE_REFERENCE memory may have an associated {@link ConfirmationPolicy}.</li>
 *   <li>A memory may be associated with one or more {@link Reminder} records.</li>
 * </ul>
 *
 * @see MemoryType
 * @see MemoryStatus
 * @see SourceType
 */
public class Memory {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** Stable UUID identifier. Never null, never changed after creation. */
    private String id;

    /**
     * Classifies the kind of information this memory holds.
     * Guides context extraction, UI display, and freshness policy selection.
     */
    private MemoryType type;

    /**
     * The original text exactly as the user entered or spoke it.
     *
     * <p><b>INVARIANT:</b> This field must never be silently overwritten by the
     * context-generation pipeline, a background job, or a summarization step.
     * If the user edits this field, {@code updatedAt} must be updated and the change
     * recorded as a user action, not a system action.</p>
     */
    private String originalText;

    /**
     * An optional short display title for list views.
     * May be null. If absent, the UI should derive a display label from {@code originalText}.
     */
    private String title;

    /** When this memory was first created, in epoch milliseconds (UTC). Never null. */
    private long createdAt;

    /** When this memory was last modified by any operation, in epoch milliseconds (UTC). */
    private long updatedAt;

    /**
     * How this memory entered the system.
     * Affects trust evaluation and provenance display.
     */
    private SourceType sourceType;

    /**
     * Optional reference to the source that produced this memory (e.g., a shared-content
     * package name, an import batch ID, or a voice session ID).
     * May be null.
     */
    private String sourceId;

    /**
     * Current lifecycle state of this memory.
     * Default is {@link MemoryStatus#ACTIVE}.
     */
    private MemoryStatus status;

    /**
     * The last time the user confirmed that this memory is still accurate.
     * Null if the memory has never been confirmed since creation.
     * Used by the freshness and trust layers (Section 15.2).
     */
    private Long lastConfirmedAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * No-argument constructor required for repository deserialization.
     * Callers should use the builder or the full constructor instead of this one directly.
     */
    public Memory() {}

    /**
     * Full constructor for creating a new Memory from a known set of fields.
     *
     * @param id            stable UUID
     * @param type          memory classification
     * @param originalText  original user text; must not be null
     * @param title         optional display title; may be null
     * @param createdAt     creation epoch ms
     * @param updatedAt     last modification epoch ms
     * @param sourceType    how the memory was captured
     * @param sourceId      optional source reference; may be null
     * @param status        lifecycle status
     * @param lastConfirmedAt optional user confirmation timestamp; may be null
     */
    public Memory(
            String id,
            MemoryType type,
            String originalText,
            String title,
            long createdAt,
            long updatedAt,
            SourceType sourceType,
            String sourceId,
            MemoryStatus status,
            Long lastConfirmedAt
    ) {
        this.id = id;
        this.type = type;
        this.originalText = originalText;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.status = status;
        this.lastConfirmedAt = lastConfirmedAt;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /** @return the stable UUID identifier for this memory */
    public String getId() { return id; }

    /** @return the memory type classification */
    public MemoryType getType() { return type; }

    /**
     * Returns the original user-provided text.
     *
     * <p>This value must not be substituted with generated context in any display
     * or storage operation without explicit user consent.</p>
     *
     * @return original text; never null for a valid memory
     */
    public String getOriginalText() { return originalText; }

    /** @return optional display title; may be null */
    public String getTitle() { return title; }

    /** @return creation timestamp as epoch milliseconds (UTC) */
    public long getCreatedAt() { return createdAt; }

    /** @return last modification timestamp as epoch milliseconds (UTC) */
    public long getUpdatedAt() { return updatedAt; }

    /** @return how this memory was captured */
    public SourceType getSourceType() { return sourceType; }

    /** @return optional source reference identifier; may be null */
    public String getSourceId() { return sourceId; }

    /** @return current lifecycle status */
    public MemoryStatus getStatus() { return status; }

    /**
     * @return the last user-confirmation timestamp in epoch ms, or null if
     * this memory has never been explicitly confirmed
     */
    public Long getLastConfirmedAt() { return lastConfirmedAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }

    public void setType(MemoryType type) { this.type = type; }

    /**
     * Updates the original text.
     *
     * <p><b>WARNING:</b> Only call this in response to an explicit user edit.
     * Background jobs and context generation must never call this method.</p>
     *
     * @param originalText the new original text; must not be null
     */
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public void setTitle(String title) { this.title = title; }

    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public void setStatus(MemoryStatus status) { this.status = status; }

    public void setLastConfirmedAt(Long lastConfirmedAt) { this.lastConfirmedAt = lastConfirmedAt; }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    /**
     * Returns true if this memory is in the ACTIVE state.
     * Convenience for use in lists and adapters; not a substitute for querying the DB.
     */
    public boolean isActive() {
        return MemoryStatus.ACTIVE.equals(status);
    }

    /**
     * Returns true if this memory has been soft-deleted.
     * DELETED memories must be excluded from search results immediately upon setting this status.
     */
    public boolean isDeleted() {
        return MemoryStatus.DELETED.equals(status);
    }

    @Override
    public String toString() {
        return "Memory{"
                + "id='" + id + '\''
                + ", type=" + type
                + ", status=" + status
                + ", createdAt=" + createdAt
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Memory)) return false;
        Memory other = (Memory) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
