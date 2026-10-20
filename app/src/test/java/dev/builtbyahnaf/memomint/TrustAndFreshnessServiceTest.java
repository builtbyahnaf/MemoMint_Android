package dev.builtbyahnaf.memomint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.UUID;

import dev.builtbyahnaf.memomint.application.TrustAndFreshnessService;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ConfirmationPolicyRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;
import dev.builtbyahnaf.memomint.domain.model.FreshnessReport;
import dev.builtbyahnaf.memomint.domain.model.FreshnessStatus;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.domain.model.SupersessionInfo;
import dev.builtbyahnaf.memomint.domain.trust.FreshnessEvaluator;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for {@link TrustAndFreshnessService}.
 *
 * <p>Verifies application-level coordination between memory persistence, confirmation policies,
 * freshness evaluation, and supersession management.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrustAndFreshnessServiceTest {

    private MemoMintDatabase db;
    private MemoryRepositoryImpl memoryRepo;
    private ConfirmationPolicyRepositoryImpl policyRepo;
    private MemoryRelationRepositoryImpl relationRepo;
    private TrustAndFreshnessService trustService;

    @Before
    public void setUp() {
        db = new MemoMintDatabase(RuntimeEnvironment.getApplication(), null);
        memoryRepo = new MemoryRepositoryImpl(db);
        policyRepo = new ConfirmationPolicyRepositoryImpl(db);
        relationRepo = new MemoryRelationRepositoryImpl(db);
        FreshnessEvaluator evaluator = new FreshnessEvaluator();
        SupersessionManager supersessionManager = new SupersessionManager(relationRepo);
        trustService = new TrustAndFreshnessService(
                memoryRepo, policyRepo, relationRepo, evaluator, supersessionManager);
    }

    @After
    public void tearDown() {
        db.close();
    }

    private Memory createAndSaveMemory(MemoryType type, String text) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Memory memory = new Memory(id, type, text, null,
                now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        memoryRepo.save(memory);
        return memory;
    }

    @Test
    public void evaluateFreshness_evaluatesCorrectlyForUnconfirmedPhysicalItem() {
        Memory item = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Spare keys on hall table");
        FreshnessReport report = trustService.evaluateFreshness(item.getId());

        assertNotNull(report);
        assertEquals(FreshnessStatus.UNCONFIRMED, report.getStatus());
        assertTrue(report.isCheckInDue());
    }

    @Test
    public void confirmMemory_updatesTimestampAndAdvancesPolicyNextCheck() {
        Memory item = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Passport in safe");
        ConfirmationPolicy policy = new ConfirmationPolicy(
                item.getId(), true, "WEEKLY", 7, null, System.currentTimeMillis() - 1000L);
        trustService.saveOrUpdatePolicy(policy);

        long confirmTime = System.currentTimeMillis();
        boolean confirmed = trustService.confirmMemory(item.getId(), confirmTime);
        assertTrue(confirmed);

        // Verify memory lastConfirmedAt was updated
        Memory updatedMemory = memoryRepo.findById(item.getId());
        assertNotNull(updatedMemory);
        assertEquals(confirmTime, (long) updatedMemory.getLastConfirmedAt());

        // Verify policy nextCheckAt was updated to future (confirmTime + 7 days)
        ConfirmationPolicy updatedPolicy = trustService.getPolicyForMemory(item.getId());
        assertNotNull(updatedPolicy);
        assertTrue(updatedPolicy.getNextCheckAt() > confirmTime);
    }

    @Test
    public void supersede_createsRelationAndLinksMemories() {
        Memory oldMem = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Glasses on desk");
        Memory newMem = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Glasses on bedside table");

        SupersessionInfo info = trustService.supersede(newMem.getId(), oldMem.getId());
        assertNotNull(info);
        assertEquals(newMem.getId(), info.getActiveMemoryId());
        assertEquals(oldMem.getId(), info.getSupersededMemoryId());

        assertTrue(relationRepo.isSuperseded(oldMem.getId()));
        assertFalse(relationRepo.isSuperseded(newMem.getId()));
    }

    @Test
    public void getDueCheckIns_returnsDuePolicies() {
        Memory itemA = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Item A");
        Memory itemB = createAndSaveMemory(MemoryType.PHYSICAL_ITEM, "Item B");

        long past = System.currentTimeMillis() - 5000L;
        long future = System.currentTimeMillis() + 10_000_000L;

        trustService.saveOrUpdatePolicy(new ConfirmationPolicy(itemA.getId(), true, "DAILY", 1, null, past));
        trustService.saveOrUpdatePolicy(new ConfirmationPolicy(itemB.getId(), true, "DAILY", 1, null, future));

        List<ConfirmationPolicy> due = trustService.getDueCheckIns();
        assertEquals(1, due.size());
        assertEquals(itemA.getId(), due.get(0).getMemoryId());
    }
}
