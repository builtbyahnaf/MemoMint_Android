package dev.builtbyahnaf.memomint.application;

import java.util.List;

import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepository;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepository;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.FreshnessReport;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.SupersessionInfo;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;

/**
 * Application service coordinating memory trust, freshness evaluation, check-in scheduling,
 * and non-destructive supersession workflows.
 *
 * <h2>Purpose &amp; Architectural Invariants (Section 15, FR-09, Section 11.7)</h2>
 * <ul>
 *   <li><b>Zero False Verification:</b> Never marks a memory as confirmed without an explicit
 *       user action or timestamp.</li>
 *   <li><b>Non-Destructive Supersession:</b> When an item is relocated or assertions updated,
 *       the old record is never deleted. Supersession relations are created to retain audit lineage.</li>
 *   <li><b>Multi-Speed Freshness:</b> Coordinates between memory entities and optional
 *       confirmation policies to deliver real-time confidence scores and check-in schedules.</li>
 * </ul>
 */
public class TrustAndFreshnessService {

    private final MemoryRepository memoryRepository;
    private final ConfirmationPolicyRepository policyRepository;
    private final MemoryRelationRepository relationRepository;
    private final FreshnessEvaluator freshnessEvaluator;
    private final SupersessionManager supersessionManager;

    /**
     * Constructs a new {@link TrustAndFreshnessService} with all required repositories and domain managers.
     *
     * @param memoryRepository    repository managing memory persistence; must not be null
     * @param policyRepository    repository managing confirmation policies; must not be null
     * @param relationRepository  repository managing memory relation edges; must not be null
     * @param freshnessEvaluator  domain service evaluating memory decay; must not be null
     * @param supersessionManager domain service managing supersession lineage; must not be null
     * @throws IllegalArgumentException if any parameter is null
     */
    public TrustAndFreshnessService(
            MemoryRepository memoryRepository,
            ConfirmationPolicyRepository policyRepository,
            MemoryRelationRepository relationRepository,
            FreshnessEvaluator freshnessEvaluator,
            SupersessionManager supersessionManager) {
        if (memoryRepository == null) {
            throw new IllegalArgumentException("memoryRepository must not be null");
        }
        if (policyRepository == null) {
            throw new IllegalArgumentException("policyRepository must not be null");
        }
        if (relationRepository == null) {
            throw new IllegalArgumentException("relationRepository must not be null");
        }
        if (freshnessEvaluator == null) {
            throw new IllegalArgumentException("freshnessEvaluator must not be null");
        }
        if (supersessionManager == null) {
            throw new IllegalArgumentException("supersessionManager must not be null");
        }

        this.memoryRepository = memoryRepository;
        this.policyRepository = policyRepository;
        this.relationRepository = relationRepository;
        this.freshnessEvaluator = freshnessEvaluator;
        this.supersessionManager = supersessionManager;
    }

    /**
     * Evaluates the current temporal freshness of the specified memory record.
     *
     * @param memoryId stable UUID of the memory to evaluate
     * @return an immutable {@link FreshnessReport} with classification and decay score
     * @throws IllegalArgumentException if memoryId is null or if the memory does not exist
     */
    public FreshnessReport evaluateFreshness(String memoryId) {
        if (memoryId == null) {
            throw new IllegalArgumentException("memoryId must not be null");
        }
        Memory memory = memoryRepository.findById(memoryId);
        if (memory == null) {
            throw new IllegalArgumentException("Memory not found for id: " + memoryId);
        }

        ConfirmationPolicy policy = policyRepository.findByMemoryId(memoryId);
        return freshnessEvaluator.evaluate(memory, policy);
    }

    /**
     * Records explicit user confirmation for a memory and reschedules the next check-in window.
     *
     * @param memoryId      stable UUID of the confirmed memory
     * @param confirmedAtMs UTC epoch milliseconds of user confirmation
     * @return true if confirmation and policy rescheduling completed successfully
     */
    public boolean confirmMemory(String memoryId, long confirmedAtMs) {
        memoryRepository.recordConfirmation(memoryId, confirmedAtMs);

        ConfirmationPolicy policy = policyRepository.findByMemoryId(memoryId);
        if (policy != null && policy.isEnabled()) {
            long nextCheckAt = confirmedAtMs + intervalMillis(policy);
            policyRepository.updateNextCheckAt(memoryId, nextCheckAt, confirmedAtMs);
        }
        return true;
    }

    /**
     * Establishes a directed supersession relationship between an active and a superseded memory.
     *
     * @param activeMemoryId     UUID of the new, primary memory record
     * @param supersededMemoryId UUID of the older, archived-in-place memory record
     * @return immutable {@link SupersessionInfo} snapshot
     */
    public SupersessionInfo supersede(String activeMemoryId, String supersededMemoryId) {
        return supersessionManager.supersede(activeMemoryId, supersededMemoryId, System.currentTimeMillis());
    }

    /**
     * Persists or updates the confirmation policy for a memory, calculating default check-in windows.
     *
     * @param policy the confirmation policy to save or update; must not be null with a valid memoryId
     * @throws IllegalArgumentException if policy or its memoryId is null
     */
    public void saveOrUpdatePolicy(ConfirmationPolicy policy) {
        if (policy == null || policy.getMemoryId() == null) {
            throw new IllegalArgumentException("policy and policy.getMemoryId() must not be null");
        }

        String intervalType = policy.getIntervalType();
        if ("DAILY".equals(intervalType)) {
            policy.setIntervalValue(1);
        } else if ("THREE_DAYS".equals(intervalType)) {
            policy.setIntervalValue(3);
        } else if ("WEEKLY".equals(intervalType)) {
            policy.setIntervalValue(7);
        } else if ("DISABLED".equals(intervalType)) {
            policy.setIntervalValue(0);
        } else {
            policy.setIntervalValue(7);
        }

        if (policy.isEnabled() && policy.getNextCheckAt() == null) {
            long nextCheckAt = System.currentTimeMillis() + intervalMillis(policy);
            policy.setNextCheckAt(nextCheckAt);
        }

        policyRepository.saveOrUpdate(policy);
    }

    /**
     * Retrieves the confirmation policy configured for the given memory UUID.
     *
     * @param memoryId stable UUID of the memory
     * @return the {@link ConfirmationPolicy}, or null if no policy is configured
     */
    public ConfirmationPolicy getPolicyForMemory(String memoryId) {
        return policyRepository.findByMemoryId(memoryId);
    }

    /**
     * Retrieves all active confirmation policies that are currently due for a verification prompt.
     *
     * @return list of due {@link ConfirmationPolicy} records; never null
     */
    public List<ConfirmationPolicy> getDueCheckIns() {
        return policyRepository.findDueForCheckIn(System.currentTimeMillis());
    }

    /**
     * Calculates the interval in milliseconds corresponding to the policy's configured interval type.
     *
     * @param p confirmation policy
     * @return duration in milliseconds
     */
    private long intervalMillis(ConfirmationPolicy p) {
        if (p == null || p.getIntervalType() == null) {
            return 7 * 86_400_000L;
        }
        long days;
        switch (p.getIntervalType()) {
            case "DAILY":
                days = 1L;
                break;
            case "THREE_DAYS":
                days = 3L;
                break;
            case "WEEKLY":
                days = 7L;
                break;
            case "DISABLED":
                days = 0L;
                break;
            default:
                days = 7L;
                break;
        }
        return days * 86_400_000L;
    }
}
