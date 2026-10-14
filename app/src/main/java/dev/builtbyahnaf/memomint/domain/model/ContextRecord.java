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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public ContextType getContextType() { return contextType; }
    public void setContextType(ContextType contextType) { this.contextType = contextType; }

    /**
     * Returns the derived content string.
     * Only valid when {@code generationStatus == DONE}; may be null otherwise.
     */
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getGeneratorVersion() { return generatorVersion; }
    public void setGeneratorVersion(int generatorVersion) { this.generatorVersion = generatorVersion; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public GenerationStatus getGenerationStatus() { return generationStatus; }
    public void setGenerationStatus(GenerationStatus generationStatus) { this.generationStatus = generationStatus; }

    /** @return true if this context record has usable, searchable content */
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
