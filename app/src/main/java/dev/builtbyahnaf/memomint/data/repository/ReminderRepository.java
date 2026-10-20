package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.ReminderStatus;

/**
 * Data access repository interface for persisting and querying {@link Reminder} entities.
 *
 * <h2>Architectural Principles (Layer C, Section 11.5)</h2>
 * <ul>
 *   <li>Handles CRUD persistence for user reminders in SQLite storage.</li>
 *   <li>Manages lifecycle transitions (ACTIVE, SNOOZED, COMPLETED, CANCELLED).</li>
 *   <li>Enables querying active and pending reminders for platform alarm registration.</li>
 * </ul>
 */
public interface ReminderRepository {

    /**
     * Persists a new or updated reminder into SQLite.
     *
     * @param reminder the reminder to persist; must not be null
     */
    void save(Reminder reminder);

    /**
     * Finds a reminder by its unique UUID.
     *
     * @param id stable reminder UUID
     * @return matching Reminder, or null if not found
     */
    Reminder findById(String id);

    /**
     * Finds the primary reminder associated with a specific memory.
     *
     * @param memoryId target memory UUID
     * @return matching Reminder, or null if none associated
     */
    Reminder findByMemoryId(String memoryId);

    /**
     * Finds all currently active reminders (ACTIVE or SNOOZED).
     *
     * @return list of active reminders ordered by scheduled time
     */
    List<Reminder> findAllActive();

    /**
     * Finds all active reminders scheduled to fire at or before the given timestamp.
     *
     * @param maxScheduledAt cutoff timestamp in epoch milliseconds
     * @return list of pending reminders
     */
    List<Reminder> findPendingDue(long maxScheduledAt);

    /**
     * Updates the lifecycle status of a reminder.
     *
     * @param id          reminder UUID
     * @param status      new status
     * @param completedAt completion timestamp in epoch milliseconds, or null
     */
    void updateStatus(String id, ReminderStatus status, Long completedAt);

    /**
     * Updates the scheduled fire time and last registered timestamp.
     *
     * @param id              reminder UUID
     * @param scheduledAt     new scheduled time in epoch milliseconds
     * @param lastScheduledAt platform registration timestamp in epoch milliseconds
     */
    void updateScheduledAt(String id, long scheduledAt, Long lastScheduledAt);

    /**
     * Deletes a reminder by its unique UUID.
     *
     * @param id reminder UUID
     */
    void delete(String id);

    /**
     * Deletes all reminders associated with a given memory UUID.
     *
     * @param memoryId memory UUID
     */
    void deleteByMemoryId(String memoryId);
}
