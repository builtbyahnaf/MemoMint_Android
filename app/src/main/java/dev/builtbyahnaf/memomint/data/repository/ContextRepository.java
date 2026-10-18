package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.context.ExtractionResult;
import dev.builtbyahnaf.memomint.domain.context.MemoryRelation;

/**
 * Repository interface for persisting and querying extracted context records, entities, and memory relations.
 *
 * <h2>Architectural Purpose (FR-07, Section 11 &amp; Phase 3 Plan)</h2>
 * <p>Encapsulates all persistence contracts for derived memory context:
 * <ul>
 *   <li>Saving and querying raw extracted context facts ({@link ExtractionResult}) in {@code context_records}.</li>
 *   <li>Upserting shared normalized entities ({@code entities}) and linking them via {@code memory_entities}.</li>
 *   <li>Querying memories sharing common entities for depth-1 graph inference.</li>
 *   <li>Persisting and retrieving bidirectional memory relations ({@link MemoryRelation}) in {@code memory_relations}.</li>
 * </ul>
 * </p>
 */
public interface ContextRepository {

    /**
     * Persists a batch of extracted context results for a given memory.
     *
     * @param results  list of extracted facts; must not be null
     * @param memoryId target memory UUID; must not be null
     */
    void saveContextRecords(List<ExtractionResult> results, String memoryId);

    /**
     * Retrieves all persisted context facts associated with a memory.
     *
     * @param memoryId target memory UUID
     * @return list of extraction results; never null
     */
    List<ExtractionResult> getContextForMemory(String memoryId);

    /**
     * Deletes all context records belonging to a memory.
     *
     * @param memoryId target memory UUID
     */
    void deleteContextForMemory(String memoryId);

    /**
     * Inserts an entity if it does not already exist.
     *
     * @param canonicalLabel normalized canonical form
     * @param entityType     entity category (e.g. ITEM_NAME, LOCATION)
     * @param originalLabel  verbatim original label
     */
    void upsertEntity(String canonicalLabel, String entityType, String originalLabel);

    /**
     * Retrieves an existing entity ID matching canonical label and type, or creates one if absent.
     *
     * @param canonicalLabel normalized canonical form
     * @param entityType     entity category
     * @param originalLabel  verbatim original label
     * @return stable UUID of the entity
     */
    String getOrCreateEntityId(String canonicalLabel, String entityType, String originalLabel);

    /**
     * Links a memory to an entity with a specific role.
     *
     * @param memoryId       target memory UUID
     * @param entityId       entity UUID
     * @param role           semantic role (e.g. ITEM_NAME, LOCATION)
     * @param canonicalLabel entity canonical label
     * @param entityType     entity type
     */
    void saveMemoryEntityLink(String memoryId, String entityId, String role, String canonicalLabel, String entityType);

    /**
     * Finds all memory IDs that reference an entity by its canonical label.
     *
     * @param canonicalLabel canonical label to search for
     * @return list of memory IDs referencing this entity; never null
     */
    List<String> findMemoriesByEntityLabel(String canonicalLabel);

    /**
     * Persists a batch of memory-memory relations.
     *
     * @param relations list of relations to store
     */
    void saveMemoryRelations(List<MemoryRelation> relations);

    /**
     * Checks if a directed relation of a given type already exists between two memories.
     *
     * @param sourceMemoryId source memory UUID
     * @param targetMemoryId target memory UUID
     * @param relationType   relation type string
     * @return true if the relation row exists
     */
    boolean relationExists(String sourceMemoryId, String targetMemoryId, String relationType);

    /**
     * Returns all relations where the specified memory is either source or target.
     *
     * @param memoryId target memory UUID
     * @return list of relations; never null
     */
    List<MemoryRelation> getRelationsForMemory(String memoryId);

    /**
     * Deletes all relations where the specified memory is either source or target.
     *
     * @param memoryId target memory UUID
     */
    void deleteRelationsForMemory(String memoryId);

    /**
     * Atomically deletes both context records and relations for the specified memory.
     *
     * @param memoryId target memory UUID
     */
    void deleteRelationsAndContextForMemory(String memoryId);
}
