package dev.builtbyahnaf.memomint.application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;

/**
 * BroadcastReceiver triggered when a user taps the "Still Here" action on a check-in notification.
 *
 * <h2>Execution Flow (Section 15.5, FR-09)</h2>
 * <ol>
 *   <li>Extracts {@code memory_id} from the incoming intent.</li>
 *   <li>Invokes {@link TrustAndFreshnessService#confirmMemory(String, long)} to update confirmation timestamp
 *       and advance the next check-in window.</li>
 *   <li>Dismisses the active notification via {@link NotificationManagerCompat}.</li>
 *   <li>Provides user feedback confirming the location verification.</li>
 * </ol>
 */
public class CheckInActionReceiver extends BroadcastReceiver {

    /** Action string for processing confirmation intents from check-in notification buttons. */
    public static final String ACTION_CONFIRM = "dev.builtbyahnaf.memomint.ACTION_CONFIRM_CHECKIN";

    /** Extra containing the target memory UUID. */
    public static final String EXTRA_MEMORY_ID = "memory_id";

    private static final String TAG = "CheckInActionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String memoryId = intent.getStringExtra(EXTRA_MEMORY_ID);
        if (memoryId == null) {
            Log.w(TAG, "Received check-in action intent without memory_id extra");
            return;
        }

        try {
            MemoMintDatabase db = MemoMintDatabase.getInstance(context);
            MemoryRepositoryImpl memoryRepo = new MemoryRepositoryImpl(db);
            ConfirmationPolicyRepositoryImpl policyRepo = new ConfirmationPolicyRepositoryImpl(db);
            MemoryRelationRepositoryImpl relationRepo = new MemoryRelationRepositoryImpl(db);
            FreshnessEvaluator evaluator = new FreshnessEvaluator();
            SupersessionManager supersessionManager = new SupersessionManager(relationRepo);
            TrustAndFreshnessService service = new TrustAndFreshnessService(
                    memoryRepo,
                    policyRepo,
                    relationRepo,
                    evaluator,
                    supersessionManager
            );

            service.confirmMemory(memoryId, System.currentTimeMillis());

            NotificationManagerCompat.from(context).cancel(memoryId.hashCode());
            Toast.makeText(context, "Location confirmed!", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Successfully confirmed memory location for id: " + memoryId);
        } catch (Exception e) {
            Log.e(TAG, "Error handling check-in confirmation for memory: " + memoryId, e);
        }
    }
}
