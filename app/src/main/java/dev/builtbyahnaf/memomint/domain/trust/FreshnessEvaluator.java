package dev.builtbyahnaf.memomint.domain.trust;

import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.FreshnessReport;
import dev.builtbyahnaf.memomint.domain.model.FreshnessStatus;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Pure Java domain service that calculates freshness status, decay scores, and check-in
 * requirements for memories based on their confirmation history and scheduling policies.
 *
 * <h2>Purpose</h2>
 * <p>Implements the confidence decay model (Section 15.2, FR-09) to estimate how likely a
 * memory assertion (e.g. location of physical items) remains accurate over time without
 * requiring network or Android framework dependencies.</p>
 *
 * <h2>Decay Formula</h2>
 * <p>When a memory is aging or stale, the decay curve uses the half-life formulation:
 * {@code score = exp(-ln(2) * (elapsedMs / intervalMs))}, clamped to the range [0.01, 1.0].</p>
 */
public class FreshnessEvaluator {

    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final long DEFAULT_INTERVAL_MS = 7 * MILLIS_PER_DAY;
    private static final double LN2 = 0.693147180559945;

    /**
     * Constructs a stateless {@link FreshnessEvaluator} instance.
     */
    public FreshnessEvaluator() {}

    /**
     * Evaluates the temporal freshness of a memory using an optional confirmation policy
     * and the current system time.
     *
     * @param memory the memory to evaluate; must not be null
     * @param policy the confirmation policy configured for this memory; may be null
     * @return an immutable {@link FreshnessReport} containing status, scores, and timestamps
     * @throws IllegalArgumentException if memory is null
     */
    public FreshnessReport evaluate(Memory memory, ConfirmationPolicy policy) {
        if (memory == null) {
            throw new IllegalArgumentException("memory must not be null");
        }

        long nowMillis = System.currentTimeMillis();

        long intervalMs = DEFAULT_INTERVAL_MS;
        if (policy != null && policy.isEnabled() && !"DISABLED".equals(policy.getIntervalType())) {
            String intervalType = policy.getIntervalType();
            if ("DAILY".equals(intervalType)) {
                intervalMs = 1 * MILLIS_PER_DAY;
            } else if ("THREE_DAYS".equals(intervalType)) {
                intervalMs = 3 * MILLIS_PER_DAY;
            } else if ("WEEKLY".equals(intervalType)) {
                intervalMs = 7 * MILLIS_PER_DAY;
            } else {
                intervalMs = DEFAULT_INTERVAL_MS;
            }
        }

        Long lastConfirmedAt = memory.getLastConfirmedAt();
        long referenceTimestamp = (lastConfirmedAt == null) ? memory.getCreatedAt() : lastConfirmedAt;
        long elapsedMs = Math.max(0L, nowMillis - referenceTimestamp);

        FreshnessStatus status;
        if (memory.getType() == MemoryType.NOTE && (policy == null || !policy.isEnabled())) {
            status = FreshnessStatus.EVERGREEN;
        } else if (lastConfirmedAt == null) {
            status = FreshnessStatus.UNCONFIRMED;
        } else if (elapsedMs <= (long) (intervalMs * 0.75)) {
            status = FreshnessStatus.CONFIRMED_FRESH;
        } else if (elapsedMs <= intervalMs) {
            status = FreshnessStatus.AGING;
        } else {
            status = FreshnessStatus.STALE;
        }

        boolean checkInDue = (status == FreshnessStatus.AGING
                || status == FreshnessStatus.STALE
                || status == FreshnessStatus.UNCONFIRMED);

        double freshnessScore;
        if (status == FreshnessStatus.EVERGREEN || status == FreshnessStatus.CONFIRMED_FRESH) {
            freshnessScore = 1.0;
        } else {
            double rawScore = Math.exp(-LN2 * ((double) elapsedMs / (double) intervalMs));
            freshnessScore = Math.max(0.01, Math.min(1.0, rawScore));
        }

        String humanReadableStatus;
        switch (status) {
            case CONFIRMED_FRESH:
                humanReadableStatus = "Confirmed fresh";
                break;
            case AGING:
                humanReadableStatus = "Check-in suggested";
                break;
            case STALE:
                humanReadableStatus = "Overdue check-in";
                break;
            case UNCONFIRMED:
                humanReadableStatus = "Not yet confirmed";
                break;
            case EVERGREEN:
            default:
                humanReadableStatus = "Always fresh";
                break;
        }

        long lastConfirmedAtVal = (lastConfirmedAt == null) ? 0L : lastConfirmedAt;

        return new FreshnessReport(
                memory.getId(),
                status,
                lastConfirmedAtVal,
                elapsedMs,
                checkInDue,
                humanReadableStatus,
                freshnessScore
        );
    }

    /**
     * Evaluates the freshness of a memory without an explicit policy using default interval parameters.
     *
     * @param memory    the memory to evaluate; must not be null
     * @param nowMillis reference timestamp (ignored in favor of current time in delegate call)
     * @return an immutable {@link FreshnessReport}
     * @throws IllegalArgumentException if memory is null
     */
    public FreshnessReport evaluateWithoutPolicy(Memory memory, long nowMillis) {
        return evaluate(memory, null);
    }
}
