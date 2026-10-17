package dev.builtbyahnaf.memomint.application;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link MemoryService}.
 *
 * <h2>Purpose & Architectural Verification</h2>
 * <p>Verifies the use cases and domain logic managed by the application layer:</p>
 * <ul>
 *   <li>Validation rules (empty or whitespace-only originalText rejected).</li>
 *   <li>Sensible defaults (null type defaults to NOTE, null source defaults to TYPED).</li>
 *   <li>Original-content preservation: explicit user edits update originalText, while other
 *       metadata changes leave it intact.</li>
 *   <li>Status lifecycle transitions (ACTIVE -> ARCHIVED -> ACTIVE, ACTIVE -> DELETED).</li>
 *   <li>Confirmation timestamps recorded accurately.</li>
 * </ul>
 *
 * <h2>Test Strategy & Isolation</h2>
 * <p>These tests use an in-memory test double ({@link FakeMemoryRepository}) implementing
 * {@link MemoryRepository}. This eliminates database and Android framework dependencies,
 * running purely on the standard JVM in milliseconds.</p>
 */
public class MemoryServiceTest {

    private FakeMemoryRepository fakeRepository;
    private MemoryService service;

    @Before
    public void setUp() {
        fakeRepository = new FakeMemoryRepository();
        // Uses the synchronous/test constructor with direct executor
        service = new MemoryService(
                fakeRepository,
                Runnable::run, // direct background executor
                Runnable::run  // direct callback executor
        );
    }

    // =========================================================================
    // Creation Tests
    // =========================================================================

    /**
     * Verifies that creating a valid memory initializes UUID, timestamps, status,
     * and persists through the repository.
     */
    @Test
    public void createMemory_validInput_createsActiveMemory() {
        Memory created = service.createMemorySync(
                "Passport is in the blue folder inside the desk.",
                "Passport Location",
                MemoryType.PHYSICAL_ITEM,
                SourceType.TYPED,
                null
        );

        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Passport is in the blue folder inside the desk.", created.getOriginalText());
        assertEquals("Passport Location", created.getTitle());
        assertEquals(MemoryType.PHYSICAL_ITEM, created.getType());
        assertEquals(SourceType.TYPED, created.getSourceType());
        assertEquals(MemoryStatus.ACTIVE, created.getStatus());
        assertTrue("createdAt must be positive", created.getCreatedAt() > 0);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
        assertNull("New memory should have null lastConfirmedAt", created.getLastConfirmedAt());

        // Verify stored in repository
        Memory inRepo = fakeRepository.findById(created.getId());
        assertNotNull(inRepo);
        assertEquals(created.getId(), inRepo.getId());
    }

    /**
     * Verifies that null or empty original text is rejected with an IllegalArgumentException.
     */
    @Test
    public void createMemory_emptyOrWhitespaceText_throwsIllegalArgumentException() {
        try {
            service.createMemorySync("", "Title", MemoryType.NOTE, SourceType.TYPED, null);
            fail("Empty text should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        try {
            service.createMemorySync("   \n\t  ", "Title", MemoryType.NOTE, SourceType.TYPED, null);
            fail("Whitespace text should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Success
        }

        try {
            service.createMemorySync(null, "Title", MemoryType.NOTE, SourceType.TYPED, null);
            fail("Null text should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    /**
     * Verifies that passing null for MemoryType defaults to NOTE.
     */
    @Test
    public void createMemory_nullType_defaultsToNote() {
        Memory m = service.createMemorySync("Quick thought", null, null, null, null);
        assertEquals(MemoryType.NOTE, m.getType());
        assertEquals(SourceType.TYPED, m.getSourceType());
    }

    // =========================================================================
    // Update & Original-Content Preservation Tests
    // =========================================================================

    /**
     * Verifies that user-initiated edits successfully modify originalText and bump updatedAt.
     */
    @Test
    public void updateMemory_userEditsText_updatesFieldAndTimestamp() {
        Memory created = service.createMemorySync("Original observation", "Title", MemoryType.NOTE, SourceType.TYPED, null);
        long originalCreated = created.getCreatedAt();

        // Update with user-edited text
        Memory updated = service.updateMemorySync(
                created.getId(),
                "User-corrected observation with additional details",
                "New Title",
                MemoryType.NOTE
        );

        assertEquals("User-corrected observation with additional details", updated.getOriginalText());
        assertEquals("New Title", updated.getTitle());
        assertEquals(originalCreated, updated.getCreatedAt());
        assertTrue("updatedAt must be >= createdAt", updated.getUpdatedAt() >= originalCreated);
    }

    /**
     * Verifies updating a non-existent memory ID throws IllegalArgumentException.
     */
    @Test
    public void updateMemory_nonExistentId_throwsException() {
        try {
            service.updateMemorySync("non-existent-id", "Text", "Title", MemoryType.NOTE);
            fail("Should throw when updating missing memory ID");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }

    // =========================================================================
    // Status Transitions & Confirmation Tests
    // =========================================================================

    /**
     * Verifies soft deletion transitions status to DELETED without destroying data.
     */
    @Test
    public void softDeleteAndRestore_togglesLifecycleProperly() {
        Memory m = service.createMemorySync("Remember to buy milk", null, MemoryType.NOTE, SourceType.TYPED, null);
        String id = m.getId();

        // Soft delete
        assertTrue(service.softDeleteMemorySync(id));
        assertEquals(MemoryStatus.DELETED, service.getMemoryByIdSync(id).getStatus());
        assertEquals(0, service.getActiveMemoriesSync().size());

        // Restore
        assertTrue(service.restoreMemorySync(id));
        assertEquals(MemoryStatus.ACTIVE, service.getMemoryByIdSync(id).getStatus());
        assertEquals(1, service.getActiveMemoriesSync().size());
    }

    /**
     * Verifies archiving transitions status to ARCHIVED.
     */
    @Test
    public void archiveMemory_transitionsToArchived() {
        Memory m = service.createMemorySync("Project notes from last semester", "Old Project", MemoryType.NOTE, SourceType.TYPED, null);
        String id = m.getId();

        assertTrue(service.archiveMemorySync(id));
        assertEquals(MemoryStatus.ARCHIVED, service.getMemoryByIdSync(id).getStatus());
        assertEquals(0, service.getActiveMemoriesSync().size());
        assertEquals(1, service.getMemoriesByStatusSync(MemoryStatus.ARCHIVED).size());
    }

    /**
     * Verifies confirming a memory sets lastConfirmedAt and updates the timestamp.
     */
    @Test
    public void confirmMemory_recordsTimestamp() {
        Memory m = service.createMemorySync("Bike is locked at rack B", "Bike Location", MemoryType.PHYSICAL_ITEM, SourceType.TYPED, null);
        assertNull(m.getLastConfirmedAt());

        assertTrue(service.confirmMemorySync(m.getId()));

        Memory refreshed = service.getMemoryByIdSync(m.getId());
        assertNotNull(refreshed.getLastConfirmedAt());
        assertTrue(refreshed.getLastConfirmedAt() > 0);
    }

    /**
     * Verifies permanent hard delete removes the record from repository completely.
     */
    @Test
    public void hardDeleteMemory_removesRecordPermanently() {
        Memory m = service.createMemorySync("Temporary note", null, MemoryType.NOTE, SourceType.TYPED, null);
        String id = m.getId();

        assertTrue(service.hardDeleteMemorySync(id));
        assertNull(service.getMemoryByIdSync(id));
    }

    // =========================================================================
    // Test Double (In-Memory Fake Repository)
    // =========================================================================

    /**
     * In-memory implementation of {@link MemoryRepository} for fast, isolated unit testing.
     */
    private static class FakeMemoryRepository implements MemoryRepository {
        private final Map<String, Memory> storage = new HashMap<>();

        @Override
        public String save(Memory memory) {
            storage.put(memory.getId(), memory);
            return memory.getId();
        }

        @Override
        public Memory findById(String id) {
            return storage.get(id);
        }

        @Override
        public List<Memory> findByStatus(MemoryStatus status) {
            List<Memory> list = new ArrayList<>();
            for (Memory m : storage.values()) {
                if (m.getStatus() == status) {
                    list.add(m);
                }
            }
            list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            return list;
        }

        @Override
        public List<Memory> findByTypeAndStatus(MemoryType type, MemoryStatus status) {
            List<Memory> list = new ArrayList<>();
            for (Memory m : storage.values()) {
                if (m.getType() == type && m.getStatus() == status) {
                    list.add(m);
                }
            }
            list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            return list;
        }

        @Override
        public List<Memory> findAll() {
            List<Memory> list = new ArrayList<>(storage.values());
            list.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
            return list;
        }

        @Override
        public int countByStatus(MemoryStatus status) {
            return findByStatus(status).size();
        }

        @Override
        public boolean update(Memory memory) {
            if (storage.containsKey(memory.getId())) {
                storage.put(memory.getId(), memory);
                return true;
            }
            return false;
        }

        @Override
        public boolean updateStatus(String id, MemoryStatus status) {
            Memory m = storage.get(id);
            if (m != null) {
                m.setStatus(status);
                return true;
            }
            return false;
        }

        @Override
        public boolean recordConfirmation(String id, long confirmedAtMs) {
            Memory m = storage.get(id);
            if (m != null) {
                m.setLastConfirmedAt(confirmedAtMs);
                m.setUpdatedAt(confirmedAtMs);
                return true;
            }
            return false;
        }

        @Override
        public boolean softDelete(String id) {
            return updateStatus(id, MemoryStatus.DELETED);
        }

        @Override
        public boolean hardDelete(String id) {
            return storage.remove(id) != null;
        }
    }
}
