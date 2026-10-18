package dev.builtbyahnaf.memomint.domain.context;

import java.util.Objects;

/**
 * Domain model representing a directed relationship between two memories in the context knowledge graph.
 *
 * <h2>Architectural Purpose (FR-07, Section 11.5 &amp; Phase 3 Plan)</h2>
 * <p>Captures semantic and spatial relations inferred between distinct memories (e.g., two items
 * stored in the same location, or memories referencing the same physical object). In Phase 3,
 * these depth-1 relations feed the {@link dev.builtbyahnaf.memomint.domain.retrieval.RelationshipExpander}
 * to enable associative candidate retrieval.</p>
 */
public final class MemoryRelation {

    private final String id;
    private final String sourceMemoryId;
    private final String relationType;
    private final String targetMemoryId;
    private final double confidence;
    private final String provenance;
    private final long createdAt;

    /**
     * Constructs a MemoryRelation.
     *
     * @param id             unique relation identifier (UUID)
     * @param sourceMemoryId source memory UUID
     * @param relationType   semantic relation type string (e.g. "SAME_LOCATION_AS", "RELATED_ITEM")
     * @param targetMemoryId target memory UUID
     * @param confidence     inference confidence in range [0.0, 1.0]
     * @param provenance     origin of relation (e.g. "INFERRED", "USER_ENTERED")
     * @param createdAt      creation timestamp in UTC epoch milliseconds
     */
    public MemoryRelation(
            String id,
            String sourceMemoryId,
            String relationType,
            String targetMemoryId,
            double confidence,
            String provenance,
            long createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sourceMemoryId = Objects.requireNonNull(sourceMemoryId, "sourceMemoryId cannot be null");
        this.relationType = Objects.requireNonNull(relationType, "relationType cannot be null");
        this.targetMemoryId = Objects.requireNonNull(targetMemoryId, "targetMemoryId cannot be null");
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.provenance = (provenance != null) ? provenance : "INFERRED";
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getSourceMemoryId() {
        return sourceMemoryId;
    }

    public String getRelationType() {
        return relationType;
    }

    public String getTargetMemoryId() {
        return targetMemoryId;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getProvenance() {
        return provenance;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryRelation)) return false;
        MemoryRelation that = (MemoryRelation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemoryRelation{" +
                "id='" + id + '\'' +
                ", sourceMemoryId='" + sourceMemoryId + '\'' +
                ", relationType='" + relationType + '\'' +
                ", targetMemoryId='" + targetMemoryId + '\'' +
                ", confidence=" + confidence +
                ", provenance='" + provenance + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
