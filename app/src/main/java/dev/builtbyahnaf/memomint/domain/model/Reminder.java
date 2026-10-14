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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMemoryId() { return memoryId; }
    public void setMemoryId(String memoryId) { this.memoryId = memoryId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(long scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getTimeZoneId() { return timeZoneId; }
    public void setTimeZoneId(String timeZoneId) { this.timeZoneId = timeZoneId; }

    public ReminderStatus getStatus() { return status; }
    public void setStatus(ReminderStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }

    public Long getLastScheduledAt() { return lastScheduledAt; }
    public void setLastScheduledAt(Long lastScheduledAt) { this.lastScheduledAt = lastScheduledAt; }

    /** @return true if this reminder is still waiting to fire */
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
