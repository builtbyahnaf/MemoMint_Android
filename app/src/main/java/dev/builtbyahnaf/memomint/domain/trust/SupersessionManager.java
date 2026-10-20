package dev.builtbyahnaf.memomint.domain.trust;

import java.util.List;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepository;
import dev.builtbyahnaf.memomint.domain.model.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.model.Provenance;
import dev.builtbyahnaf.memomint.domain.model.RelationType;
import dev.builtbyahnaf.memomint.domain.model.SupersessionInfo;

/**
 * Pure Java domain service managing supersession relationships and lineage chains between memories.
 *
 * <h2>Purpose &amp; Invariants</h2>
 * <p>When a physical item is moved or a memory assertion is updated, the older memory must
 * never be silently overwritten or deleted (Section 15.4, NFR-03). Instead, a directed
 * {@link RelationType#SUPERSEDES} relation is created from the new memory to the old memory,
 * preserving complete audit history while allowing retrieval ranking to prioritize the newer claim.</p>
 */
public class SupersessionManager {

    private final MemoryRelationRepository relationRepository;

    /**
     * Constructs a new {@link SupersessionManager}.
     *
     * @param relationRepository the repository managing {@link MemoryRelation} records; must not be null
     * @throws IllegalArgumentException if relationRepository is null
     */
    public SupersessionManager(MemoryRelationRepository relationRepository) {
        if (relationRepository == null) {
            throw new IllegalArgumentException("relationRepository must not be null");
        }
        this.relationRepository = relationRepository;
    }

    /**
     * Creates a {@link RelationType#SUPERSEDES} link asserting that {@code activeMemoryId}
     * supersedes {@code supersededMemoryId}.
     *
     * @param activeMemoryId     the UUID of the newer, active memory; must not be null
     * @param supersededMemoryId the UUID of the older, superseded memory; must not be null
     * @param nowMillis          UTC epoch timestamp when the supersession was established
     * @return an immutable {@link SupersessionInfo} snapshot of the supersession event
     * @throws IllegalArgumentException if either activeMemoryId or supersededMemoryId is null
     */
    public SupersessionInfo supersede(String activeMemoryId, String supersededMemoryId, long nowMillis) {
        if (activeMemoryId == null || supersededMemoryId == null) {
            throw new IllegalArgumentException("activeMemoryId and supersededMemoryId must not be null");
        }

        MemoryRelation relation = new MemoryRelation(
                UUID.randomUUID().toString(),
                DatabaseConstants.RelationParty.MEMORY,
                activeMemoryId,
                RelationType.SUPERSEDES,
                DatabaseConstants.RelationParty.MEMORY,
                supersededMemoryId,
                Provenance.USER_ENTERED,
                1.0,
                nowMillis,
                null
        );

        relationRepository.save(relation);

        return new SupersessionInfo(activeMemoryId, supersededMemoryId, nowMillis, null, null);
    }

    /**
     * Checks whether the specified memory has been superseded by any other active memory.
     *
     * @param memoryId the UUID of the memory to check; null returns false
     * @return {@code true} if a target {@code SUPERSEDES} edge exists pointing to this memory; {@code false} otherwise
     */
    public boolean isSuperseded(String memoryId) {
        return relationRepository.isSuperseded(memoryId);
    }

    /**
     * Retrieves the entire supersession history chain for the specified memory.
     *
     * @param memoryId the UUID of the memory whose lineage should be fetched
     * @return list of {@link MemoryRelation} records with {@link RelationType#SUPERSEDES}; never null
     */
    public List<MemoryRelation> getSupersessionChain(String memoryId) {
        return relationRepository.findSupersessionChain(memoryId);
    }
}
