package dev.builtbyahnaf.memomint.application;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import dev.builtbyahnaf.memomint.MainActivity;
import dev.builtbyahnaf.memomint.R;
import dev.builtbyahnaf.memomint.domain.model.Reminder;

/**
 * Handles creation and presentation of Android system notifications for scheduled reminders.
 *
 * <h2>Design & Privacy Principles (Section 16, Section 18)</h2>
 * <ul>
 *   <li>Establishes an explicit, high-priority Notification Channel with user control.</li>
 *   <li>Only fires notifications locally on-device without cloud dependencies.</li>
 *   <li>Checks {@code POST_NOTIFICATIONS} permission (Android 13+) before triggering display.</li>
 *   <li>Clicking the notification directs the user seamlessly into {@link MainActivity}.</li>
 * </ul>
 */
public class ReminderNotificationHelper {

    private static final String TAG = "ReminderNotification";

    public static final String CHANNEL_ID_REMINDERS = "memomint_reminders_channel";
    public static final String CHANNEL_NAME_REMINDERS = "MemoMint Reminders";
    public static final String CHANNEL_DESC_REMINDERS = "Notifications for scheduled memories and tasks";

    /**
     * Initializes the notification channel on Android 8.0 (API 26) and above.
     * Safe to call multiple times (idempotent).
     *
     * @param context application context
     */
    public static void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID_REMINDERS);
                if (existing == null) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID_REMINDERS,
                            CHANNEL_NAME_REMINDERS,
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    channel.setDescription(CHANNEL_DESC_REMINDERS);
                    channel.enableVibration(true);
                    channel.enableLights(true);
                    manager.createNotificationChannel(channel);
                    Log.i(TAG, "Created notification channel: " + CHANNEL_ID_REMINDERS);
                }
            }
        }
    }

    /**
     * Displays a system notification for the given reminder.
     *
     * @param context  application context
     * @param reminder the triggered reminder
     */
    public static void showNotification(Context context, Reminder reminder) {
        if (reminder == null) return;

        // Check POST_NOTIFICATIONS permission on Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot post notification for: " + reminder.getId());
                return;
            }
        }

        ensureNotificationChannel(context);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Intent to launch MainActivity when notification is tapped
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (reminder.getMemoryId() != null) {
            tapIntent.putExtra("EXTRA_MEMORY_ID", reminder.getMemoryId());
        }

        int requestCode = Math.abs(reminder.getId().hashCode());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, tapIntent, flags);

        String title = reminder.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = context.getString(R.string.app_name) + " Reminder";
        }

        String subText = reminder.isRepeating() ? "Repeats: " + reminder.getRepeatInterval() : null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_REMINDERS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText("It's time for your scheduled memory!")
                .setSubText(subText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(requestCode, builder.build());
        Log.i(TAG, "Notification displayed for reminder: " + reminder.getId());
    }
}
