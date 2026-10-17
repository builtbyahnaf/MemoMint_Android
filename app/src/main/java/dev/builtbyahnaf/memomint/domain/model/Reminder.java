package dev.builtbyahnaf.memomint.domain.model;

/**
 * A scheduled action or future intention, optionally linked to a memory.
 *
 * <h2>Purpose</h2>
 * <p>Reminders let the user associate time-based triggers with memories (FR-10).
 * For example, after recording that earbuds are beside the bed, the user can create a
 * reminder to take them at 8 PM the next day. The reminder is linked to the memory by a
 * {@link MemoryRelation} with type {@link RelationType#ASSOCIATED_WITH_REMINDER}.</p>
 *
 * <h2>Platform scheduling</h2>
 * <p>The database is the authoritative record of the user's intended reminder. Android
 * WorkManager or AlarmManager may be used to fire the notification, but they are
 * subject to deferral, device restart, and battery management. The application must
 * re-register scheduled reminders on device boot and must not promise exact delivery
 * when the platform cannot guarantee it (FR-10, Section 14.3).</p>
 *
 * <h2>Time zone</h2>
 * <p>{@code timeZoneId} records the IANA time zone in which {@code scheduledAt} was
 * originally interpreted. This is important for daylight-saving transitions: if the
 * device moves time zones or DST changes between creation and firing, the stored zone
 * lets the application re-evaluate whether the intended local time has shifted.</p>
 *
 * @see ReminderStatus
 */
public class Reminder {

    /** Stable UUID identifier. */
    private String id;

    /**
     * The memory this reminder is associated with, or null if the reminder stands alone.
     * When set, a {@link MemoryRelation} of type ASSOCIATED_WITH_REMINDER should also exist.
     */
    private String memoryId;

    /** Short description displayed in the notification and reminder list. */
    private String title;

    /**
     * When the reminder should fire, in epoch milliseconds (UTC).
     * Updated when the reminder is snoozed.
     */
    private long scheduledAt;

    /**
     * IANA time zone identifier in which {@code scheduledAt} was resolved
     * (e.g., "Asia/Dhaka", "America/New_York"). May be null for legacy records.
     */
    private String timeZoneId;

    /** Current lifecycle state of this reminder. */
    private ReminderStatus status;

    /** When this reminder was created, in epoch milliseconds (UTC). */
    private long createdAt;

    /**
     * When the user marked this reminder as completed.
     * Null until the reminder is completed; set atomically with the COMPLETED status update.
     */
    private Long completedAt;

    /**
     * The most recent time at which the platform scheduling API was asked to deliver
     * this reminder, in epoch milliseconds. Null if never scheduled with the platform.
     *
     * <p>This is distinct from {@code scheduledAt}: {@code scheduledAt} is the user's
     * intended time, while {@code lastScheduledAt} records the last platform registration.</p>
     */
    private Long lastScheduledAt;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** No-argument constructor for repository deserialization. */
    public Reminder() {}

    /**
     * Full constructor.
     *
     * @param id              stable UUID
     * @param memoryId        linked memory UUID; may be null
     * @param title           notification title; must not be null
     * @param scheduledAt     intended fire time in epoch ms (UTC)
     * @param timeZoneId      IANA time zone; may be null
     * @param status          lifecycle state
     * @param createdAt       creation epoch ms
     * @param completedAt     completion epoch ms; null if not completed
     * @param lastScheduledAt last platform registration epoch ms; null if not yet scheduled
     */
    public Reminder(
            String id,
            String memoryId,
            String title,
            long scheduledAt,
            String timeZoneId,
            ReminderStatus status,
            long createdAt,
            Long completedAt,
            Long lastScheduledAt
    ) {
        this.id = id;
        this.memoryId = memoryId;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.timeZoneId = timeZoneId;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.lastScheduledAt = lastScheduledAt;
    }

    // -------------------------------------------------------------------------
    // Getters & setters
    // -------------------------------------------------------------------------

    /**
     * Gets the stable UUID identifier for this reminder.
     *
     * @return stable UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the stable UUID identifier for this reminder.
     *
     * @param id stable UUID string
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the UUID of the associated memory, if any.
     *
     * <p>Reminders may be linked to a specific memory (e.g., "Check passport expiry" linked to
     * a memory recording passport location) or may stand alone. When non-null, a corresponding
     * {@link MemoryRelation} with {@link RelationType#ASSOCIATED_WITH_REMINDER} should also be maintained.</p>
     *
     * @return foreign key UUID matching {@link Memory#getId()}, or null if standalone
     */
    public String getMemoryId() {
        return memoryId;
    }

    /**
     * Sets the UUID of the associated memory.
     *
     * @param memoryId foreign key UUID, or null
     */
    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    /**
     * Gets the short title displayed in the system notification and reminder list.
     *
     * @return notification title text; never null for valid records
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the short title displayed in the notification.
     *
     * @param title notification title text
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the UTC epoch milliseconds when this reminder is intended to fire.
     *
     * <p>When a reminder is snoozed, this timestamp is updated to the new target fire time.</p>
     *
     * @return intended fire time in UTC epoch milliseconds
     */
    public long getScheduledAt() {
        return scheduledAt;
    }

    /**
     * Sets the UTC epoch milliseconds when this reminder is intended to fire.
     *
     * @param scheduledAt target fire time in UTC epoch milliseconds
     */
    public void setScheduledAt(long scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    /**
     * Gets the IANA time zone identifier in which {@link #getScheduledAt()} was originally formulated.
     *
     * <p><b>Daylight Saving Time (DST) & Time Zone Shifts:</b> Storing the IANA zone string
     * (e.g. "Asia/Dhaka", "America/New_York") enables the application to detect whether a device
     * has crossed time zones or undergone a DST shift between creation and firing, allowing correct
     * re-evaluation of local wall-clock intentions.</p>
     *
     * @return IANA time zone identifier string, or null for legacy records
     */
    public String getTimeZoneId() {
        return timeZoneId;
    }

    /**
     * Sets the IANA time zone identifier.
     *
     * @param timeZoneId IANA time zone string
     */
    public void setTimeZoneId(String timeZoneId) {
        this.timeZoneId = timeZoneId;
    }

    /**
     * Gets the current lifecycle state of this reminder.
     *
     * @return current {@link ReminderStatus}
     */
    public ReminderStatus getStatus() {
        return status;
    }

    /**
     * Sets the current lifecycle state of this reminder.
     *
     * @param status the {@link ReminderStatus}
     */
    public void setStatus(ReminderStatus status) {
        this.status = status;
    }

    /**
     * Gets the timestamp when this reminder record was created.
     *
     * @return UTC epoch milliseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt UTC epoch milliseconds
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the timestamp when the user marked this reminder as completed.
     *
     * <p>Null while active or snoozed; assigned atomically when transitioning to
     * {@link ReminderStatus#COMPLETED}.</p>
     *
     * @return completion UTC epoch milliseconds, or null
     */
    public Long getCompletedAt() {
        return completedAt;
    }

    /**
     * Sets the completion timestamp.
     *
     * @param completedAt completion UTC epoch milliseconds, or null
     */
    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Gets the timestamp when the reminder was last registered with Android's platform scheduling API.
     *
     * <p><b>Platform vs Intent Separation (Section 14.3):</b> {@link #getScheduledAt()} represents the
     * user's intended fire time. {@link #getLastScheduledAt()} records when WorkManager or AlarmManager
     * was actually invoked to schedule the alarm. On device reboot, reminders where {@code lastScheduledAt}
     * is stale or missing must be re-registered with the platform.</p>
     *
     * @return platform registration UTC epoch milliseconds, or null if never scheduled
     */
    public Long getLastScheduledAt() {
        return lastScheduledAt;
    }

    /**
     * Sets the platform registration timestamp.
     *
     * @param lastScheduledAt platform registration UTC epoch milliseconds, or null
     */
    public void setLastScheduledAt(Long lastScheduledAt) {
        this.lastScheduledAt = lastScheduledAt;
    }

    /**
     * Checks if this reminder is active and awaiting dispatch.
     *
     * <p><b>Algorithm:</b> A reminder is active if its status is either {@link ReminderStatus#ACTIVE}
     * or {@link ReminderStatus#SNOOZED}. Completed, dismissed, or failed reminders are inactive.</p>
     *
     * @return true if the reminder is active or snoozed; false otherwise
     */
    public boolean isActive() {
        return ReminderStatus.ACTIVE.equals(status) || ReminderStatus.SNOOZED.equals(status);
    }

    @Override
    public String toString() {
        return "Reminder{"
                + "id='" + id + '\''
                + ", title='" + title + '\''
                + ", scheduledAt=" + scheduledAt
                + ", status=" + status
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reminder)) return false;
        Reminder other = (Reminder) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
