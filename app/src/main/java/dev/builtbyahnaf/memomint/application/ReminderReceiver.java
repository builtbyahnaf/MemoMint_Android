package dev.builtbyahnaf.memomint.application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepository;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.ReminderStatus;

/**
 * BroadcastReceiver triggered by {@link android.app.AlarmManager} when a scheduled reminder is due.
 *
 * <h2>Algorithm & Lifecycle</h2>
 * <ol>
 *   <li>Extracts {@code extra_reminder_id} from incoming intent.</li>
 *   <li>Asynchronously queries the database for the {@link Reminder} entity.</li>
 *   <li>Dispatches the system notification via {@link ReminderNotificationHelper}.</li>
 *   <li><b>Repetition Handling:</b> If {@link Reminder#isRepeating()} is true:
 *     <ul>
 *       <li>Calculates the next occurrence timestamp according to {@link Reminder#getRepeatInterval()}.</li>
 *       <li>Updates {@code scheduled_at} in the database.</li>
 *       <li>Schedules the subsequent alarm with {@link ReminderScheduler}.</li>
 *     </ul>
 *     Otherwise, marks the reminder status as {@link ReminderStatus#COMPLETED}.
 *   </li>
 * </ol>
 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        String fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        String fallbackMemoryId = intent.getStringExtra(ReminderScheduler.EXTRA_MEMORY_ID);

        Log.i(TAG, "Received reminder trigger broadcast for id: " + reminderId);

        // Run database access off the main receiver thread using goAsync()
        final PendingResult pendingResult = goAsync();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                MemoMintDatabase dbHelper = MemoMintDatabase.getInstance(context);
                ReminderRepository reminderRepo = new ReminderRepositoryImpl(dbHelper);
                ReminderScheduler scheduler = new ReminderScheduler(context, reminderRepo);

                Reminder reminder = null;
                if (reminderId != null) {
                    reminder = reminderRepo.findById(reminderId);
                }

                if (reminder == null && reminderId != null) {
                    // Fallback using extras if DB row was temporarily inaccessible
                    reminder = new Reminder();
                    reminder.setId(reminderId);
                    reminder.setTitle(fallbackTitle != null ? fallbackTitle : "MemoMint Reminder");
                    reminder.setMemoryId(fallbackMemoryId);
                    reminder.setStatus(ReminderStatus.ACTIVE);
                    reminder.setRepeatInterval("NONE");
                }

                if (reminder != null) {
                    // 1. Show the system notification
                    ReminderNotificationHelper.showNotification(context, reminder);

                    // 2. Handle recurrence or completion
                    if (reminder.isRepeating()) {
                        long nextTrigger = ReminderScheduler.computeNextOccurrence(
                                reminder.getScheduledAt(),
                                reminder.getRepeatInterval()
                        );
                        reminder.setScheduledAt(nextTrigger);
                        reminderRepo.updateScheduledAt(reminder.getId(), nextTrigger, System.currentTimeMillis());
                        scheduler.schedule(reminder);
                        Log.i(TAG, "Rescheduled repeating reminder id=" + reminder.getId() + " for epoch ms " + nextTrigger);
                    } else {
                        reminderRepo.updateStatus(reminder.getId(), ReminderStatus.COMPLETED, System.currentTimeMillis());
                        Log.i(TAG, "Marked one-time reminder id=" + reminder.getId() + " as COMPLETED");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing reminder in ReminderReceiver: " + e.getMessage(), e);
            } finally {
                pendingResult.finish();
            }
        });
    }
}
