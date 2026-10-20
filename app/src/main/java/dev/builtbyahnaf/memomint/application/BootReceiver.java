package dev.builtbyahnaf.memomint.application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepository;
import dev.builtbyahnaf.memomint.data.repository.ReminderRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Reminder;

/**
 * Re-registers all active and repeating reminders with {@link android.app.AlarmManager}
 * after a device reboot or application package update.
 *
 * <h2>Invariant (FR-10, Section 14.3)</h2>
 * <p>Android clears all scheduled alarms on reboot. The database is the authoritative
 * persistent record of user intentions. This receiver ensures scheduled reminders
 * survive system restarts without data loss.</p>
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "Reboot or package update detected. Rescheduling active reminders...");

            final PendingResult pendingResult = goAsync();

            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    MemoMintDatabase db = MemoMintDatabase.getInstance(context);
                    ReminderRepository repo = new ReminderRepositoryImpl(db);
                    ReminderScheduler scheduler = new ReminderScheduler(context, repo);

                    List<Reminder> activeReminders = repo.findAllActive();
                    int count = 0;
                    for (Reminder reminder : activeReminders) {
                        if (scheduler.schedule(reminder)) {
                            count++;
                        }
                    }
                    Log.i(TAG, "Successfully restored " + count + " active alarms on device boot.");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restore alarms on reboot: " + e.getMessage(), e);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }
}
