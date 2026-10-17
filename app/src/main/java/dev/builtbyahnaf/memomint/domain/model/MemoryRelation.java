package dev.builtbyahnaf.memomint.domain.model;

/**
 * Represents a directed relationship between two records in the memory graph.
 *
 * <h2>Purpose</h2>
 * <p>MemoMint's relationship graph (FR-07) makes connections between memories, entities,
 * and files explicit and traversable. Rather than scanning all memories to discover that
 * "earbuds" might be related to "desk drawer", the retrieval engine can follow an explicit
 * STORED_AT relation. This improves recall without requiring semantic understanding.</p>
 *
 * <h2>Polymorphic endpoints</h2>
 * <p>Both source and target use a (type, id) pair to reference different record types.
 * Valid type strings are defined in {@code DatabaseConstants.RelationParty}:
 * {@code "MEMORY"} and {@code "ENTITY"}. This avoids a full graph database while still
 * supporting heterogeneous relationships.</p>
 *
 * <h2>SUPERSEDES relationships and history preservation</h2>
 * <p>When a user records a new location for an existing item, the new memory should
 * SUPERSEDES the old one rather than silently replacing it (Section 15.4). Both records
 * are retained; the SUPERSEDES relation allows the retrieval engine to prefer the newer
 * record while still exposing the historical chain.</p>
 *
 * @see RelationType
 * @see Provenance
 */
public class MemoryRelation {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The type of the source record.
     * Must be one of the constants in {@code DatabaseConstants.RelationParty}.
     */
    private String sourceType;

    /**
     * The UUID of the source record (a Memory or Entity id, depending on sourceType).
     */
    private String sourceId;

    /** The kind of relationship being expressed. */
    private RelationType relationType;

    /**
     * The type of the target record.
     * Must be one of the constants in {@code DatabaseConstants.RelationParty}.
     */
    private String targetType;

    /**
     * The UUID of the target record (a Memory or Entity id, depending on targetType).
     */
    private String targetId;

    /**
     * How this relationship was established.
     * Relations created by the user have USER_ENTERED provenance;
     * relations created by extraction rules have INFERRED or EXTRACTED provenance.
     */
    private Provenance provenance;

    /**
     * Optional confidence value [0.0, 1.0] for inferred relationships.
     * Null for user-created relationships.
     */
    private Double confidence;

    /** When this relation was created, in epoch milliseconds (UTC). */
    private long createdAt;

    /**
     * When the user last confirmed that this relation is still accurate.
     * Null if never confirmed (or if confirmation is not applicable to this relation type).
     */
    private Long lastConfirmedAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public MemoryRelation() {}

    /**
     * Full constructor.
     *
     * @param id              stable UUID
     * @param sourceType      "MEMORY" or "ENTITY"
     * @param sourceId        UUID of the source record
     * @param relationType    the kind of relationship
     * @param targetType      "MEMORY" or "ENTITY"
     * @param targetId        UUID of the target record
     * @param provenance      how this relation was established
     * @param confidence      optional confidence for inferred relations; may be null
     * @param createdAt       creation epoch ms
     * @param lastConfirmedAt optional confirmation timestamp; may be null
     */
    public MemoryRelation(
            String id,
            String sourceType,
            String sourceId,
            RelationType relationType,
            String targetType,
            String targetId,
            Provenance provenance,
            Double confidence,
            long createdAt,
            Long lastConfirmedAt
    ) {
        this.id = id;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.relationType = relationType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.provenance = provenance;
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.lastConfirmedAt = lastConfirmedAt;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    /**
     * Gets the unique stable identifier (UUID) for this relation record.
     *
     * @return stable UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique stable identifier (UUID) for this relation record.
     *
     * @param id stable UUID string
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the polymorphic type discriminator for the relation source.
     *
     * <p>Must be one of the constants defined in {@code DatabaseConstants.RelationParty}:
     * {@code "MEMORY"} or {@code "ENTITY"}. For instance, in a {@link RelationType#STORED_AT}
     * relationship where a memory describes the location of an entity, the source is "MEMORY".</p>
     *
     * @return source type discriminator string
     */
    public String getSourceType() {
        return sourceType;
    }

    /**
     * Sets the polymorphic type discriminator for the relation source.
     *
     * @param sourceType source type discriminator string ("MEMORY" or "ENTITY")
     */
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * Gets the UUID of the source record.
     *
     * @return foreign key UUID matching either a {@link Memory#getId()} or {@link Entity#getId()}
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Sets the UUID of the source record.
     *
     * @param sourceId foreign key UUID
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    /**
     * Gets the semantic relationship type linking source to target.
     *
     * <p>Defines the traversal semantics (e.g., {@link RelationType#SUPERSEDES} creates a historical
     * precedence chain, {@link RelationType#STORED_AT} creates a spatial containment link).</p>
     *
     * @return the {@link RelationType} classification
     */
    public RelationType getRelationType() {
        return relationType;
    }

    /**
     * Sets the semantic relationship type.
     *
     * @param relationType the {@link RelationType} classification
     */
    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
    }

    /**
     * Gets the polymorphic type discriminator for the relation target.
     *
     * <p>Must be one of the constants defined in {@code DatabaseConstants.RelationParty}:
     * {@code "MEMORY"} or {@code "ENTITY"}.</p>
     *
     * @return target type discriminator string
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * Sets the polymorphic type discriminator for the relation target.
     *
     * @param targetType target type discriminator string ("MEMORY" or "ENTITY")
     */
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    /**
     * Gets the UUID of the target record.
     *
     * @return foreign key UUID matching either a {@link Memory#getId()} or {@link Entity#getId()}
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Sets the UUID of the target record.
     *
     * @param targetId foreign key UUID
     */
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    /**
     * Gets the provenance indicating how this relationship was created.
     *
     * <p><b>Trust Invariant:</b> Inferred or extracted relations (e.g. from NLP heuristics)
     * must have {@link Provenance#INFERRED} or {@link Provenance#EXTRACTED} and are subject to
     * lower trust ranking than user-created relationships ({@link Provenance#USER_ENTERED}).</p>
     *
     * @return the {@link Provenance} origin of this relationship
     */
    public Provenance getProvenance() {
        return provenance;
    }

    /**
     * Sets the provenance indicating how this relationship was created.
     *
     * @param provenance the {@link Provenance} origin
     */
    public void setProvenance(Provenance provenance) {
        this.provenance = provenance;
    }

    /**
     * Gets the extraction or inference confidence score.
     *
     * <p><b>Value Range & Semantics:</b> Must be in the interval [0.0, 1.0]. Null for user-created relations.
     * Used by retrieval graph traversals to prune weak inferences below a threshold.</p>
     *
     * @return confidence value in [0.0, 1.0], or null if not applicable
     */
    public Double getConfidence() {
        return confidence;
    }

    /**
     * Sets the extraction or inference confidence score.
     *
     * @param confidence optional confidence in [0.0, 1.0], or null
     */
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    /**
     * Gets the creation timestamp in UTC epoch milliseconds.
     *
     * @return UTC epoch milliseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp in UTC epoch milliseconds.
     *
     * @param createdAt UTC epoch milliseconds
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the timestamp when the user last explicitly verified that this relationship holds true.
     *
     * <p>Used by freshness decay algorithms (e.g., confirming that an item is still stored at the
     * referenced location resets the decay clock).</p>
     *
     * @return UTC epoch milliseconds, or null if never confirmed
     */
    public Long getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    /**
     * Sets the timestamp when the user last confirmed this relationship.
     *
     * @param lastConfirmedAt UTC epoch milliseconds, or null
     */
    public void setLastConfirmedAt(Long lastConfirmedAt) {
        this.lastConfirmedAt = lastConfirmedAt;
    }

    @Override
    public String toString() {
        return "MemoryRelation{"
                + "id='" + id + '\''
                + ", sourceType='" + sourceType + '\''
                + ", sourceId='" + sourceId + '\''
                + ", relationType=" + relationType
                + ", targetType='" + targetType + '\''
                + ", targetId='" + targetId + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryRelation)) return false;
        MemoryRelation other = (MemoryRelation) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
