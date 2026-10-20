package dev.builtbyahnaf.memomint.domain.model;

/**
 * Immutable record describing a supersession event between two memory records.
 *
 * <h2>Purpose</h2>
 * <p>When a user relocates a physical item or corrects an outdated memory, the system creates
 * a SUPERSEDES relation between the new (active) memory and the old (superseded) memory. A
 * {@code SupersessionInfo} snapshot bundles the identifiers of both parties together with the
 * timestamp of the event and optional human-readable summaries of the previous and current
 * assertions. This provides the presentation layer with everything it needs to display a
 * "You previously noted X, now updated to Y" history chip without additional database queries.</p>
 *
 * <h2>History preservation</h2>
 * <p>The superseded memory is <em>never deleted</em>. It remains in the database with its
 * original status. Only its de-prioritisation in the default feed (via the SUPERSEDES relation)
 * signals that a newer assertion now takes precedence (Section 15.4, NFR-03).</p>
 *
 * @see MemoryRelation
 * @see RelationType#SUPERSEDES
 */
public final class SupersessionInfo {

    /** The UUID of the memory that now takes precedence (the newer, active memory). */
    private final String activeMemoryId;

    /** The UUID of the memory that has been superseded (the older, archived-in-place memory). */
    private final String supersededMemoryId;

    /** UTC epoch milliseconds when the supersession relation was created. */
    private final long supersededAt;

    /**
     * A short human-readable summary of the superseded (old) location or assertion.
     * May be {@code null} if the summary was not available at supersession time.
     */
    private final String previousLocationSummary;

    /**
     * A short human-readable summary of the active (new) location or assertion.
     * May be {@code null} if the summary was not available at supersession time.
     */
    private final String currentLocationSummary;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a fully-specified, immutable {@code SupersessionInfo}.
     *
     * @param activeMemoryId          UUID of the newer, active memory; must not be null
     * @param supersededMemoryId      UUID of the older, superseded memory; must not be null
     * @param supersededAt            UTC epoch ms when the SUPERSEDES relation was written
     * @param previousLocationSummary short display string for the old assertion; may be null
     * @param currentLocationSummary  short display string for the new assertion; may be null
     */
    public SupersessionInfo(
            String activeMemoryId,
            String supersededMemoryId,
            long supersededAt,
            String previousLocationSummary,
            String currentLocationSummary
    ) {
        this.activeMemoryId = activeMemoryId;
        this.supersededMemoryId = supersededMemoryId;
        this.supersededAt = supersededAt;
        this.previousLocationSummary = previousLocationSummary;
        this.currentLocationSummary = currentLocationSummary;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the UUID of the memory currently considered correct and active.
     *
     * @return non-null UUID string
     */
    public String getActiveMemoryId() {
        return activeMemoryId;
    }

    /**
     * Returns the UUID of the memory that was superseded and is now de-prioritised.
     *
     * <p>The superseded memory row still exists in the database. Callers should not
     * delete it — it is intentionally retained for audit history.</p>
     *
     * @return non-null UUID string
     */
    public String getSupersededMemoryId() {
        return supersededMemoryId;
    }

    /**
     * Returns the UTC epoch millisecond timestamp when the supersession occurred.
     *
     * @return UTC epoch ms
     */
    public long getSupersededAt() {
        return supersededAt;
    }

    /**
     * Returns a short display string describing the previous (superseded) assertion.
     *
     * <p>For example: {@code "Kitchen counter"}. May be {@code null} if not captured.</p>
     *
     * @return display string or {@code null}
     */
    public String getPreviousLocationSummary() {
        return previousLocationSummary;
    }

    /**
     * Returns a short display string describing the current (active) assertion.
     *
     * <p>For example: {@code "Hallway table"}. May be {@code null} if not captured.</p>
     *
     * @return display string or {@code null}
     */
    public String getCurrentLocationSummary() {
        return currentLocationSummary;
    }

    @Override
    public String toString() {
        return "SupersessionInfo{"
                + "activeMemoryId='" + activeMemoryId + '\''
                + ", supersededMemoryId='" + supersededMemoryId + '\''
                + ", supersededAt=" + supersededAt
                + ", previousLocationSummary='" + previousLocationSummary + '\''
                + ", currentLocationSummary='" + currentLocationSummary + '\''
                + '}';
    }
}
