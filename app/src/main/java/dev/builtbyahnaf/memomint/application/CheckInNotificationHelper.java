package dev.builtbyahnaf.memomint.application;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Creates and posts check-in reminder notifications for physical items and memories needing verification.
 *
 * <h2>Purpose &amp; Design (Section 15.5, FR-09)</h2>
 * <ul>
 *   <li>Establishes an explicit {@code checkin_channel} notification channel.</li>
 *   <li>Provides one-tap verification action ("✓ Still Here") to confirm item presence without opening the full UI.</li>
 *   <li>Uses idempotent channel initialization for safe invocation from receivers or background tasks.</li>
 * </ul>
 */
public class CheckInNotificationHelper {

    /** Notification channel ID for periodic location check-in reminders. */
    public static final String CHANNEL_ID = "checkin_channel";

    /** User-visible name of the check-in notification channel. */
    private static final String CHANNEL_NAME = "Location Check-Ins";

    /** Description explaining the purpose of the check-in notification channel. */
    private static final String CHANNEL_DESC = "Periodic prompts to verify item locations";

    /**
     * Initializes the notification channel on Android 8.0 (API 26) and above.
     * Safe to call multiple times (idempotent).
     *
     * @param context application or service context
     */
    public static void createNotificationChannel(Context context) {
        if (context == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
                if (existing == null) {
                    NotificationChannel channel = new NotificationChannel(
                            CHANNEL_ID,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_DEFAULT
                    );
                    channel.setDescription(CHANNEL_DESC);
                    manager.createNotificationChannel(channel);
                }
            }
        }
    }

    /**
     * Posts a check-in reminder notification with a direct confirmation action button.
     *
     * @param context  application context
     * @param memoryId stable UUID of the memory to confirm
     * @param title    display title of the memory or item
     * @param bodyText descriptive location or summary text
     */
    public static void postCheckInNotification(Context context, String memoryId, String title, String bodyText) {
        if (context == null || memoryId == null) return;

        createNotificationChannel(context);

        Intent confirmIntent = new Intent(context, CheckInActionReceiver.class);
        confirmIntent.setAction(CheckInActionReceiver.ACTION_CONFIRM);
        confirmIntent.putExtra(CheckInActionReceiver.EXTRA_MEMORY_ID, memoryId);

        int requestCode = memoryId.hashCode();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent confirmPendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                confirmIntent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Check-in: " + (title != null ? title : "Memory"))
                .setContentText(bodyText != null ? bodyText : "Tap to verify this memory.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .addAction(0, "✓ Still Here", confirmPendingIntent);

        try {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build());
        } catch (SecurityException ignored) {
            // Handled when POST_NOTIFICATIONS runtime permission has not yet been granted
        }
    }
}
