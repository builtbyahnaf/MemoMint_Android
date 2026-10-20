package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.MemoryRelation;

/**
 * Repository interface for CRUD operations on {@link MemoryRelation} records (graph edges).
 *
 * <h2>Design contract</h2>
 * <p>The knowledge graph in MemoMint (FR-07, Section 11.5) is stored as directed edges in the
 * {@code memory_relations} table. This interface abstracts all persistence operations so that
 * callers in the domain and application layers are decoupled from SQLite-specific code.</p>
 *
 * <h2>SUPERSEDES relations (Phase 5)</h2>
 * <p>A core Phase 5 invariant is that relocating a physical item must never delete the old
 * memory row. Instead a {@code SUPERSEDES} edge is inserted from the new memory to the old
 * one. The methods {@link #findSupersessionChain(String)} and {@link #isSuperseded(String)}
 * provide the query surface needed to enforce this invariant at query time (Section 15.4).</p>
 *
 * <h2>Thread safety</h2>
 * <p>Implementations must be safe to call from a background thread. The application layer
 * is responsible for dispatching calls off the Android main thread.</p>
 */
public interface MemoryRelationRepository {

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Inserts a new {@link MemoryRelation} edge into the knowledge graph.
     *
     * <p>The caller must populate the relation's {@code id} (UUID), both endpoints,
     * timestamps, and provenance before calling this method. The repository does not
     * generate IDs or timestamps.</p>
     *
     * @param relation the relation to insert; must not be null; must have a non-null id
     * @return the stable id string of the inserted relation
     * @throws IllegalArgumentException if {@code relation} or its id is null
     * @throws RuntimeException         if the insert fails due to a database constraint or I/O error
     */
    String save(MemoryRelation relation);

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the relation with the given id, or {@code null} if not found.
     *
     * @param id the stable UUID of the relation
     * @return the {@link MemoryRelation}, or {@code null} if no record exists with that id
     */
    MemoryRelation findById(String id);

    /**
     * Returns all relations that originate at the given source endpoint (forward graph traversal).
     *
     * <p>Equivalent to: {@code SELECT * FROM memory_relations WHERE source_type=? AND source_id=?}.</p>
     *
     * @param sourceType the type discriminator (e.g. {@code "MEMORY"} or {@code "ENTITY"})
     * @param sourceId   the UUID of the source record
     * @return list of matching relations; never null; may be empty
     */
    List<MemoryRelation> findBySource(String sourceType, String sourceId);

    /**
     * Returns all relations that terminate at the given target endpoint (reverse graph traversal).
     *
     * <p>Equivalent to: {@code SELECT * FROM memory_relations WHERE target_type=? AND target_id=?}.</p>
     *
     * @param targetType the type discriminator (e.g. {@code "MEMORY"} or {@code "ENTITY"})
     * @param targetId   the UUID of the target record
     * @return list of matching relations; never null; may be empty
     */
    List<MemoryRelation> findByTarget(String targetType, String targetId);

    /**
     * Returns all {@code SUPERSEDES} relations involving the given memory as either source or target.
     *
     * <p>Used by the Phase 5 supersession engine to build the full historical lineage chain
     * for a memory. The returned list may contain relations where {@code source_id=memoryId}
     * (meaning this memory supersedes another) and relations where {@code target_id=memoryId}
     * (meaning another memory supersedes this one).</p>
     *
     * <p>Equivalent to:
     * {@code SELECT * FROM memory_relations WHERE relation_type='SUPERSEDES' AND (source_id=? OR target_id=?)}.</p>
     *
     * @param memoryId the UUID of the memory whose supersession chain should be returned
     * @return list of SUPERSEDES relations involving this memory; never null; may be empty
     */
    List<MemoryRelation> findSupersessionChain(String memoryId);

    /**
     * Returns {@code true} if there exists an active memory that has a {@code SUPERSEDES}
     * relation pointing <em>at</em> the given memory (i.e., the given memory has been superseded).
     *
     * <p>Equivalent to:
     * {@code SELECT COUNT(*) FROM memory_relations WHERE relation_type='SUPERSEDES' AND target_id=?}.</p>
     *
     * @param memoryId the UUID of the memory to check
     * @return {@code true} if the memory has been superseded; {@code false} otherwise
     */
    boolean isSuperseded(String memoryId);

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Removes the relation with the given id from the knowledge graph.
     *
     * @param id the stable UUID of the relation to delete
     * @return {@code true} if a row was deleted; {@code false} if no matching id was found
     */
    boolean delete(String id);

    /**
     * Removes all relations whose source endpoint matches the given type and id.
     *
     * <p>Typically called when a source memory or entity is being permanently hard-deleted.
     * In practice, SQLite cascade rules on the {@code memories} table handle memory-sourced
     * edges automatically; this method is provided for explicit programmatic control or
     * for non-cascaded source types.</p>
     *
     * @param sourceType the type discriminator of the source
     * @param sourceId   the UUID of the source record
     */
    void deleteBySource(String sourceType, String sourceId);
}
