package dev.builtbyahnaf.memomint.domain.model;

/**
 * Immutable value object that summarises the freshness evaluation for a single memory.
 *
 * <h2>Purpose</h2>
 * <p>A {@code FreshnessReport} is produced by the freshness-evaluation service each time a
 * memory is retrieved or a scheduled check-in sweep is run. It bundles the raw timing data
 * (elapsed milliseconds, last confirmation timestamp) with the derived {@link FreshnessStatus}
 * classification, a human-readable summary string, and a normalised score. Callers can use any
 * combination of these fields for display, ranking, or alert logic without re-deriving them.</p>
 *
 * <h2>Immutability</h2>
 * <p>All fields are set through the constructor and exposed only via getters. There are no
 * setters. This guarantees that a {@code FreshnessReport} snapshot cannot be accidentally
 * mutated after it is created and passed around.</p>
 *
 * <h2>Score semantics</h2>
 * <p>{@link #getFreshnessScore()} returns a value in [0.0, 1.0] where 1.0 means maximally
 * fresh (e.g., confirmed within the last check-in window) and 0.0 means the information is
 * completely stale or has never been confirmed. Intermediate values allow continuous ranking
 * alongside other retrieval signals.</p>
 *
 * @see FreshnessStatus
 * @see ConfirmationPolicy
 */
public final class FreshnessReport {

    /** The stable UUID of the memory this report describes. */
    private final String memoryId;

    /** The freshness classification derived from elapsed time and policy. */
    private final FreshnessStatus status;

    /**
     * When the memory was last explicitly confirmed by the user, in UTC epoch milliseconds.
     * A value of {@code 0} means the memory has never been confirmed.
     */
    private final long lastConfirmedAt;

    /**
     * The number of milliseconds that have elapsed since {@link #lastConfirmedAt}.
     * If the memory was never confirmed, this value represents the elapsed time since
     * the memory was created.
     */
    private final long elapsedMillis;

    /**
     * Whether a confirmation check-in is currently overdue or due within the next prompt window.
     * When {@code true}, the scheduling service should surface a check-in prompt to the user.
     */
    private final boolean checkInDue;

    /**
     * A concise, locale-independent string describing the freshness status for display.
     * Examples: "Confirmed fresh", "Aging – last confirmed 5 days ago", "Stale – never confirmed".
     */
    private final String humanReadableStatus;

    /**
     * Normalised freshness score in the range [0.0, 1.0].
     * A score of 1.0 indicates the information was confirmed very recently relative to the
     * configured check-in interval. A score of 0.0 indicates the information is maximally
     * stale or has never been confirmed.
     */
    private final double freshnessScore;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a fully-specified, immutable {@code FreshnessReport}.
     *
     * @param memoryId           stable UUID of the evaluated memory; must not be null
     * @param status             freshness classification; must not be null
     * @param lastConfirmedAt    UTC epoch ms of last user confirmation; {@code 0} if never confirmed
     * @param elapsedMillis      milliseconds elapsed since {@code lastConfirmedAt} (or since creation)
     * @param checkInDue         {@code true} if a prompt should be surfaced now or very soon
     * @param humanReadableStatus short display string describing the freshness state
     * @param freshnessScore     normalised score in [0.0, 1.0]; 1.0 = maximally fresh
     */
    public FreshnessReport(
            String memoryId,
            FreshnessStatus status,
            long lastConfirmedAt,
            long elapsedMillis,
            boolean checkInDue,
            String humanReadableStatus,
            double freshnessScore
    ) {
        this.memoryId = memoryId;
        this.status = status;
        this.lastConfirmedAt = lastConfirmedAt;
        this.elapsedMillis = elapsedMillis;
        this.checkInDue = checkInDue;
        this.humanReadableStatus = humanReadableStatus;
        this.freshnessScore = freshnessScore;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the stable UUID of the memory to which this report applies.
     *
     * @return memory UUID; never null
     */
    public String getMemoryId() {
        return memoryId;
    }

    /**
     * Returns the freshness classification for the memory.
     *
     * @return one of {@link FreshnessStatus#CONFIRMED_FRESH}, {@link FreshnessStatus#AGING},
     *         {@link FreshnessStatus#STALE}, {@link FreshnessStatus#UNCONFIRMED}, or
     *         {@link FreshnessStatus#EVERGREEN}
     */
    public FreshnessStatus getStatus() {
        return status;
    }

    /**
     * Returns the UTC epoch millisecond timestamp of the most recent explicit user confirmation.
     *
     * <p>A return value of {@code 0} is the sentinel meaning "never confirmed". Callers
     * should treat {@code 0} identically to a very old timestamp when computing elapsed
     * time for display purposes.</p>
     *
     * @return UTC epoch ms, or {@code 0} if the memory has never been confirmed
     */
    public long getLastConfirmedAt() {
        return lastConfirmedAt;
    }

    /**
     * Returns the number of milliseconds that have elapsed since the memory was last confirmed.
     *
     * <p>When the memory has never been confirmed ({@link #getLastConfirmedAt()} == 0), this
     * value is typically the age of the memory itself (elapsed since creation). The freshness
     * evaluation service determines the exact semantics.</p>
     *
     * @return elapsed duration in milliseconds; non-negative
     */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /**
     * Indicates whether a confirmation check-in prompt is currently due for this memory.
     *
     * <p>When {@code true}, the freshness scheduler should surface a prompt asking the user
     * to verify that the stored information is still correct. This flag takes the configured
     * {@link ConfirmationPolicy} interval into account.</p>
     *
     * @return {@code true} if a check-in prompt should be issued; {@code false} otherwise
     */
    public boolean isCheckInDue() {
        return checkInDue;
    }

    /**
     * Returns a short, locale-independent human-readable description of the freshness state.
     *
     * <p>Intended for display in list items or detail screens. Example values:
     * {@code "Confirmed fresh"}, {@code "Aging – last confirmed 5 days ago"},
     * {@code "Stale – never confirmed"}, {@code "Evergreen"}.</p>
     *
     * @return non-null display string
     */
    public String getHumanReadableStatus() {
        return humanReadableStatus;
    }

    /**
     * Returns the normalised freshness score for this memory.
     *
     * <p>The score is in the closed interval [0.0, 1.0]:
     * <ul>
     *   <li>1.0 — confirmed within the current check-in window; maximally fresh.</li>
     *   <li>0.5 — roughly halfway through the aging window.</li>
     *   <li>0.0 — fully stale or never confirmed.</li>
     * </ul>
     * Retrieval ranking can multiply this score with other signals (recency, relevance)
     * to produce a combined result ordering.</p>
     *
     * @return freshness score in [0.0, 1.0]
     */
    public double getFreshnessScore() {
        return freshnessScore;
    }

    @Override
    public String toString() {
        return "FreshnessReport{"
                + "memoryId='" + memoryId + '\''
                + ", status=" + status
                + ", elapsedMillis=" + elapsedMillis
                + ", checkInDue=" + checkInDue
                + ", freshnessScore=" + freshnessScore
                + '}';
    }
}
