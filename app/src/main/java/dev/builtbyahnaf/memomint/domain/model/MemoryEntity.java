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

    /**
     * Gets the UUID of the owning memory.
     *
     * <p>Together with {@link #getEntityId()} and {@link #getRole()}, this forms the composite
     * primary key in the SQLite database schema.</p>
     *
     * @return foreign key UUID matching {@link Memory#getId()}; never null in persisted rows
     */
    public String getMemoryId() {
        return memoryId;
    }

    /**
     * Sets the UUID of the owning memory.
     *
     * @param memoryId foreign key UUID matching {@link Memory#getId()}
     */
    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    /**
     * Gets the UUID of the linked entity.
     *
     * <p>Together with {@link #getMemoryId()} and {@link #getRole()}, this forms the composite
     * primary key in the SQLite database schema.</p>
     *
     * @return foreign key UUID matching {@link Entity#getId()}; never null in persisted rows
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * Sets the UUID of the linked entity.
     *
     * @param entityId foreign key UUID matching {@link Entity#getId()}
     */
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    /**
     * Gets the semantic role this entity plays within the memory.
     *
     * <p>For example, in "Passport is inside the blue backpack", the entity "Passport" plays
     * the role {@link EntityRole#SUBJECT}, whereas "blue backpack" plays {@link EntityRole#LOCATION}.
     * This role is part of the composite primary key, allowing an entity to theoretically appear
     * in distinct roles within a single memory without primary key collisions.</p>
     *
     * @return the {@link EntityRole} classification
     */
    public EntityRole getRole() {
        return role;
    }

    /**
     * Sets the semantic role this entity plays within the memory.
     *
     * @param role the {@link EntityRole} classification
     */
    public void setRole(EntityRole role) {
        this.role = role;
    }

    /**
     * Gets the provenance indicating how this memory-entity link was established.
     *
     * <p><b>Trust Invariant:</b> Links with {@link Provenance#EXTRACTED} or {@link Provenance#INFERRED}
     * must never be presented to the user as confirmed ground truth unless explicitly validated by the user
     * (transitioning provenance to {@link Provenance#USER_ENTERED}).</p>
     *
     * @return the {@link Provenance} origin of this link
     */
    public Provenance getProvenance() {
        return provenance;
    }

    /**
     * Sets the provenance indicating how this memory-entity link was established.
     *
     * @param provenance the {@link Provenance} origin of this link
     */
    public void setProvenance(Provenance provenance) {
        this.provenance = provenance;
    }

    /**
     * Gets the extraction confidence score for automated or heuristic entity links.
     *
     * <p><b>Value Range & Semantics:</b> Must be in the interval [0.0, 1.0]. Null for user-created links
     * where confidence is absolute and not probabilistic. This value serves strictly as an input to
     * ranking algorithms and must not be displayed to the user as a percentage probability claim.</p>
     *
     * @return confidence value in [0.0, 1.0], or null if not applicable
     */
    public Double getConfidence() {
        return confidence;
    }

    /**
     * Sets the extraction confidence score for this link.
     *
     * @param confidence optional confidence value in [0.0, 1.0], or null
     */
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    @Override
    public String toString() {
        return "MemoryEntity{"
                + "memoryId='" + memoryId + '\''
                + ", entityId='" + entityId + '\''
                + ", role=" + role
                + ", provenance=" + provenance
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryEntity)) return false;
        MemoryEntity other = (MemoryEntity) o;
        return (memoryId != null ? memoryId.equals(other.memoryId) : other.memoryId == null)
                && (entityId != null ? entityId.equals(other.entityId) : other.entityId == null)
                && role == other.role;
    }

    @Override
    public int hashCode() {
        int result = memoryId != null ? memoryId.hashCode() : 0;
        result = 31 * result + (entityId != null ? entityId.hashCode() : 0);
        result = 31 * result + (role != null ? role.hashCode() : 0);
        return result;
    }
}
