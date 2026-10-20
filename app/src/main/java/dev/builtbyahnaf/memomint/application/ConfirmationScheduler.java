package dev.builtbyahnaf.memomint.application;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Schedules a daily inexact repeating alarm via {@link AlarmManager} to trigger {@link CheckInReceiver}
 * for periodic check-in audits.
 *
 * <h2>Battery &amp; Execution Principles (FR-09, Section 15.5)</h2>
 * <ul>
 *   <li><b>Battery-Friendly Inexact Timing:</b> Employs {@link AlarmManager#ELAPSED_REALTIME} and
 *       inexact repeating alarms to allow the Android OS to batch wakeups with other system jobs.</li>
 *   <li><b>Zero Wake-Lock Abuse:</b> Does not hold wakelocks across device idle windows.</li>
 *   <li><b>Idempotent Rescheduling:</b> Multiple calls to schedule update existing pending alarms safely.</li>
 * </ul>
 */
public class ConfirmationScheduler {

    /** Action string for broadcasting periodic check-in audit sweeps. */
    public static final String ACTION_CHECKIN_AUDIT = "dev.builtbyahnaf.memomint.ACTION_CHECKIN_AUDIT";

    /** Stable request code identifying check-in audit pending intents. */
    private static final int REQUEST_CODE = 4001;

    /**
     * Schedules a daily inexact repeating audit alarm if not already active.
     *
     * @param context application or activity context; must not be null
     */
    public static void scheduleRecurringAudit(Context context) {
        if (context == null) return;

        Intent intent = new Intent(context, CheckInReceiver.class);
        intent.setAction(ACTION_CHECKIN_AUDIT);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_DAY,
                    AlarmManager.INTERVAL_DAY,
                    pi
            );
        }
    }

    /**
     * Cancels any previously scheduled recurring check-in audit alarm.
     *
     * @param context application or activity context; must not be null
     */
    public static void cancelAudit(Context context) {
        if (context == null) return;

        Intent intent = new Intent(context, CheckInReceiver.class);
        intent.setAction(ACTION_CHECKIN_AUDIT);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.cancel(pi);
            }
            pi.cancel();
        }
    }

    /**
     * Determines whether a recurring check-in audit alarm is currently active and registered in the system.
     *
     * @param context application or activity context; must not be null
     * @return true if an alarm pending intent exists, false otherwise
     */
    public static boolean isScheduled(Context context) {
        if (context == null) return false;

        Intent intent = new Intent(context, CheckInReceiver.class);
        intent.setAction(ACTION_CHECKIN_AUDIT);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        return pi != null;
    }
}
