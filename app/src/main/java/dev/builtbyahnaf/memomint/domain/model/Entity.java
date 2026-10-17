package dev.builtbyahnaf.memomint.domain.model;

/**
 * A normalized concept or named entity extracted from one or more memories.
 *
 * <h2>Purpose</h2>
 * <p>Entity records represent the vocabulary of the user's stored knowledge. By normalizing
 * synonymous terms to a canonical label (e.g., "earbuds" and "earphones" might both map to
 * a canonical entity), the retrieval engine can match queries and memories that use
 * different surface forms of the same concept.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code canonicalLabel} is the normalized form used for retrieval and synonym lookup.</li>
 *   <li>{@code originalLabel} preserves the exact wording that caused this entity to be
 *       created. Normalization must not erase the user's original phrasing.</li>
 *   <li>Entity records are not user-visible by default; they are a retrieval aid.
 *       Extracted entities must not be presented as confirmed facts (FR-04).</li>
 * </ul>
 *
 * <p>Entities are connected to memories via {@link MemoryEntity} join records.</p>
 *
 * @see EntityType
 * @see MemoryEntity
 */
public class Entity {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The normalized label used as the canonical name for this concept.
     * Example: "earbuds" (even if first observed as "earphones").
     */
    private String canonicalLabel;

    /** The category of concept this entity represents. */
    private EntityType entityType;

    /**
     * The exact label from the memory that caused this entity to be created.
     * Preserved separately so that normalization decisions can be audited or reversed.
     */
    private String originalLabel;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public Entity() {}

    /**
     * Full constructor.
     *
     * @param id             stable UUID
     * @param canonicalLabel normalized concept name
     * @param entityType     type of entity
     * @param originalLabel  user's original wording; may equal canonicalLabel if no normalization occurred
     */
    public Entity(String id, String canonicalLabel, EntityType entityType, String originalLabel) {
        this.id = id;
        this.canonicalLabel = canonicalLabel;
        this.entityType = entityType;
        this.originalLabel = originalLabel;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    /**
     * Returns the stable UUID of this canonical entity.
     *
     * @return unique identifier string
     */
    public String getId() { return id; }

    /**
     * Sets the unique UUID identifier for this entity.
     *
     * @param id stable UUID string
     */
    public void setId(String id) { this.id = id; }

    /**
     * Returns the canonical, normalized form of the concept label.
     * Used by retrieval algorithms for synonym cluster matching (e.g. "earbuds").
     *
     * @return normalized concept string
     */
    public String getCanonicalLabel() { return canonicalLabel; }

    /**
     * Sets the canonical concept label.
     *
     * @param canonicalLabel normalized concept string
     */
    public void setCanonicalLabel(String canonicalLabel) { this.canonicalLabel = canonicalLabel; }

    /**
     * Returns the conceptual classification of this entity (e.g. ITEM, LOCATION, PERSON, TOPIC).
     *
     * @return entity type enum
     */
    public EntityType getEntityType() { return entityType; }

    /**
     * Sets the conceptual classification of this entity.
     *
     * @param entityType classification enum
     */
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }

    /**
     * Returns the raw user phrasing that initially triggered creation of this entity.
     * Preserved separately to prevent irreversible semantic distortion.
     *
     * @return verbatim original surface label
     */
    public String getOriginalLabel() { return originalLabel; }

    /**
     * Sets the verbatim original phrasing label.
     *
     * @param originalLabel raw surface string
     */
    public void setOriginalLabel(String originalLabel) { this.originalLabel = originalLabel; }

    @Override
    public String toString() {
        return "Entity{"
                + "id='" + id + '\''
                + ", canonicalLabel='" + canonicalLabel + '\''
                + ", entityType=" + entityType
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity)) return false;
        Entity other = (Entity) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
