package dev.builtbyahnaf.memomint.domain.context;

import java.util.Objects;

/**
 * Domain record linking a {@link dev.builtbyahnaf.memomint.domain.model.Memory} to an extracted entity.
 *
 * <h2>Architectural Purpose (FR-07, Section 11 &amp; Phase 3 Plan)</h2>
 * <p>Captures the association between a memory and a named entity (such as a physical item or location)
 * along with the semantic role played by that entity within the memory context.</p>
 */
public final class MemoryEntityLink {

    private final String memoryId;
    private final String entityId;
    private final String canonicalLabel;
    private final String entityType;
    private final String role;

    /**
     * Constructs a MemoryEntityLink record.
     *
     * @param memoryId       owning memory UUID
     * @param entityId       linked entity UUID
     * @param canonicalLabel canonical dictionary label
     * @param entityType     entity type classification (e.g. ITEM_NAME, LOCATION)
     * @param role           semantic role of entity in memory
     */
    public MemoryEntityLink(
            String memoryId,
            String entityId,
            String canonicalLabel,
            String entityType,
            String role
    ) {
        this.memoryId = Objects.requireNonNull(memoryId, "memoryId cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.canonicalLabel = Objects.requireNonNull(canonicalLabel, "canonicalLabel cannot be null");
        this.entityType = Objects.requireNonNull(entityType, "entityType cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
    }

    public String getMemoryId() {
        return memoryId;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getCanonicalLabel() {
        return canonicalLabel;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryEntityLink)) return false;
        MemoryEntityLink that = (MemoryEntityLink) o;
        return Objects.equals(memoryId, that.memoryId)
                && Objects.equals(entityId, that.entityId)
                && Objects.equals(role, that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memoryId, entityId, role);
    }

    @Override
    public String toString() {
        return "MemoryEntityLink{" +
                "memoryId='" + memoryId + '\'' +
                ", entityId='" + entityId + '\'' +
                ", canonicalLabel='" + canonicalLabel + '\'' +
                ", entityType='" + entityType + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
