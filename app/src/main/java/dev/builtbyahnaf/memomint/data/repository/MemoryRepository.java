package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Repository interface for {@link Memory} CRUD operations.
 *
 * <h2>Design contract</h2>
 * <p>This interface defines what the application layer can do with memories, without
 * exposing how the data is stored. The SQLite implementation ({@link MemoryRepositoryImpl})
 * is the default; alternative implementations (e.g., in-memory for tests) can be provided
 * without changing any callers.</p>
 *
 * <h2>Original-content preservation (FR-03)</h2>
 * <p>The {@code save} and {@code update} methods must never overwrite {@code originalText}
 * with generated context. The implementation must enforce this by treating {@code originalText}
 * as a separate, protected column.</p>
 *
 * <h2>Cascade deletion (FR-14)</h2>
 * <p>When {@code delete} is called, the implementation must ensure that all associated
 * ContextRecords, MemoryEntities, Reminders, and ConfirmationPolicies are removed.
 * In the SQLite implementation this is handled by {@code ON DELETE CASCADE} on the FK
 * columns; but the interface contract makes this expectation explicit.</p>
 *
 * <h2>Soft vs. hard delete</h2>
 * <p>{@link #softDelete} marks the memory as {@link MemoryStatus#DELETED} and excludes
 * it from search immediately. {@link #hardDelete} removes the row and triggers cascade
 * cleanup of all derived records. The application layer should prefer soft delete first
 * to allow recovery, and may schedule a hard delete for later cleanup.</p>
 *
 * <h2>Thread safety</h2>
 * <p>Implementations must be safe to call from a background thread. The application layer
 * is responsible for not calling these methods from the main thread.</p>
 */
public interface MemoryRepository {

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Inserts a new Memory into the database.
     *
     * <p>The caller is responsible for setting a valid {@code id} (UUID) and timestamps
     * before calling this method. The repository does not generate IDs or timestamps.</p>
     *
     * @param memory the memory to insert; must not be null; must have a non-null id
     * @return the stable id of the inserted memory
     * @throws IllegalArgumentException if {@code memory} or its id is null
     * @throws RuntimeException         if the insert fails due to a database error
     */
    String save(Memory memory);

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Returns the memory with the given id, or {@code null} if not found.
     *
     * <p>Returns memories in any status including DELETED. Callers that want only
     * active memories should use {@link #findByStatus} or check the returned status.</p>
     *
     * @param id the stable UUID of the memory
     * @return the {@link Memory}, or {@code null} if no record exists with that id
     */
    Memory findById(String id);

    /**
     * Returns all memories with the given status, ordered by {@code created_at} descending
     * (most recently created first).
     *
     * @param status the lifecycle status to filter by; must not be null
     * @return a list of matching memories; never null; may be empty
     */
    List<Memory> findByStatus(MemoryStatus status);

    /**
     * Returns all memories of the given type and status, ordered by {@code created_at} descending.
     *
     * @param type   the memory type to filter by; must not be null
     * @param status the lifecycle status to filter by; must not be null
     * @return a list of matching memories; never null; may be empty
     */
    List<Memory> findByTypeAndStatus(MemoryType type, MemoryStatus status);

    /**
     * Returns all memories, regardless of status, ordered by {@code created_at} descending.
     *
     * <p>This method is primarily intended for export and debugging. The UI should use
     * {@link #findByStatus} to show only relevant records.</p>
     *
     * @return all memories; never null; may be empty
     */
    List<Memory> findAll();

    /**
     * Returns the total number of memories with the given status.
     *
     * @param status the lifecycle status to count; must not be null
     * @return count of matching memories
     */
    int countByStatus(MemoryStatus status);

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Updates an existing memory in the database.
     *
     * <p><b>Original text rule:</b> the {@code originalText} field in the stored record
     * must only be changed if the caller explicitly sets it on the {@code memory} object
     * AND the change was triggered by a user edit action, not by context generation.
     * The implementation does not enforce intent, but it must faithfully write whatever
     * {@code originalText} value is provided — callers must not pass generated text here.</p>
     *
     * <p>The {@code updatedAt} field on the passed-in memory should be set by the caller
     * before calling this method.</p>
     *
     * @param memory the memory with updated fields; must have a valid id matching an
     *               existing row
     * @return true if the update modified a row; false if no matching id was found
     * @throws IllegalArgumentException if {@code memory} or its id is null
     */
    boolean update(Memory memory);

    /**
     * Updates only the {@code status} field of the memory with the given id.
     *
     * <p>This is a targeted update used by the soft-delete and archive flows to avoid
     * accidentally overwriting other fields that might have changed concurrently.</p>
     *
     * @param id     the stable UUID of the memory to update
     * @param status the new status to set
     * @return true if the update modified a row; false if no matching id was found
     */
    boolean updateStatus(String id, MemoryStatus status);

    /**
     * Records a user confirmation for the memory with the given id by setting
     * {@code last_confirmed_at} to the provided timestamp and updating {@code updated_at}.
     *
     * @param id              the stable UUID of the memory to confirm
     * @param confirmedAtMs   the confirmation timestamp in epoch milliseconds (UTC)
     * @return true if the update modified a row; false if no matching id was found
     */
    boolean recordConfirmation(String id, long confirmedAtMs);

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Marks the memory with the given id as {@link MemoryStatus#DELETED} without removing
     * the database row.
     *
     * <p>The memory is immediately excluded from normal search results. Associated records
     * (context, reminders, etc.) are still present and will be cleaned up by a hard delete
     * or a scheduled cleanup job.</p>
     *
     * @param id the stable UUID of the memory to soft-delete
     * @return true if a row was updated; false if no matching id was found
     */
    boolean softDelete(String id);

    /**
     * Permanently removes the memory row with the given id from the database.
     *
     * <p>All associated ContextRecords, MemoryEntities, Reminders, and ConfirmationPolicies
     * are removed by cascade. Shared entities (Entity records) are NOT deleted because they
     * may be referenced by other memories.</p>
     *
     * <p>Call this only after the user has confirmed permanent deletion, or from a scheduled
     * cleanup job processing memories already in DELETED status.</p>
     *
     * @param id the stable UUID of the memory to permanently remove
     * @return true if a row was deleted; false if no matching id was found
     */
    boolean hardDelete(String id);
}
