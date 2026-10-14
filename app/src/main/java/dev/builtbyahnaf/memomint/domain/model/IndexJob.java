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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** @return progress in [0, 100] */
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    /** @return true if this job is in a terminal state (DONE, FAILED, or CANCELLED) */
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
