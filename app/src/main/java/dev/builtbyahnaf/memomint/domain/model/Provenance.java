package dev.builtbyahnaf.memomint.domain.model;

/**
 * Describes the origin of a Memory, ContextRecord, MemoryRelation, or MemoryEntity record.
 *
 * <p>Provenance is at the heart of MemoMint's trust model (Section 15.1). Every important
 * stored claim should carry a provenance value that lets the system — and the user — understand
 * how reliable the information is and whether it was ever explicitly verified.</p>
 *
 * <p>Key rule: an EXTRACTED or INFERRED value must never be presented as USER_ENTERED or
 * CONFIRMED without an explicit user confirmation step. The ranking and UI layers must
 * treat these levels differently.</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum Provenance {

    /**
     * The information was typed directly by the user.
     * Highest trustworthiness for literal wording. Still not guaranteed to be currently
     * accurate — the user may have recorded incorrect information.
     */
    USER_ENTERED,

    /**
     * The information came from a voice transcript that was committed without manual correction.
     * May contain speech-recognition errors. The raw transcript should be preserved.
     */
    VOICE_TRANSCRIBED,

    /**
     * The information was extracted from a document or file by the indexing pipeline.
     * Accuracy depends on parser quality and source document correctness.
     */
    EXTRACTED,

    /**
     * The information was produced by a deterministic rule or heuristic applied to other
     * known information. Inferred claims must not be represented as confirmed facts.
     */
    INFERRED,

    /**
     * The user explicitly confirmed that this information is correct.
     * The most reliable provenance level for time-sensitive claims like physical locations.
     */
    CONFIRMED,

    /**
     * The information was produced by a local machine-learning model.
     * Reserved for future use; not used in the initial implementation.
     */
    MODEL_GENERATED
}
