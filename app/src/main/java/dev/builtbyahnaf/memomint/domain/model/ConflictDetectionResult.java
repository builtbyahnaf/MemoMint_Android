package dev.builtbyahnaf.memomint.domain.model;

/**
 * Result object returned by the conflict-detection engine when a new memory is saved.
 *
 * <h2>Purpose</h2>
 * <p>When the user creates a new memory (especially a {@link MemoryType#PHYSICAL_ITEM} memory),
 * the system checks whether an existing active memory already asserts a location for the same
 * physical item. If a contradiction is detected, a {@code ConflictDetectionResult} with
 * {@link #isConflictFound()} {@code = true} is returned so the presentation layer can prompt
 * the user to choose between superseding the old record or keeping both independently
 * (Section 15.4, Phase 5 Plan §2.4).</p>
 *
 * <h2>Non-destructive semantics</h2>
 * <p>A conflict report does <em>not</em> automatically modify any data. It is purely advisory.
 * The system waits for explicit user intent before creating any SUPERSEDES relation.</p>
 *
 * <h2>Factory methods</h2>
 * <p>Use {@link #noConflict(String)} when no contradiction was detected and
 * {@link #conflict(String, String, String, String, String)} when one was.</p>
 *
 * @see dev.builtbyahnaf.memomint.domain.trust.ConflictDetector
 * @see RelationType#SUPERSEDES
 */
public final class ConflictDetectionResult {

    /** Whether a conflicting memory was found. */
    private final boolean conflictFound;

    /** UUID of the memory that was just being evaluated (the "new" memory). */
    private final String newMemoryId;

    /**
     * UUID of the existing active memory that conflicts with the new one.
     * {@code null} when {@link #conflictFound} is {@code false}.
     */
    private final String conflictingMemoryId;

    /**
     * The normalised label of the shared entity (e.g. "passport", "spare keys") that both
     * memories reference. {@code null} when no conflict was found.
     */
    private final String sharedEntityLabel;

    /**
     * Short human-readable summary of the location asserted by the existing conflicting memory.
     * {@code null} when no conflict was found.
     */
    private final String existingLocationSummary;

    /**
     * Short human-readable summary of the location asserted by the new memory.
     * {@code null} when no conflict was found.
     */
    private final String newLocationSummary;

    // -------------------------------------------------------------------------
    // Private constructor (use factory methods)
    // -------------------------------------------------------------------------

    private ConflictDetectionResult(
            boolean conflictFound,
            String newMemoryId,
            String conflictingMemoryId,
            String sharedEntityLabel,
            String existingLocationSummary,
            String newLocationSummary
    ) {
        this.conflictFound = conflictFound;
        this.newMemoryId = newMemoryId;
        this.conflictingMemoryId = conflictingMemoryId;
        this.sharedEntityLabel = sharedEntityLabel;
        this.existingLocationSummary = existingLocationSummary;
        this.newLocationSummary = newLocationSummary;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Creates a result indicating that no conflict was detected for the given new memory.
     *
     * @param newMemoryId UUID of the new memory that was evaluated; must not be null
     * @return a {@code ConflictDetectionResult} with {@link #isConflictFound()} = {@code false}
     */
    public static ConflictDetectionResult noConflict(String newMemoryId) {
        return new ConflictDetectionResult(false, newMemoryId, null, null, null, null);
    }

    /**
     * Creates a result indicating that a location conflict was detected.
     *
     * @param newMemoryId             UUID of the new memory being saved
     * @param conflictingMemoryId     UUID of the existing memory that contradicts the new one
     * @param sharedEntityLabel       normalised label of the item both memories reference
     * @param existingLocationSummary display text for the old assertion
     * @param newLocationSummary      display text for the new assertion
     * @return a {@code ConflictDetectionResult} with {@link #isConflictFound()} = {@code true}
     */
    public static ConflictDetectionResult conflict(
            String newMemoryId,
            String conflictingMemoryId,
            String sharedEntityLabel,
            String existingLocationSummary,
            String newLocationSummary
    ) {
        return new ConflictDetectionResult(
                true,
                newMemoryId,
                conflictingMemoryId,
                sharedEntityLabel,
                existingLocationSummary,
                newLocationSummary
        );
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Indicates whether the conflict detector found a contradiction between the new memory
     * and an existing active memory.
     *
     * @return {@code true} if a conflict was found; {@code false} otherwise
     */
    public boolean isConflictFound() {
        return conflictFound;
    }

    /**
     * Returns the UUID of the memory that was being evaluated (the "new" memory).
     *
     * @return non-null UUID string
     */
    public String getNewMemoryId() {
        return newMemoryId;
    }

    /**
     * Returns the UUID of the existing memory that conflicts with the new one.
     *
     * @return UUID string, or {@code null} if no conflict was found
     */
    public String getConflictingMemoryId() {
        return conflictingMemoryId;
    }

    /**
     * Returns the normalised label of the entity both memories reference.
     *
     * <p>For example, if both memories mention "passport", this returns "passport".</p>
     *
     * @return normalised entity label, or {@code null} if no conflict
     */
    public String getSharedEntityLabel() {
        return sharedEntityLabel;
    }

    /**
     * Returns a short display summary of what the existing memory asserts.
     *
     * @return location summary string, or {@code null} if no conflict
     */
    public String getExistingLocationSummary() {
        return existingLocationSummary;
    }

    /**
     * Returns a short display summary of what the new memory asserts.
     *
     * @return location summary string, or {@code null} if no conflict
     */
    public String getNewLocationSummary() {
        return newLocationSummary;
    }

    @Override
    public String toString() {
        return "ConflictDetectionResult{"
                + "conflictFound=" + conflictFound
                + ", newMemoryId='" + newMemoryId + '\''
                + ", conflictingMemoryId='" + conflictingMemoryId + '\''
                + ", sharedEntityLabel='" + sharedEntityLabel + '\''
                + '}';
    }
}
