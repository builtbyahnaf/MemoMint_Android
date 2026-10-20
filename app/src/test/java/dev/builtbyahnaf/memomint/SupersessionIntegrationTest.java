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
import dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepositoryImpl;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.Provenance;
import dev.builtbyahnaf.memomint.domain.model.RelationType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.domain.model.SupersessionInfo;
import dev.builtbyahnaf.memomint.domain.trust.SupersessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the non-destructive supersession workflow.
 *
 * <p>Tests cover the full supersession chain: creating a SUPERSEDES relation, verifying the
 * old memory is still in the database, confirming {@link SupersessionManager#isSuperseded}
 * returns the correct result, and verifying chain retrieval (Phase 5, Section 15.4).</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SupersessionIntegrationTest {

    private MemoMintDatabase db;
    private MemoryRepositoryImpl memoryRepo;
    private MemoryRelationRepositoryImpl relationRepo;
    private SupersessionManager supersessionManager;

    @Before
    public void setUp() {
        db = new MemoMintDatabase(RuntimeEnvironment.getApplication(), null);
        memoryRepo = new MemoryRepositoryImpl(db);
        relationRepo = new MemoryRelationRepositoryImpl(db);
        supersessionManager = new SupersessionManager(relationRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts a minimal active memory and returns its UUID.
     */
    private String insertMemory(String text) {
        String id = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        memoryRepo.save(new Memory(id, MemoryType.PHYSICAL_ITEM, text, null,
                now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null));
        return id;
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Verifies that superseding creates exactly one SUPERSEDES relation.
     */
    @Test
    public void supersede_createsSupersessionRelation() {
        String oldId = insertMemory("Passport in nightstand");
        String newId = insertMemory("Passport in desk safe");

        SupersessionInfo info = supersessionManager.supersede(newId, oldId, System.currentTimeMillis());

        assertNotNull(info);
        assertEquals(newId, info.getActiveMemoryId());
        assertEquals(oldId, info.getSupersededMemoryId());

        List<MemoryRelation> chain = supersessionManager.getSupersessionChain(oldId);
        assertEquals(1, chain.size());
        assertEquals(RelationType.SUPERSEDES, chain.get(0).getRelationType());
    }

    /**
     * Verifies that the superseded memory row is still present in the database after supersession.
     * This enforces the non-destructive history invariant (Section 15.4, NFR-03).
     */
    @Test
    public void supersede_oldMemoryStillExistsInDatabase_nonDestructive() {
        String oldId = insertMemory("Spare keys on kitchen counter");
        String newId = insertMemory("Spare keys on hallway table");

        supersessionManager.supersede(newId, oldId, System.currentTimeMillis());

        // The old memory must still be in the database
        Memory oldMemory = memoryRepo.findById(oldId);
        assertNotNull("Superseded memory must not be deleted", oldMemory);
        assertEquals("Spare keys on kitchen counter", oldMemory.getOriginalText());
    }

    /**
     * Verifies that isSuperseded returns true for the superseded memory and false for the active one.
     */
    @Test
    public void isSuperseded_returnsCorrectResultForBothParties() {
        String oldId = insertMemory("Earbuds in desk drawer");
        String newId = insertMemory("Earbuds in gym bag");

        supersessionManager.supersede(newId, oldId, System.currentTimeMillis());

        assertTrue("Old memory should be marked as superseded",
                supersessionManager.isSuperseded(oldId));
        assertFalse("New memory should NOT be superseded",
                supersessionManager.isSuperseded(newId));
    }

    /**
     * Verifies that the supersession relation uses USER_ENTERED provenance and confidence 1.0.
     */
    @Test
    public void supersede_relationHasCorrectProvenanceAndConfidence() {
        String oldId = insertMemory("Watch in dresser");
        String newId = insertMemory("Watch in safe");

        supersessionManager.supersede(newId, oldId, System.currentTimeMillis());

        List<MemoryRelation> chain = supersessionManager.getSupersessionChain(newId);
        assertEquals(1, chain.size());
        MemoryRelation rel = chain.get(0);
        assertEquals(Provenance.USER_ENTERED, rel.getProvenance());
        assertEquals(1.0, rel.getConfidence(), 0.001);
    }

    /**
     * Verifies that a chain of 2 supersessions returns both relations.
     */
    @Test
    public void supersede_chainOfTwo_bothRelationsReturned() {
        String idA = insertMemory("Laptop on desk");   // oldest
        String idB = insertMemory("Laptop in bag");    // supersedes A
        String idC = insertMemory("Laptop at home");   // supersedes B

        supersessionManager.supersede(idB, idA, System.currentTimeMillis());
        supersessionManager.supersede(idC, idB, System.currentTimeMillis());

        List<MemoryRelation> chainB = supersessionManager.getSupersessionChain(idB);
        assertEquals("Memory B should have 2 relations (supersedes A, superseded by C)",
                2, chainB.size());
    }

    /**
     * Verifies that getSupersessionChain returns empty list for a memory with no relations.
     */
    @Test
    public void getSupersessionChain_noRelations_returnsEmptyList() {
        String id = insertMemory("Camera on shelf");
        List<MemoryRelation> chain = supersessionManager.getSupersessionChain(id);
        assertNotNull(chain);
        assertTrue(chain.isEmpty());
    }
}
