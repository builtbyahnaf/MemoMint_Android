package dev.builtbyahnaf.memomint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for {@link ConfirmationPolicyRepositoryImpl} against an in-memory SQLite DB.
 *
 * <p>Verifies CRUD operations, upsert semantics, due-check-in queries, and cascade deletion
 * behaviour for the {@code confirmation_policies} table (Phase 5, Section 11.7).</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ConfirmationPolicyRepositoryTest {

    private MemoMintDatabase db;
    private MemoryRepositoryImpl memoryRepo;
    private ConfirmationPolicyRepositoryImpl policyRepo;

    @Before
    public void setUp() {
        // Use an in-memory database for fast, isolated tests
        db = new MemoMintDatabase(RuntimeEnvironment.getApplication(), null);
        memoryRepo = new MemoryRepositoryImpl(db);
        policyRepo = new ConfirmationPolicyRepositoryImpl(db);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Creates and persists a minimal active memory and returns its UUID.
     */
    private String insertMemory(String text) {
        String id = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Memory memory = new Memory(id, MemoryType.PHYSICAL_ITEM, text, null,
                now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        memoryRepo.save(memory);
        return id;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that a policy can be saved and retrieved by memoryId.
     */
    @Test
    public void saveOrUpdate_insertsNewPolicy_canBeRetrievedByMemoryId() {
        String memoryId = insertMemory("Passport in desk drawer");
        ConfirmationPolicy policy = new ConfirmationPolicy(
                memoryId, true, "WEEKLY", 7, null, null);

        policyRepo.saveOrUpdate(policy);

        ConfirmationPolicy loaded = policyRepo.findByMemoryId(memoryId);
        assertNotNull(loaded);
        assertEquals(memoryId, loaded.getMemoryId());
        assertTrue(loaded.isEnabled());
        assertEquals("WEEKLY", loaded.getIntervalType());
        assertEquals(7, loaded.getIntervalValue());
    }

    /**
     * Verifies that saveOrUpdate with the same memoryId replaces the existing row (upsert).
     */
    @Test
    public void saveOrUpdate_overwritesExistingPolicy_upsertSemantics() {
        String memoryId = insertMemory("Keys on kitchen counter");
        ConfirmationPolicy first = new ConfirmationPolicy(
                memoryId, true, "DAILY", 1, null, null);
        policyRepo.saveOrUpdate(first);

        // Update to WEEKLY
        ConfirmationPolicy updated = new ConfirmationPolicy(
                memoryId, true, "WEEKLY", 7, null, null);
        policyRepo.saveOrUpdate(updated);

        ConfirmationPolicy loaded = policyRepo.findByMemoryId(memoryId);
        assertNotNull(loaded);
        assertEquals("WEEKLY", loaded.getIntervalType());
        assertEquals(7, loaded.getIntervalValue());
    }

    /**
     * Verifies that findByMemoryId returns null for a memory with no policy.
     */
    @Test
    public void findByMemoryId_returnsNull_whenNoPolicyExists() {
        String memoryId = insertMemory("No policy memory");
        ConfirmationPolicy result = policyRepo.findByMemoryId(memoryId);
        assertNull(result);
    }

    /**
     * Verifies that findDueForCheckIn returns policies where enabled=1 and next_check_at <= now.
     */
    @Test
    public void findDueForCheckIn_returnsDuePolicies_ignoresFuturePolicies() {
        long past   = System.currentTimeMillis() - 1_000L;
        long future = System.currentTimeMillis() + 10_000_000L;

        String memA = insertMemory("Item A - past due");
        String memB = insertMemory("Item B - future due");
        String memC = insertMemory("Item C - disabled");

        policyRepo.saveOrUpdate(new ConfirmationPolicy(memA, true, "DAILY", 1, null, past));
        policyRepo.saveOrUpdate(new ConfirmationPolicy(memB, true, "DAILY", 1, null, future));
        policyRepo.saveOrUpdate(new ConfirmationPolicy(memC, false, "DAILY", 1, null, past));

        List<ConfirmationPolicy> due = policyRepo.findDueForCheckIn(System.currentTimeMillis());

        assertEquals(1, due.size());
        assertEquals(memA, due.get(0).getMemoryId());
    }

    /**
     * Verifies that updateNextCheckAt modifies only the scheduling fields.
     */
    @Test
    public void updateNextCheckAt_updatesSchedulingFields() {
        String memoryId = insertMemory("Laptop in bag");
        long now = System.currentTimeMillis();
        policyRepo.saveOrUpdate(new ConfirmationPolicy(memoryId, true, "DAILY", 1, null, now));

        long newNextCheck = now + 86_400_000L;
        boolean updated = policyRepo.updateNextCheckAt(memoryId, newNextCheck, now);
        assertTrue(updated);

        ConfirmationPolicy loaded = policyRepo.findByMemoryId(memoryId);
        assertNotNull(loaded);
        assertEquals(newNextCheck, (long) loaded.getNextCheckAt());
        assertEquals(now, (long) loaded.getLastPromptedAt());
    }

    /**
     * Verifies that delete removes the policy row and subsequent lookup returns null.
     */
    @Test
    public void delete_removesPolicy_returnsTrueAndNullOnSubsequentLookup() {
        String memoryId = insertMemory("Camera in shelf");
        policyRepo.saveOrUpdate(new ConfirmationPolicy(memoryId, true, "WEEKLY", 7, null, null));

        boolean deleted = policyRepo.delete(memoryId);
        assertTrue(deleted);
        assertNull(policyRepo.findByMemoryId(memoryId));
    }

    /**
     * Verifies that cascade-delete on the parent memory removes the policy automatically.
     */
    @Test
    public void cascadeDelete_removesPolicy_whenParentMemoryHardDeleted() {
        String memoryId = insertMemory("Watch in nightstand");
        policyRepo.saveOrUpdate(new ConfirmationPolicy(memoryId, true, "THREE_DAYS", 3, null, null));

        // Hard-delete the parent memory
        memoryRepo.hardDelete(memoryId);

        // Policy must be gone too
        assertNull(policyRepo.findByMemoryId(memoryId));
    }
}
