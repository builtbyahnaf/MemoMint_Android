package dev.builtbyahnaf.memomint.domain.model;

/**
 * Join record that links a {@link Memory} to an {@link Entity} and describes
 * the role that entity plays within that memory.
 *
 * <h2>Purpose</h2>
 * <p>A memory may reference multiple entities (e.g., an item as SUBJECT and a location
 * as LOCATION). The same entity may appear in many memories with different roles.
 * This join record makes those relationships explicit so the retrieval engine can
 * traverse them.</p>
 *
 * <h2>Composite primary key</h2>
 * <p>The combination of (memoryId, entityId, role) is the composite primary key. A memory
 * cannot have the same entity in the same role twice, but it can have the same entity in
 * two different roles (unusual but permitted).</p>
 *
 * <h2>Trust</h2>
 * <p>The {@code provenance} field records whether this link was created by user action,
 * deterministic extraction, or inference. An extracted link must not be displayed as
 * confirmed. {@code confidence} carries an optional numeric signal (0.0–1.0) for
 * extraction-produced links.</p>
 *
 * @see EntityRole
 * @see Provenance
 */
public class MemoryEntity {

    /** Foreign key to {@link Memory#getId()}. Part of composite PK. */
    private String memoryId;

    /** Foreign key to {@link Entity#getId()}. Part of composite PK. */
    private String entityId;

    /** The semantic role this entity plays in the referenced memory. Part of composite PK. */
    private EntityRole role;

    /**
     * How this link was established.
     * Links with INFERRED or EXTRACTED provenance should not be treated as user-confirmed facts.
     */
    private Provenance provenance;

    /**
     * Optional confidence value for extraction-produced links, in the range [0.0, 1.0].
     * Null for user-entered links (where confidence is not meaningful).
     *
     * <p>This value is an input to ranking, not a display claim. A 0.9 confidence score
     * does not mean the extraction is 90% correct in all contexts.</p>
     */
    private Double confidence;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public MemoryEntity() {}

    /**
     * Full constructor.
     *
     * @param memoryId   owning memory's UUID
     * @param entityId   related entity's UUID
     * @param role       entity's role in this memory
     * @param provenance how this link was established
     * @param confidence optional extraction confidence; null for user-entered links
     */
    public MemoryEntity(
            String memoryId,
            String entityId,
            EntityRole role,
            Provenance provenance,
            Double confidence
    ) {
        this.memoryId = memoryId;
        this.entityId = entityId;
        this.role = role;
        this.provenance = provenance;
        this.confidence = confidence;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public EntityRole getRole() { return role; }
    public void setRole(EntityRole role) { this.role = role; }

    public Provenance getProvenance() { return provenance; }
    public void setProvenance(Provenance provenance) { this.provenance = provenance; }

    /** @return confidence value in [0.0, 1.0], or null if not applicable */
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    @Override
    public String toString() {
        return "MemoryEntity{"
                + "memoryId='" + memoryId + '\''
                + ", entityId='" + entityId + '\''
                + ", role=" + role
                + ", provenance=" + provenance
                + '}';
    }
}
