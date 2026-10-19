package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;

/**
 * Repository interface for managing {@link FileReference} entities.
 *
 * <h2>Purpose & Architectural Invariants (Section 11.6, Phase 4 Plan §6.3)</h2>
 * <p>Belongs to Layer C (Data Access). Handles CRUD persistence of tracked documents,
 * their health states, and indexing statuses.</p>
 */
public interface FileReferenceRepository {

    /**
     * Persists a new or updated file reference into SQLite storage.
     *
     * @param fileReference the file reference to persist; must not be null
     */
    void save(FileReference fileReference);

    /**
     * Retrieves a file reference by its unique UUID.
     *
     * @param id stable identifier
     * @return matching FileReference, or null if not found
     */
    FileReference findById(String id);

    /**
     * Retrieves a file reference matching the exact content URI or filesystem path.
     *
     * @param uriOrPath URI or path string
     * @return matching FileReference, or null if not found
     */
    FileReference findByUri(String uriOrPath);

    /**
     * Retrieves all tracked file references up to the specified limit, ordered by display name.
     *
     * @param limit maximum records to return
     * @return list of file references
     */
    List<FileReference> findAll(int limit);

    /**
     * Retrieves all file references matching a specific access health state.
     *
     * @param state target AccessState
     * @return list of matching file references
     */
    List<FileReference> findByAccessState(AccessState state);

    /**
     * Updates only the access state and last verification timestamp of a file reference.
     *
     * @param id         file reference UUID
     * @param state      new access health state
     * @param verifiedAt timestamp in epoch milliseconds
     */
    void updateAccessState(String id, AccessState state, long verifiedAt);

    /**
     * Updates the indexing status, indexed timestamp, and fingerprint of a file reference.
     *
     * @param id          file reference UUID
     * @param status      new indexing status
     * @param indexedAt   timestamp in epoch milliseconds
     * @param fingerprint cryptographic hash of indexed content, or null
     */
    void updateIndexingStatus(String id, IndexingStatus status, Long indexedAt, String fingerprint);

    /**
     * Deletes a file reference from persistence.
     *
     * @param id file reference UUID
     */
    void delete(String id);
}
