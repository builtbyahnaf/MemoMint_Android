package dev.builtbyahnaf.memomint.domain.model;

/**
 * Tracks a file or document that has been indexed or referenced by a memory.
 *
 * <h2>Purpose</h2>
 * <p>A FileReference records where a document lives (via a content URI or path),
 * its last-known metadata, and its health state. It is the authoritative source for
 * file availability checks and re-indexing decisions.</p>
 *
 * <h2>Android storage model</h2>
 * <p>On Android, a content URI obtained via the Storage Access Framework (SAF) and a
 * persisted URI permission grant is more reliable than a raw filesystem path, because
 * scoped storage restricts what paths the application can open directly (Section 16.4).
 * The {@code uriOrPath} field should store the content URI when available.</p>
 *
 * <h2>Honesty about file state</h2>
 * <p>The application must never claim a file is deleted when it is only inaccessible
 * (Section 25.3). {@link AccessState} provides the vocabulary to distinguish these cases.</p>
 *
 * @see AccessState
 * @see IndexingStatus
 */
public class FileReference {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The content URI (preferred on Android) or filesystem path used to access this file.
     * Never null for a valid record. This is the authoritative reference; the display name
     * and other metadata are derived from it.
     */
    private String uriOrPath;

    /**
     * A human-readable filename or title for display purposes.
     * May differ from the actual filename if the user renamed the display label.
     */
    private String displayName;

    /** MIME type of the file, if known. Null if not determined. */
    private String mimeType;

    /** File size in bytes at last indexing, or null if unknown. */
    private Long sizeBytes;

    /**
     * The file's last-modified timestamp from the filesystem or content provider,
     * at the time of last indexing. Null if unavailable.
     */
    private Long lastModifiedAt;

    /**
     * When this file was last successfully indexed (text extracted and stored),
     * in epoch milliseconds. Null if never indexed.
     */
    private Long lastIndexedAt;

    /**
     * A content fingerprint (e.g., a hex-encoded SHA-256 hash) computed at indexing time,
     * used for change detection. Null if not computed.
     *
     * <p>A matching fingerprint strongly suggests the content has not changed, but is not a
     * guarantee — the file's URI could theoretically be reused for different content.
     * Combine with size and modification timestamp for more reliable detection.</p>
     */
    private String contentFingerprint;

    /** Current availability state of this file from the application's perspective. */
    private AccessState accessState;

    /**
     * When the application last attempted to verify the file's accessibility,
     * in epoch milliseconds. Null if never checked.
     */
    private Long lastVerifiedAt;

    /** Current text-indexing state. */
    private IndexingStatus indexingStatus;

    /**
     * Whether this file has been excluded from indexing.
     * True if the user or a sensitive-directory rule has excluded it.
     * Excluded files are not reprocessed by background indexing jobs.
     */
    private boolean excluded;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public FileReference() {}

    /**
     * Full constructor.
     *
     * @param id                 stable UUID
     * @param uriOrPath          content URI or path; must not be null
     * @param displayName        human-readable name
     * @param mimeType           MIME type; may be null
     * @param sizeBytes          size in bytes; may be null
     * @param lastModifiedAt     filesystem modification time; may be null
     * @param lastIndexedAt      last successful indexing time; may be null
     * @param contentFingerprint content hash; may be null
     * @param accessState        current accessibility state
     * @param lastVerifiedAt     last verification time; may be null
     * @param indexingStatus     text indexing state
     * @param excluded           true if excluded from indexing
     */
    public FileReference(
            String id,
            String uriOrPath,
            String displayName,
            String mimeType,
            Long sizeBytes,
            Long lastModifiedAt,
            Long lastIndexedAt,
            String contentFingerprint,
            AccessState accessState,
            Long lastVerifiedAt,
            IndexingStatus indexingStatus,
            boolean excluded
    ) {
        this.id = id;
        this.uriOrPath = uriOrPath;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.lastModifiedAt = lastModifiedAt;
        this.lastIndexedAt = lastIndexedAt;
        this.contentFingerprint = contentFingerprint;
        this.accessState = accessState;
        this.lastVerifiedAt = lastVerifiedAt;
        this.indexingStatus = indexingStatus;
        this.excluded = excluded;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUriOrPath() { return uriOrPath; }
    public void setUriOrPath(String uriOrPath) { this.uriOrPath = uriOrPath; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Long getLastModifiedAt() { return lastModifiedAt; }
    public void setLastModifiedAt(Long lastModifiedAt) { this.lastModifiedAt = lastModifiedAt; }

    public Long getLastIndexedAt() { return lastIndexedAt; }
    public void setLastIndexedAt(Long lastIndexedAt) { this.lastIndexedAt = lastIndexedAt; }

    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String contentFingerprint) { this.contentFingerprint = contentFingerprint; }

    public AccessState getAccessState() { return accessState; }
    public void setAccessState(AccessState accessState) { this.accessState = accessState; }

    public Long getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(Long lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public IndexingStatus getIndexingStatus() { return indexingStatus; }
    public void setIndexingStatus(IndexingStatus indexingStatus) { this.indexingStatus = indexingStatus; }

    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }

    /** @return true if the file is currently accessible and indexed */
    public boolean isReadyToSearch() {
        return AccessState.ACCESSIBLE.equals(accessState)
                && IndexingStatus.INDEXED.equals(indexingStatus);
    }

    @Override
    public String toString() {
        return "FileReference{"
                + "id='" + id + '\''
                + ", displayName='" + displayName + '\''
                + ", accessState=" + accessState
                + ", indexingStatus=" + indexingStatus
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileReference)) return false;
        FileReference other = (FileReference) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
