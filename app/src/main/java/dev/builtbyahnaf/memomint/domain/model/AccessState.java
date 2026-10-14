package dev.builtbyahnaf.memomint.domain.model;

/**
 * Represents the access and indexing health of a referenced file.
 *
 * <p>Android does not grant unrestricted filesystem access. A file reference may become
 * inaccessible for different reasons: the permission was revoked, the file was deleted,
 * the file was moved, or it was never verified at all. The UI must distinguish these
 * cases rather than collapsing them into a single "file not found" message (Section 15.3).</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum AccessState {

    /**
     * The file was successfully accessed the last time it was checked.
     */
    ACCESSIBLE,

    /**
     * The file has been registered but not yet checked.
     * Initial state for newly added file references.
     */
    NOT_YET_VERIFIED,

    /**
     * The file could not be accessed during the last health check, but it may be a
     * temporary condition (e.g., external storage disconnected).
     */
    TEMPORARILY_INACCESSIBLE,

    /**
     * The file appears to be gone (no content URI resolves, or the path is absent).
     * This is a best-effort assessment; the application cannot confirm that it was deleted.
     */
    MISSING_OR_UNAVAILABLE,

    /**
     * The persistent URI permission was explicitly revoked or was never granted.
     * Distinct from the file being deleted: the file may still exist but the app cannot read it.
     */
    PERMISSION_REVOKED,

    /**
     * The file is accessible but the index is out of date and needs to be rebuilt.
     */
    REINDEX_REQUIRED
}
