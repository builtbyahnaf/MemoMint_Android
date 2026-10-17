package dev.builtbyahnaf.memomint.domain.model;

/**
 * Tracks a long-running or resumable document-indexing operation.
 *
 * <h2>Purpose</h2>
 * <p>Document indexing may take significant time for large files and may be interrupted
 * by the OS, low battery, or the user. IndexJob records allow the system to resume
 * interrupted work without reprocessing files that were already completed, and to report
 * indexing progress and failure details to the user (Section 13.2, FR-13).</p>
 *
 * <h2>Idempotency</h2>
 * <p>Before starting a new IndexJob for a file, the system should check whether an
 * existing PENDING or RUNNING job already exists for the same source. Starting a duplicate
 * job must be avoided (NFR-03).</p>
 *
 * <h2>Error codes</h2>
 * <p>When a job fails, {@code errorCode} should contain a short, stable identifier
 * (e.g., "PARSE_FAILURE", "PERMISSION_DENIED", "OOM") that the system and UI can
 * use to decide whether to retry, report to the user, or mark the file as permanently
 * excluded. Error codes must not contain full memory content or private document text.</p>
 */
public class IndexJob {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The file reference this job is processing.
     * Foreign key to {@link FileReference#getId()}.
     */
    private String sourceId;

    /**
     * Current execution state.
     * Must be one of: "PENDING", "RUNNING", "DONE", "FAILED", "CANCELLED".
     * Valid values are defined in {@code DatabaseConstants.IndexJobStatus}.
     */
    private String status;

    /**
     * Progress percentage, in the range [0, 100].
     * 0 means not yet started or unknown; 100 means complete.
     * This is an approximation for large files that are processed in chunks.
     */
    private int progress;

    /**
     * When this job started executing, in epoch milliseconds (UTC).
     * Null if the job has not started yet.
     */
    private Long startedAt;

    /**
     * When this record was last updated (status change, progress update),
     * in epoch milliseconds (UTC). Always set.
     */
    private long updatedAt;

    /**
     * A short, stable error code if the job failed.
     * Null if the job succeeded or has not failed yet.
     *
     * <p>Must not contain private document content or user memory text.</p>
     */
    private String errorCode;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public IndexJob() {}

    /**
     * Full constructor.
     *
     * @param id        stable UUID
     * @param sourceId  UUID of the FileReference being processed
     * @param status    job status string; see DatabaseConstants.IndexJobStatus
     * @param progress  completion percentage [0–100]
     * @param startedAt execution start epoch ms; null if not started
     * @param updatedAt last update epoch ms
     * @param errorCode short error code on failure; may be null
     */
    public IndexJob(
            String id,
            String sourceId,
            String status,
            int progress,
            Long startedAt,
            long updatedAt,
            String errorCode
    ) {
        this.id = id;
        this.sourceId = sourceId;
        this.status = status;
        this.progress = progress;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.errorCode = errorCode;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    /**
     * Gets the stable UUID identifier for this indexing job.
     *
     * @return stable UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the stable UUID identifier for this indexing job.
     *
     * @param id stable UUID string
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the UUID of the {@link FileReference} being processed.
     *
     * @return foreign key UUID matching {@link FileReference#getId()}
     */
    public String getSourceId() {
        return sourceId;
    }

    /**
     * Sets the UUID of the {@link FileReference} being processed.
     *
     * @param sourceId foreign key UUID
     */
    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    /**
     * Gets the current execution lifecycle status string.
     *
     * <p>Permitted values are defined in {@code DatabaseConstants.IndexJobStatus}:
     * <ul>
     *   <li>{@code "PENDING"}: Job enqueued but not yet executing.</li>
     *   <li>{@code "RUNNING"}: Worker currently parsing or indexing file chunks.</li>
     *   <li>{@code "DONE"}: Terminal success state.</li>
     *   <li>{@code "FAILED"}: Terminal error state; check {@link #getErrorCode()}.</li>
     *   <li>{@code "CANCELLED"}: Terminal aborted state (user cancelled or file removed).</li>
     * </ul>
     *
     * @return current status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the execution lifecycle status string.
     *
     * @param status status string from {@code DatabaseConstants.IndexJobStatus}
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets the estimated completion progress as a percentage.
     *
     * <p><b>Range & Calculation:</b> Integer in the interval [0, 100]. For multi-page documents (PDFs)
     * or large text files, progress is computed as {@code (processedChunks / totalEstimatedChunks) * 100}.
     * 0 indicates unstarted or indeterminate; 100 indicates complete.</p>
     *
     * @return progress percentage in [0, 100]
     */
    public int getProgress() {
        return progress;
    }

    /**
     * Sets the progress percentage.
     *
     * @param progress integer in [0, 100]
     */
    public void setProgress(int progress) {
        this.progress = progress;
    }

    /**
     * Gets the timestamp when execution commenced.
     *
     * <p><b>Crash Detection:</b> If a job remains in {@code "RUNNING"} status across process restarts
     * without progress updates for longer than a timeout threshold, this timestamp allows background
     * recovery logic to detect the crash and reschedule the job as {@code "PENDING"}.</p>
     *
     * @return start timestamp in UTC epoch milliseconds, or null if not yet started
     */
    public Long getStartedAt() {
        return startedAt;
    }

    /**
     * Sets the start timestamp.
     *
     * @param startedAt UTC epoch milliseconds, or null
     */
    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * Gets the timestamp of the most recent status or progress heartbeat.
     *
     * @return UTC epoch milliseconds
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the timestamp of the most recent update.
     *
     * @param updatedAt UTC epoch milliseconds
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Gets the machine-readable error code if the job failed.
     *
     * <p><b>Privacy Rule (Section 16.1, Section 25.7):</b> Error codes must be short, categorical
     * constants (e.g., "PARSE_FAILURE", "PERMISSION_DENIED", "OUT_OF_MEMORY"). They must NEVER contain
     * document contents, user memory text, or private file names.</p>
     *
     * @return error code string, or null if no failure occurred
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Sets the machine-readable error code.
     *
     * @param errorCode privacy-safe error identifier, or null
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Checks if this job has reached a terminal execution state from which no further transitions occur.
     *
     * <p><b>Algorithm:</b> Tests whether {@code status} equals "DONE", "FAILED", or "CANCELLED".
     * Terminal jobs are not re-executed by background workers.</p>
     *
     * @return true if the job is finished, failed, or cancelled; false if pending or actively running
     */
    public boolean isTerminal() {
        return "DONE".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    @Override
    public String toString() {
        return "IndexJob{"
                + "id='" + id + '\''
                + ", sourceId='" + sourceId + '\''
                + ", status='" + status + '\''
                + ", progress=" + progress
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexJob)) return false;
        IndexJob other = (IndexJob) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
