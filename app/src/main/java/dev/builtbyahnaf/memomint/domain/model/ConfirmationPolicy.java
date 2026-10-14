package dev.builtbyahnaf.memomint.domain.model;

/**
 * Stores the optional freshness-check schedule for a memory.
 *
 * <h2>Purpose</h2>
 * <p>Physical-item memories may become stale — the item may have been moved since the
 * location was recorded. The ConfirmationPolicy allows the user to configure a periodic
 * prompt asking them to verify that the item is still where it was last recorded (FR-09).</p>
 *
 * <h2>Key constraints</h2>
 * <ul>
 *   <li>A check-in prompt requests user confirmation. It must never assume the item is still
 *       in place or mark the location as verified without an explicit user response.</li>
 *   <li>The scheduling system must tolerate missed execution windows. If a scheduled check-in
 *       is missed (e.g., because the device was off), it should be rescheduled rather than
 *       silently dropped.</li>
 *   <li>Not all memory types benefit from confirmation schedules. The policy is optional;
 *       a null ConfirmationPolicy row means no periodic check is scheduled.</li>
 * </ul>
 *
 * <h2>Interval types</h2>
 * <p>Supported interval type strings are defined in {@code DatabaseConstants.IntervalType}.
 * Valid values: {@code "DAILY"}, {@code "THREE_DAYS"}, {@code "WEEKLY"}, {@code "DISABLED"}.</p>
 */
public class ConfirmationPolicy {

    /**
     * The memory this policy applies to.
     * Primary key (one policy per memory). Foreign key to {@link Memory#getId()};
     * cascade-deleted when the memory is deleted.
     */
    private String memoryId;

    /**
     * Whether the confirmation schedule is enabled.
     * When false, no prompts are sent regardless of the interval setting.
     */
    private boolean enabled;

    /**
     * The type of interval between prompts.
     * Must be one of the strings in {@code DatabaseConstants.IntervalType}.
     */
    private String intervalType;

    /**
     * The numeric interval in days, used alongside {@code intervalType} for scheduling.
     * For example, intervalType="DAILY" with intervalValue=1 means prompt every day.
     * This redundancy allows the interval to be changed without changing the type constant.
     */
    private int intervalValue;

    /**
     * When the user was last shown a confirmation prompt for this memory,
     * in epoch milliseconds (UTC). Null if never prompted.
     */
    private Long lastPromptedAt;

    /**
     * When the next confirmation check should occur, in epoch milliseconds (UTC).
     * Calculated by adding the interval to {@code lastPromptedAt} (or to creation time
     * if never prompted). Null if disabled or not yet calculated.
     */
    private Long nextCheckAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public ConfirmationPolicy() {}

    /**
     * Full constructor.
     *
     * @param memoryId       owning memory's UUID (PK)
     * @param enabled        whether periodic prompting is active
     * @param intervalType   interval type string; see DatabaseConstants.IntervalType
     * @param intervalValue  numeric interval in days
     * @param lastPromptedAt last prompt time; may be null
     * @param nextCheckAt    next scheduled check time; may be null
     */
    public ConfirmationPolicy(
            String memoryId,
            boolean enabled,
            String intervalType,
            int intervalValue,
            Long lastPromptedAt,
            Long nextCheckAt
    ) {
        this.memoryId = memoryId;
        this.enabled = enabled;
        this.intervalType = intervalType;
        this.intervalValue = intervalValue;
        this.lastPromptedAt = lastPromptedAt;
        this.nextCheckAt = nextCheckAt;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getIntervalType() { return intervalType; }
    public void setIntervalType(String intervalType) { this.intervalType = intervalType; }

    public int getIntervalValue() { return intervalValue; }
    public void setIntervalValue(int intervalValue) { this.intervalValue = intervalValue; }

    public Long getLastPromptedAt() { return lastPromptedAt; }
    public void setLastPromptedAt(Long lastPromptedAt) { this.lastPromptedAt = lastPromptedAt; }

    public Long getNextCheckAt() { return nextCheckAt; }
    public void setNextCheckAt(Long nextCheckAt) { this.nextCheckAt = nextCheckAt; }

    @Override
    public String toString() {
        return "ConfirmationPolicy{"
                + "memoryId='" + memoryId + '\''
                + ", enabled=" + enabled
                + ", intervalType='" + intervalType + '\''
                + ", intervalValue=" + intervalValue
                + '}';
    }
}
