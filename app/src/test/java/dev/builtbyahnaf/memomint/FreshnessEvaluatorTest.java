package dev.builtbyahnaf.memomint;

import org.junit.Before;
import org.junit.Test;

import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.FreshnessReport;
import dev.builtbyahnaf.memomint.domain.model.FreshnessStatus;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FreshnessEvaluator}.
 *
 * <p>All tests are pure-Java with no Android dependencies and no database access.
 * Time values are chosen to produce deterministic outcomes regardless of wall-clock time
 * by fixing {@code lastConfirmedAt} relative to a known "now" offset.</p>
 *
 * <p>Freshness state transitions under test (Section 15.2):</p>
 * <ul>
 *   <li>{@code CONFIRMED_FRESH}: elapsed ≤ 75% of interval</li>
 *   <li>{@code AGING}: 75% &lt; elapsed ≤ 100% of interval</li>
 *   <li>{@code STALE}: elapsed &gt; interval</li>
 *   <li>{@code UNCONFIRMED}: never confirmed</li>
 *   <li>{@code EVERGREEN}: NOTE type without policy</li>
 * </ul>
 */
public class FreshnessEvaluatorTest {

    private static final long MILLIS_PER_DAY = 86_400_000L;

    private FreshnessEvaluator evaluator;

    @Before
    public void setUp() {
        evaluator = new FreshnessEvaluator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a minimal Memory with a specific type and optional lastConfirmedAt.
     */
    private Memory makeMemory(MemoryType type, Long lastConfirmedAt) {
        long now = System.currentTimeMillis();
        return new Memory(
                "test-id", type, "Sample memory text", null,
                now - MILLIS_PER_DAY * 30, // created 30 days ago
                now,
                SourceType.TYPED, null, MemoryStatus.ACTIVE, lastConfirmedAt);
    }

    /**
     * Creates a weekly ConfirmationPolicy (enabled).
     */
    private ConfirmationPolicy weeklyPolicy(String memoryId) {
        long now = System.currentTimeMillis();
        return new ConfirmationPolicy(memoryId, true, "WEEKLY", 7, null,
                now + MILLIS_PER_DAY * 7);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * A NOTE memory without a policy should always be EVERGREEN.
     */
    @Test
    public void evaluate_noteWithoutPolicy_returnsEvergreen() {
        Memory note = makeMemory(MemoryType.NOTE, null);
        FreshnessReport report = evaluator.evaluate(note, null);

        assertNotNull(report);
        assertEquals(FreshnessStatus.EVERGREEN, report.getStatus());
        assertFalse(report.isCheckInDue());
        assertEquals(1.0, report.getFreshnessScore(), 0.001);
    }

    /**
     * A PHYSICAL_ITEM never confirmed should be UNCONFIRMED.
     */
    @Test
    public void evaluate_physicalItemNeverConfirmed_returnsUnconfirmed() {
        Memory item = makeMemory(MemoryType.PHYSICAL_ITEM, null);
        FreshnessReport report = evaluator.evaluate(item, weeklyPolicy("test-id"));

        assertEquals(FreshnessStatus.UNCONFIRMED, report.getStatus());
        assertTrue(report.isCheckInDue());
    }

    /**
     * Confirmed 1 day ago under a 7-day policy (14% elapsed) → CONFIRMED_FRESH.
     */
    @Test
    public void evaluate_confirmedRecently_returnsConfirmedFresh() {
        long oneDayAgo = System.currentTimeMillis() - MILLIS_PER_DAY;
        Memory item = makeMemory(MemoryType.PHYSICAL_ITEM, oneDayAgo);
        FreshnessReport report = evaluator.evaluate(item, weeklyPolicy("test-id"));

        assertEquals(FreshnessStatus.CONFIRMED_FRESH, report.getStatus());
        assertFalse(report.isCheckInDue());
        assertTrue(report.getFreshnessScore() > 0.8);
    }

    /**
     * Confirmed 6 days ago under a 7-day policy (86% elapsed) → AGING.
     */
    @Test
    public void evaluate_confirmed6DaysUnder7DayPolicy_returnsAging() {
        long sixDaysAgo = System.currentTimeMillis() - (6 * MILLIS_PER_DAY);
        Memory item = makeMemory(MemoryType.PHYSICAL_ITEM, sixDaysAgo);
        FreshnessReport report = evaluator.evaluate(item, weeklyPolicy("test-id"));

        assertEquals(FreshnessStatus.AGING, report.getStatus());
        assertTrue(report.isCheckInDue());
    }

    /**
     * Confirmed 10 days ago under a 7-day policy (143% elapsed) → STALE.
     */
    @Test
    public void evaluate_confirmed10DaysUnder7DayPolicy_returnsStale() {
        long tenDaysAgo = System.currentTimeMillis() - (10 * MILLIS_PER_DAY);
        Memory item = makeMemory(MemoryType.PHYSICAL_ITEM, tenDaysAgo);
        FreshnessReport report = evaluator.evaluate(item, weeklyPolicy("test-id"));

        assertEquals(FreshnessStatus.STALE, report.getStatus());
        assertTrue(report.isCheckInDue());
        assertTrue(report.getFreshnessScore() < 0.5);
    }

    /**
     * A null memory argument should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void evaluate_nullMemory_throwsIllegalArgumentException() {
        evaluator.evaluate(null, null);
    }

    /**
     * A disabled policy should behave the same as a null policy (EVERGREEN for NOTE).
     */
    @Test
    public void evaluate_disabledPolicy_noteBecomesEvergreen() {
        Memory note = makeMemory(MemoryType.NOTE, null);
        ConfirmationPolicy disabled = new ConfirmationPolicy(
                "test-id", false, "DISABLED", 0, null, null);

        FreshnessReport report = evaluator.evaluate(note, disabled);
        assertEquals(FreshnessStatus.EVERGREEN, report.getStatus());
    }

    /**
     * The freshness score for STALE must be in the valid range [0.01, 1.0].
     */
    @Test
    public void evaluate_freshnessScore_alwaysInValidRange() {
        // Very stale: confirmed 365 days ago under 7-day policy
        long veryOld = System.currentTimeMillis() - (365 * MILLIS_PER_DAY);
        Memory item = makeMemory(MemoryType.PHYSICAL_ITEM, veryOld);
        FreshnessReport report = evaluator.evaluate(item, weeklyPolicy("test-id"));

        assertTrue(report.getFreshnessScore() >= 0.01);
        assertTrue(report.getFreshnessScore() <= 1.0);
    }

    /**
     * humanReadableStatus must be a non-empty, non-null string for every status.
     */
    @Test
    public void evaluate_humanReadableStatus_neverNullOrEmpty() {
        // Test all statuses
        MemoryType[] types = {MemoryType.NOTE, MemoryType.PHYSICAL_ITEM, MemoryType.LOCATION};
        for (MemoryType type : types) {
            Memory m = makeMemory(type, null);
            FreshnessReport report = evaluator.evaluate(m, null);
            assertNotNull("humanReadableStatus must not be null", report.getHumanReadableStatus());
            assertFalse("humanReadableStatus must not be empty",
                    report.getHumanReadableStatus().trim().isEmpty());
        }
    }
}
