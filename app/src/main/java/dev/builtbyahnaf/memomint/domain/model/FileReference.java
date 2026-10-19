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

    /**
     * Gets the stable UUID identifier for this file reference.
     *
     * @return stable UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the stable UUID identifier for this file reference.
     *
     * @param id stable UUID string
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the content URI or filesystem path used to access this document.
     *
     * <p><b>Android Scoped Storage Tradeoff:</b> On modern Android (API 29+), content URIs obtained via
     * the Storage Access Framework (SAF) with persisted permissions
     * ({@code Intent.FLAG_GRANT_READ_URI_PERMISSION}) must be preferred over absolute filesystem paths.
     * Absolute paths often fail when app storage isolation or external media remounting occurs.</p>
     *
     * @return content URI string (e.g., "content://com.android.providers.media.documents/document/...")
     *         or absolute path; never null for valid records
     */
    public String getUriOrPath() {
        return uriOrPath;
    }

    /**
     * Sets the content URI or filesystem path for this document.
     *
     * @param uriOrPath content URI string or file path; must not be null
     */
    public void setUriOrPath(String uriOrPath) {
        this.uriOrPath = uriOrPath;
    }

    /**
     * Gets the human-readable display label or filename.
     *
     * <p>May differ from the filename extracted from the URI if the user provided a custom title.
     * Used in UI lists, search snippets, and breadcrumbs.</p>
     *
     * @return human-readable title or filename
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the human-readable display label or filename.
     *
     * @param displayName display label
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Gets the MIME type of the referenced file.
     *
     * <p>Guides parser selection during text extraction (e.g., "application/pdf" vs "text/plain").
     * May be null if undetermined or if the content provider did not declare a MIME type.</p>
     *
     * @return MIME type string or null
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Sets the MIME type of the referenced file.
     *
     * @param mimeType MIME type string or null
     */
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Gets the size of the file in bytes at the time of last indexing.
     *
     * <p>Used alongside {@link #getLastModifiedAt()} and {@link #getContentFingerprint()} for
     * lightweight change detection without reading the entire stream.</p>
     *
     * @return size in bytes, or null if unknown
     */
    public Long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Sets the size of the file in bytes.
     *
     * @param sizeBytes size in bytes, or null
     */
    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    /**
     * Gets the filesystem last-modified timestamp reported by the content provider or OS.
     *
     * @return last modified epoch milliseconds, or null if unavailable
     */
    public Long getLastModifiedAt() {
        return lastModifiedAt;
    }

    /**
     * Sets the filesystem last-modified timestamp.
     *
     * @param lastModifiedAt last modified epoch milliseconds, or null
     */
    public void setLastModifiedAt(Long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
    }

    /**
     * Gets the timestamp when this file was last successfully indexed into text chunks.
     *
     * @return UTC epoch milliseconds, or null if indexing has never completed
     */
    public Long getLastIndexedAt() {
        return lastIndexedAt;
    }

    /**
     * Sets the timestamp when this file was last successfully indexed.
     *
     * @param lastIndexedAt UTC epoch milliseconds, or null
     */
    public void setLastIndexedAt(Long lastIndexedAt) {
        this.lastIndexedAt = lastIndexedAt;
    }

    /**
     * Gets the cryptographic content fingerprint (e.g., hex-encoded SHA-256) of the document.
     *
     * <p><b>Algorithm & Tradeoffs:</b> A SHA-256 hash guarantees detection of any altered content
     * even if timestamps or file sizes match. However, computing the hash requires reading the full
     * file, which can be expensive on battery and I/O. Therefore, the system only recomputes the
     * hash when size or last-modified checks indicate potential changes.</p>
     *
     * @return hex-encoded hash string, or null if not yet computed
     */
    public String getContentFingerprint() {
        return contentFingerprint;
    }

    /**
     * Sets the cryptographic content fingerprint.
     *
     * @param contentFingerprint hex-encoded hash string, or null
     */
    public void setContentFingerprint(String contentFingerprint) {
        this.contentFingerprint = contentFingerprint;
    }

    /**
     * Gets the accessibility state of the file from the application's perspective.
     *
     * <p><b>Honesty Principle (Section 25.3):</b> The application must never report a file as "deleted"
     * when it is merely {@link AccessState#TEMPORARILY_INACCESSIBLE} (e.g. unmounted SD card) or
     * {@link AccessState#PERMISSION_REVOKED}. Honest states prevent false assumptions and data loss.</p>
     *
     * @return current {@link AccessState}
     */
    public AccessState getAccessState() {
        return accessState;
    }

    /**
     * Sets the accessibility state of the file.
     *
     * @param accessState current {@link AccessState}
     */
    public void setAccessState(AccessState accessState) {
        this.accessState = accessState;
    }

    /**
     * Gets the timestamp when file accessibility was last actively verified.
     *
     * @return UTC epoch milliseconds, or null if never verified
     */
    public Long getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    /**
     * Sets the timestamp when file accessibility was last actively verified.
     *
     * @param lastVerifiedAt UTC epoch milliseconds, or null
     */
    public void setLastVerifiedAt(Long lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    /**
     * Gets the document indexing lifecycle status.
     *
     * @return current {@link IndexingStatus}
     */
    public IndexingStatus getIndexingStatus() {
        return indexingStatus;
    }

    /**
     * Sets the document indexing lifecycle status.
     *
     * @param indexingStatus current {@link IndexingStatus}
     */
    public void setIndexingStatus(IndexingStatus indexingStatus) {
        this.indexingStatus = indexingStatus;
    }

    /**
     * Returns whether this document has been explicitly excluded from indexing.
     *
     * <p>Files may be excluded by user action or by security filters identifying sensitive directories
     * (e.g., SSH keys, credential caches, Section 16.3). Excluded files are never scanned by background jobs.</p>
     *
     * @return true if excluded from background indexing; false otherwise
     */
    public boolean isExcluded() {
        return excluded;
    }

    /**
     * Sets whether this document is excluded from indexing.
     *
     * @param excluded true to exclude; false otherwise
     */
    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    /**
     * Determines whether this file is fully prepared and eligible for retrieval queries.
     *
     * <p><b>Contract:</b> A document can only contribute to search results if its backing storage is
     * currently {@link AccessState#ACCESSIBLE} and its content is completely {@link IndexingStatus#INDEXED}.
     * Stale, missing, or pending files are excluded to maintain search result honesty.</p>
     *
     * @return true if the file is accessible and indexed; false otherwise
     */
    public boolean isReadyToSearch() {
        return AccessState.ACCESSIBLE.equals(accessState)
                && (IndexingStatus.INDEXED.equals(indexingStatus)
                    || IndexingStatus.DONE.equals(indexingStatus)
                    || IndexingStatus.DONE_WITHOUT_TEXT.equals(indexingStatus));
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
