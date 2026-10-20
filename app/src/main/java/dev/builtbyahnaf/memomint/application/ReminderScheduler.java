package dev.builtbyahnaf.memomint.application;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;
import java.util.Objects;

import dev.builtbyahnaf.memomint.data.repository.ReminderRepository;
import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;

/**
 * Service orchestrating platform-level alarm scheduling and cancellation for {@link Reminder}s.
 *
 * <h2>Purpose & Invariants (FR-10, Section 14.3)</h2>
 * <ul>
 *   <li>Uses Android's {@link AlarmManager} to deliver exact reminders even while device is in Doze mode.</li>
 *   <li>Respects Android 12+ (API 31+) exact alarm scheduling permissions.</li>
 *   <li>Separates intended fire time ({@link Reminder#getScheduledAt()}) from platform registration
 *       timestamp ({@link Reminder#getLastScheduledAt()}).</li>
 *   <li>Calculates forward repetitions for recurring reminders (DAILY, WEEKLY, MONTHLY).</li>
 * </ul>
 */
public class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    public static final String ACTION_TRIGGER_REMINDER = "dev.builtbyahnaf.memomint.ACTION_TRIGGER_REMINDER";
    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_MEMORY_ID = "extra_memory_id";
    public static final String EXTRA_TITLE = "extra_title";

    private final Context context;
    private final ReminderRepository reminderRepository;

    public ReminderScheduler(Context context, ReminderRepository reminderRepository) {
        this.context = Objects.requireNonNull(context, "context cannot be null").getApplicationContext();
        this.reminderRepository = reminderRepository;
    }

    /**
     * Schedules or reschedules an alarm for the given reminder.
     *
     * @param reminder the reminder to schedule; must not be null
     * @return true if successfully registered with AlarmManager; false otherwise
     */
    public boolean schedule(Reminder reminder) {
        if (reminder == null || reminder.getId() == null) {
            return false;
        }

        long triggerAtMillis = reminder.getScheduledAt();
        long now = System.currentTimeMillis();

        // If the intended fire time has already passed and the reminder repeats, advance it to next occurrence
        if (triggerAtMillis <= now && reminder.isRepeating()) {
            triggerAtMillis = computeNextOccurrence(triggerAtMillis, reminder.getRepeatInterval());
            reminder.setScheduledAt(triggerAtMillis);
        }

        // If still in the past (non-repeating), do not schedule past alarms
        if (triggerAtMillis <= now) {
            Log.w(TAG, "Cannot schedule reminder in the past: id=" + reminder.getId() + ", time=" + triggerAtMillis);
            return false;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager system service is unavailable.");
            return false;
        }

        PendingIntent pendingIntent = createPendingIntent(reminder);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    // Fallback for when exact alarm permission is not granted
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }

            long scheduledTimestamp = System.currentTimeMillis();
            reminder.setLastScheduledAt(scheduledTimestamp);
            if (reminderRepository != null) {
                reminderRepository.updateScheduledAt(reminder.getId(), triggerAtMillis, scheduledTimestamp);
            }
            Log.i(TAG, "Successfully scheduled reminder id=" + reminder.getId() + " for epoch ms " + triggerAtMillis);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while scheduling exact alarm: " + e.getMessage(), e);
            try {
                // Secondary fallback to inexact window alarm if exact scheduling was rejected
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                return true;
            } catch (Exception ex) {
                Log.e(TAG, "Inexact alarm fallback also failed: " + ex.getMessage(), ex);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error scheduling alarm: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cancels any pending platform alarm registered for the given reminder ID.
     *
     * @param reminderId reminder UUID
     */
    public void cancel(String reminderId) {
        if (reminderId == null) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ACTION_TRIGGER_REMINDER);
        intent.setPackage(context.getPackageName());
        int requestCode = Math.abs(reminderId.hashCode());

        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.i(TAG, "Cancelled platform alarm for reminder id=" + reminderId);
        }
    }

    /**
     * Advances a scheduled time to its next calendar occurrence based on the repetition interval.
     *
     * @param fromTimestamp  base timestamp in epoch milliseconds
     * @param repeatInterval interval type ("DAILY", "WEEKLY", "MONTHLY")
     * @return next epoch millisecond timestamp strictly in the future
     */
    public static long computeNextOccurrence(long fromTimestamp, String repeatInterval) {
        RepeatInterval interval = RepeatInterval.fromString(repeatInterval);
        if (interval == RepeatInterval.NONE) {
            return fromTimestamp;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(fromTimestamp);
        long now = System.currentTimeMillis();

        do {
            switch (interval) {
                case DAILY:
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                    break;
                case WEEKLY:
                    calendar.add(Calendar.WEEK_OF_YEAR, 1);
                    break;
                case MONTHLY:
                    calendar.add(Calendar.MONTH, 1);
                    break;
                default:
                    return fromTimestamp;
            }
        } while (calendar.getTimeInMillis() <= now);

        return calendar.getTimeInMillis();
    }

    private PendingIntent createPendingIntent(Reminder reminder) {
        Intent intent = new Intent(ACTION_TRIGGER_REMINDER);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_REMINDER_ID, reminder.getId());
        intent.putExtra(EXTRA_MEMORY_ID, reminder.getMemoryId());
        intent.putExtra(EXTRA_TITLE, reminder.getTitle());

        int requestCode = Math.abs(reminder.getId().hashCode());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
