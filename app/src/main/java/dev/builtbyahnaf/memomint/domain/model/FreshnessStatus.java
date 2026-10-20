package dev.builtbyahnaf.memomint.domain.model;

/**
 * Classifies the temporal confidence level of a memory record.
 *
 * <h2>Purpose</h2>
 * <p>Not all memories age at the same rate. A memory that records an evergreen fact — such as
 * a piece of trivia or a permanent filing-cabinet location — should never be considered stale.
 * A memory that records where the user left their keys last night should decay quickly. This
 * enum provides a five-level classification that lets presentation and retrieval logic apply
 * different trust weights and visual cues depending on how confident we are that the stored
 * information is still accurate.</p>
 *
 * <h2>Usage</h2>
 * <p>Typically produced by a freshness-evaluation service that combines the elapsed time since
 * the memory was last confirmed, the configured check-in interval in {@link ConfirmationPolicy},
 * and any EVERGREEN tagging on the memory itself. The result is returned as part of a
 * {@link FreshnessReport}.</p>
 *
 * @see FreshnessReport
 * @see ConfirmationPolicy
 */
public enum FreshnessStatus {

    /**
     * The memory was confirmed recently and is considered fully up-to-date.
     *
     * <p>The user has explicitly verified — or re-entered — this information within the
     * active confirmation window. Retrieval logic may present memories with this status
     * at the top of results without any staleness warning.</p>
     */
    CONFIRMED_FRESH,

    /**
     * The memory has not been confirmed for a while but has not yet crossed the stale threshold.
     *
     * <p>The memory is still serviceable, but the information may have drifted. The UI may
     * display a soft visual hint encouraging the user to verify the information soon, without
     * blocking the result.</p>
     */
    AGING,

    /**
     * The memory is old enough that its accuracy is in serious doubt.
     *
     * <p>The last confirmation is significantly older than the configured check-in interval.
     * Retrieval results should show a clear staleness warning. The confirmation-policy
     * scheduler should prioritise generating a check-in prompt for this memory.</p>
     */
    STALE,

    /**
     * The memory has never been explicitly confirmed by the user.
     *
     * <p>This status applies to memories that were created with a provenance other than
     * {@link Provenance#CONFIRMED} and for which no subsequent confirmation has been recorded.
     * The claim may still be accurate, but it lacks any explicit user verification. The UI
     * should distinguish these memories from actively confirmed ones.</p>
     */
    UNCONFIRMED,

    /**
     * The memory describes information that is inherently stable over time and does not decay.
     *
     * <p>Evergreen memories are exempt from freshness scheduling. Examples include a general
     * reference note, a permanent address, or a fact that the user has marked as not subject
     * to change. The confirmation-policy scheduler must skip these memories when generating
     * check-in prompts.</p>
     */
    EVERGREEN
}
