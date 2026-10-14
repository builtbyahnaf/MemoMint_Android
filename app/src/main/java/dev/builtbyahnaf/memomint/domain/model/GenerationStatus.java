package dev.builtbyahnaf.memomint.domain.model;

/**
 * Tracks the completion state of context generation for a ContextRecord.
 *
 * <p>Context generation may fail or be interrupted. This status lets the system track
 * which records need to be generated, retried, or have already succeeded, without
 * corrupting the original memory on failure (FR-03, NFR-08).</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum GenerationStatus {

    /**
     * Context generation has been requested but not yet started.
     * The record may have been created as a placeholder during memory creation.
     */
    PENDING,

    /**
     * Context generation completed successfully.
     * The content field contains usable, searchable derived text.
     */
    DONE,

    /**
     * Context generation failed.
     * The content field should not be used for retrieval. The original memory is unaffected.
     * A failed generation must not erase any previously valid DONE record.
     */
    FAILED
}
