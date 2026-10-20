package dev.builtbyahnaf.memomint.application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;

/**
 * Receives the daily check-in audit alarm from {@link ConfirmationScheduler}.
 *
 * <h2>Execution Flow (Section 15.5, FR-09)</h2>
 * <ol>
 *   <li>Queries all enabled {@link ConfirmationPolicy} records where {@code next_check_at <= now}.</li>
 *   <li>Ensures the notification channel exists via {@link CheckInNotificationHelper#createNotificationChannel(Context)}.</li>
 *   <li>Iterates through due policies, loading the corresponding memory and posting a check-in prompt.</li>
 *   <li>Re-arms the recurring daily audit alarm with {@link ConfirmationScheduler#scheduleRecurringAudit(Context)}.</li>
 * </ol>
 */
public class CheckInReceiver extends BroadcastReceiver {

    /** Action matching periodic check-in audit broadcasts. */
    public static final String ACTION_CHECKIN_AUDIT = ConfirmationScheduler.ACTION_CHECKIN_AUDIT;

    private static final String TAG = "CheckInReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        Log.i(TAG, "Executing check-in audit sweep");

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

            List<ConfirmationPolicy> duePolicies = service.getDueCheckIns();
            CheckInNotificationHelper.createNotificationChannel(context);

            if (duePolicies != null) {
                for (ConfirmationPolicy policy : duePolicies) {
                    if (policy != null && policy.getMemoryId() != null) {
                        Memory memory = memoryRepo.findById(policy.getMemoryId());
                        if (memory != null) {
                            CheckInNotificationHelper.postCheckInNotification(
                                    context,
                                    memory.getId(),
                                    memory.getTitle(),
                                    memory.getOriginalText()
                            );
                        }
                    }
                }
            }

            ConfirmationScheduler.scheduleRecurringAudit(context);
        } catch (Exception e) {
            Log.e(TAG, "Error executing check-in audit sweep: " + e.getMessage(), e);
        }
    }
}
