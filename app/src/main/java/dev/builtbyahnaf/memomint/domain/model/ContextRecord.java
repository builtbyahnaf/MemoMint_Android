package dev.builtbyahnaf.memomint.domain.model;

/**
 * A derived, searchable representation of a Memory's content.
 *
 * <h2>Purpose</h2>
 * <p>Context records convert unstructured memory text into forms that improve retrieval
 * (FR-04). Examples include keyword sets, extracted entities, summaries, and indexed
 * document text. Each ContextRecord is owned by exactly one Memory and has a type that
 * describes what kind of derived content it holds.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>A failed generation ({@link GenerationStatus#FAILED}) must not replace or delete
 *       the previous valid record. The previous record's content remains searchable until
 *       a successful regeneration replaces it.</li>
 *   <li>Generated content is never used to overwrite {@link Memory#getOriginalText()}.</li>
 *   <li>{@code generatorVersion} tracks which version of the extraction logic produced
 *       this record. When the logic changes, stale records can be identified and
 *       queued for regeneration.</li>
 * </ul>
 *
 * @see ContextType
 * @see GenerationStatus
 */
public class ContextRecord {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The memory this context record belongs to.
     * Foreign key to {@link Memory#getId()}; cascade-deleted when the memory is deleted.
     */
    private String memoryId;

    /** The kind of derived information held in {@code content}. */
    private ContextType contextType;

    /**
     * The derived textual content.
     *
     * <p>For KEYWORDS, this might be a whitespace-separated keyword list.
     * For SUMMARY, a short paragraph. For ENTITIES, a JSON-like structured representation.
     * For DOCUMENT_CONTENT, a chunk of the extracted document text.</p>
     *
     * <p>This field is only meaningful when {@code generationStatus} is {@link GenerationStatus#DONE}.</p>
     */
    private String content;

    /**
     * An integer version number identifying which version of the extraction logic produced
     * this record. Increment this constant in {@code DatabaseConstants} when extraction
     * logic changes so that outdated records can be detected and regenerated.
     */
    private int generatorVersion;

    /** When this record was first created, in epoch milliseconds (UTC). */
    private long createdAt;

    /** When this record was last updated (e.g., regenerated), in epoch milliseconds (UTC). */
    private long updatedAt;

    /** Whether generation completed, is pending, or failed. */
    private GenerationStatus generationStatus;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public ContextRecord() {}

    /**
     * Full constructor.
     *
     * @param id               stable UUID
     * @param memoryId         owning memory's UUID
     * @param contextType      type of derived content
     * @param content          derived text; may be null when status is PENDING or FAILED
     * @param generatorVersion extraction logic version
     * @param createdAt        creation epoch ms
     * @param updatedAt        last update epoch ms
     * @param generationStatus current generation state
     */
    public ContextRecord(
            String id,
            String memoryId,
            ContextType contextType,
            String content,
            int generatorVersion,
            long createdAt,
            long updatedAt,
            GenerationStatus generationStatus
    ) {
        this.id = id;
        this.memoryId = memoryId;
        this.contextType = contextType;
        this.content = content;
        this.generatorVersion = generatorVersion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.generationStatus = generationStatus;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    /**
     * Returns the stable UUID of this context record.
     *
     * @return unique identifier string
     */
    public String getId() { return id; }

    /**
     * Sets the UUID identifier for this context record.
     *
     * @param id stable UUID string
     */
    public void setId(String id) { this.id = id; }

    /**
     * Returns the UUID of the parent Memory owning this context.
     *
     * @return owning memory's UUID
     */
    public String getMemoryId() { return memoryId; }

    /**
     * Sets the owning memory UUID reference.
     *
     * @param memoryId parent memory's UUID
     */
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    /**
     * Returns the category of derived content (e.g. SUMMARY, KEYWORDS, ENTITIES, DOCUMENT_CONTENT).
     *
     * @return context type enum
     */
    public ContextType getContextType() { return contextType; }

    /**
     * Sets the category of derived content.
     *
     * @param contextType classification enum
     */
    public void setContextType(ContextType contextType) { this.contextType = contextType; }

    /**
     * Returns the derived content string (keywords, entity structure, summary).
     *
     * <p><b>Invariant:</b> This string represents derived information only. It must never be
     * used to overwrite {@link Memory#getOriginalText()}. Only valid when
     * {@link #getGenerationStatus()} is {@link GenerationStatus#DONE}.</p>
     *
     * @return derived searchable text, or null if generation is pending or failed
     */
    public String getContent() { return content; }

    /**
     * Sets the derived textual content.
     *
     * @param content derived text string
     */
    public void setContent(String content) { this.content = content; }

    /**
     * Returns the generator logic version that created this record.
     * Used to identify and regenerate outdated records when extraction heuristics improve.
     *
     * @return integer version counter
     */
    public int getGeneratorVersion() { return generatorVersion; }

    /**
     * Sets the generator logic version number.
     *
     * @param generatorVersion integer version number
     */
    public void setGeneratorVersion(int generatorVersion) { this.generatorVersion = generatorVersion; }

    /**
     * Returns the creation timestamp in UTC epoch milliseconds.
     *
     * @return creation epoch ms
     */
    public long getCreatedAt() { return createdAt; }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt UTC epoch milliseconds
     */
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    /**
     * Returns the last modification/regeneration timestamp in UTC epoch milliseconds.
     *
     * @return last updated epoch ms
     */
    public long getUpdatedAt() { return updatedAt; }

    /**
     * Sets the last update timestamp.
     *
     * @param updatedAt UTC epoch milliseconds
     */
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Returns the generation lifecycle state (PENDING, DONE, FAILED).
     *
     * @return generation status enum
     */
    public GenerationStatus getGenerationStatus() { return generationStatus; }

    /**
     * Sets the current generation lifecycle status.
     *
     * @param generationStatus state enum
     */
    public void setGenerationStatus(GenerationStatus generationStatus) { this.generationStatus = generationStatus; }

    /**
     * Evaluates whether this context record contains valid, searchable content.
     *
     * @return true if status is DONE and content is non-null; false otherwise
     */
    public boolean isUsable() {
        return GenerationStatus.DONE.equals(generationStatus) && content != null;
    }

    @Override
    public String toString() {
        return "ContextRecord{"
                + "id='" + id + '\''
                + ", memoryId='" + memoryId + '\''
                + ", contextType=" + contextType
                + ", generationStatus=" + generationStatus
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContextRecord)) return false;
        ContextRecord other = (ContextRecord) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
