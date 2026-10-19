package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.IndexJob;

/**
 * Repository interface for managing background document {@link IndexJob} records.
 *
 * <h2>Purpose & Architectural Invariants (Section 11.8, Phase 4 Plan §6.3)</h2>
 * <p>Belongs to Layer C (Data Access). Handles queuing, state transitions, progress tracking,
 * and error telemetry for document indexing jobs.</p>
 */
public interface IndexJobRepository {

    /**
     * Enqueues a new indexing job or updates an existing one.
     *
     * @param job IndexJob record to persist
     */
    void enqueue(IndexJob job);

    /**
     * Finds the oldest pending job in the queue.
     *
     * @return next pending IndexJob, or null if queue is empty
     */
    IndexJob findNextPendingJob();

    /**
     * Retrieves a job by its unique identifier.
     *
     * @param id job UUID
     * @return matching IndexJob, or null if not found
     */
    IndexJob findById(String id);

    /**
     * Retrieves the most recent job for a specific file reference.
     *
     * @param sourceId file reference UUID
     * @return matching IndexJob, or null if none exists
     */
    IndexJob findBySourceId(String sourceId);

    /**
     * Updates execution status, progress percentage, and error code.
     *
     * @param jobId     job UUID
     * @param status    new status string (e.g. RUNNING, DONE, FAILED)
     * @param progress  progress percentage [0, 100]
     * @param errorCode error code string if failed; null otherwise
     */
    void updateStatus(String jobId, String status, int progress, String errorCode);

    /**
     * Retrieves all currently active (PENDING or RUNNING) jobs.
     *
     * @return list of active IndexJob records
     */
    List<IndexJob> findActiveJobs();

    /**
     * Retrieves recent jobs sorted by updated timestamp descending.
     *
     * @param limit maximum records to return
     * @return list of recent jobs
     */
    List<IndexJob> findRecentJobs(int limit);
}
