package dev.builtbyahnaf.memomint.domain.model;

/**
 * Describes how a Memory was originally entered into the system.
 *
 * <p>Source type is recorded at creation time and never changed. It contributes to provenance
 * tracking (Section 15.1 of the spec) and is used by the trust layer to distinguish
 * user-typed facts from voice transcripts or imported content.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum SourceType {

    /**
     * The user typed the memory directly into the application.
     * Highest trust for literal wording; the text represents the user's explicit intent.
     */
    TYPED,

    /**
     * The memory was created from a voice-recognition transcript.
     * The transcript may contain recognition errors; the raw transcript should be preserved
     * alongside the interpreted memory so the user can correct it.
     */
    VOICE,

    /**
     * The memory arrived through Android's share sheet from another application.
     * The shared text is treated as the original_text; the sharing application's package
     * may be stored in source_id for auditing.
     */
    SHARED,

    /**
     * The memory was imported from an external file or backup.
     * The import source reference should be retained in source_id.
     */
    IMPORTED
}
