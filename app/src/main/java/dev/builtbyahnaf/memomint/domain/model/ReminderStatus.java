package dev.builtbyahnaf.memomint.domain.model;

/**
 * Lifecycle state of a Reminder record.
 *
 * <p>A reminder linked to a memory can be created, completed, cancelled, or snoozed
 * independently of the memory itself. These are separate operations and the states
 * must be tracked explicitly rather than inferred from the scheduled time (FR-10).</p>
 *
 * <p>Stored as the enum name string in the database.</p>
 */
public enum ReminderStatus {

    /**
     * The reminder is scheduled and not yet acted upon.
     */
    ACTIVE,

    /**
     * The user acknowledged and completed the reminder.
     * {@code completed_at} is set when transitioning to this state.
     */
    COMPLETED,

    /**
     * The reminder was cancelled by the user before it fired or was acted upon.
     */
    CANCELLED,

    /**
     * The reminder fired but the user chose to be reminded again later.
     * The new scheduled time is stored in {@code scheduled_at}.
     */
    SNOOZED
}
