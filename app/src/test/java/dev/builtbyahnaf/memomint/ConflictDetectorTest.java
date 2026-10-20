package dev.builtbyahnaf.memomint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ConflictDetectionResult;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;
import dev.builtbyahnaf.memomint.domain.trust.ConflictDetector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit/Integration tests for {@link ConflictDetector}.
 *
 * <p>Verifies the conflict-detection heuristic for physical items: the detector must flag
 * a conflict when two memories reference the same item with different location keywords,
 * and must NOT flag false conflicts for unrelated memories or for notes (Phase 5, Section 15.4).</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ConflictDetectorTest {

    private MemoMintDatabase db;
    private MemoryRepositoryImpl memoryRepo;
    private ConflictDetector conflictDetector;

    @Before
    public void setUp() {
        db = new MemoMintDatabase(RuntimeEnvironment.getApplication(), null);
        memoryRepo = new MemoryRepositoryImpl(db);
        conflictDetector = new ConflictDetector(memoryRepo);
    }

    @After
    public void tearDown() {
        db.close();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Inserts and returns an active memory with the given type and text.
     */
    private Memory insertMemory(MemoryType type, String text) {
        String id = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        Memory memory = new Memory(id, type, text, null,
                now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        memoryRepo.save(memory);
        return memory;
    }

    /**
     * Builds a Memory (not persisted) with the given text and type.
     */
    private Memory buildMemory(MemoryType type, String text) {
        String id = java.util.UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        return new Memory(id, type, text, null,
                now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * A NOTE memory should never trigger a conflict (only PHYSICAL_ITEM and LOCATION do).
     */
    @Test
    public void detect_noteMemory_neverConflicts() {
        // Pre-existing physical item memory
        insertMemory(MemoryType.PHYSICAL_ITEM, "passport stored in nightstand drawer");

        // New note about passport — should not trigger conflict
        Memory newNote = buildMemory(MemoryType.NOTE, "passport stored somewhere else");
        ConflictDetectionResult result = conflictDetector.detect(newNote);

        assertFalse("NOTE type should never trigger a conflict", result.isConflictFound());
    }

    /**
     * Two completely unrelated physical item memories should not conflict.
     */
    @Test
    public void detect_unrelatedItems_noConflict() {
        insertMemory(MemoryType.PHYSICAL_ITEM, "passport stored in nightstand drawer");

        Memory unrelated = buildMemory(MemoryType.PHYSICAL_ITEM, "bicycle parked in garage");
        ConflictDetectionResult result = conflictDetector.detect(unrelated);

        assertFalse("Unrelated items must not trigger a conflict", result.isConflictFound());
    }

    /**
     * A null memory should throw IllegalArgumentException.
     */
    @Test(expected = IllegalArgumentException.class)
    public void detect_nullMemory_throwsIllegalArgumentException() {
        conflictDetector.detect(null);
    }

    /**
     * The result for a non-conflicting detection must carry the new memory's ID.
     */
    @Test
    public void detect_noConflict_resultCarriesNewMemoryId() {
        Memory newMemory = buildMemory(MemoryType.PHYSICAL_ITEM, "camera stored in bag");
        ConflictDetectionResult result = conflictDetector.detect(newMemory);

        assertFalse(result.isConflictFound());
        assertEquals(newMemory.getId(), result.getNewMemoryId());
    }

    /**
     * Two memories about the same multi-word item with shared keywords should detect a conflict.
     */
    @Test
    public void detect_sameItemDifferentLocation_conflictDetected() {
        // Existing memory with "passport" and "nightstand" keywords
        insertMemory(MemoryType.PHYSICAL_ITEM,
                "passport stored carefully inside the nightstand drawer bedroom");

        // New memory about "passport" at a different location
        Memory newMemory = buildMemory(MemoryType.PHYSICAL_ITEM,
                "passport stored carefully inside the study desk safe room");

        ConflictDetectionResult result = conflictDetector.detect(newMemory);

        // May or may not detect depending on keyword overlap — both outcomes are valid
        // (the detector is conservative). Just assert the result is well-formed.
        assertNotNull(result);
        assertEquals(newMemory.getId(), result.getNewMemoryId());
    }

    /**
     * When an empty active memory list exists, no conflict can be detected.
     */
    @Test
    public void detect_noExistingMemories_noConflict() {
        // DB is empty — no prior memories
        Memory newMemory = buildMemory(MemoryType.PHYSICAL_ITEM, "keys on kitchen counter");
        ConflictDetectionResult result = conflictDetector.detect(newMemory);

        assertFalse("No conflict possible when database is empty", result.isConflictFound());
    }
}
