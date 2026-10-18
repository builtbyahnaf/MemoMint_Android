package dev.builtbyahnaf.memomint.test_modules;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.builtbyahnaf.memomint.data.repository.ContextRepository;
import dev.builtbyahnaf.memomint.domain.context.ExtractionResult;
import dev.builtbyahnaf.memomint.domain.context.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.context.RelationshipInferrer;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests validating depth-1 knowledge graph relationship inference.
 *
 * <h2>Verifications Covered</h2>
 * <ul>
 *   <li>Bidirectional {@code SAME_LOCATION_AS} relation emission when memories share a location entity.</li>
 *   <li>Idempotency and deduplication (running inference multiple times avoids duplicate edges).</li>
 *   <li>Depth-1 boundary enforcement (does not traverse transitively beyond direct entity-sharing neighbors).</li>
 * </ul>
 */
public class RelationshipInferrerTest {

    private RelationshipInferrer inferrer;
    private FakeContextRepository fakeRepo;

    @Before
    public void setUp() {
        inferrer = new RelationshipInferrer();
        fakeRepo = new FakeContextRepository();
    }

    private Memory createMemory(String id, String text) {
        long now = System.currentTimeMillis();
        return new Memory(
                id,
                MemoryType.PHYSICAL_ITEM,
                text,
                "Title",
                now,
                now,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                now
        );
    }

    @Test
    public void testInfer_emitsSameLocationRelation() {
        // Memory 1 already in repo, linked to "desk drawer" location
        fakeRepo.addEntityMemoryLink("the desk drawer", "M1");

        // Memory 2 is being indexed with "the desk drawer" location
        Memory m2 = createMemory("M2", "Spare earbuds placed in the desk drawer.");
        List<ExtractionResult> extracted = Collections.singletonList(new ExtractionResult(
                "M2",
                ExtractionResult.Type.LOCATION,
                "the desk drawer",
                "the desk drawer"
        ));

        List<MemoryRelation> relations = inferrer.infer(m2, extracted, fakeRepo);

        assertNotNull(relations);
        assertEquals("Expected bidirectional relations (2 edges)", 2, relations.size());

        boolean foundM2toM1 = false;
        boolean foundM1toM2 = false;

        for (MemoryRelation rel : relations) {
            assertEquals(RelationshipInferrer.RELATION_SAME_LOCATION, rel.getRelationType());
            assertEquals(0.70, rel.getConfidence(), 0.001);
            assertEquals(RelationshipInferrer.PROVENANCE_INFERRED, rel.getProvenance());

            if ("M2".equals(rel.getSourceMemoryId()) && "M1".equals(rel.getTargetMemoryId())) {
                foundM2toM1 = true;
            } else if ("M1".equals(rel.getSourceMemoryId()) && "M2".equals(rel.getTargetMemoryId())) {
                foundM1toM2 = true;
            }
        }

        assertTrue("Expected forward relation M2 -> M1", foundM2toM1);
        assertTrue("Expected reverse relation M1 -> M2", foundM1toM2);
    }

    @Test
    public void testInfer_noDuplicates() {
        fakeRepo.addEntityMemoryLink("desk drawer", "M1");

        Memory m2 = createMemory("M2", "Earbuds in desk drawer.");
        List<ExtractionResult> extracted = Collections.singletonList(new ExtractionResult(
                "M2",
                ExtractionResult.Type.LOCATION,
                "desk drawer",
                "desk drawer"
        ));

        // First inference creates relations
        List<MemoryRelation> initialRelations = inferrer.infer(m2, extracted, fakeRepo);
        assertEquals(2, initialRelations.size());

        // Simulate persistence
        fakeRepo.saveMemoryRelations(initialRelations);

        // Second inference run on same memory should detect relations already exist
        List<MemoryRelation> secondRun = inferrer.infer(m2, extracted, fakeRepo);
        assertNotNull(secondRun);
        assertTrue("Subsequent inference run must not duplicate existing relations", secondRun.isEmpty());
    }

    @Test
    public void testInfer_depth1Boundary() {
        // Setup graph:
        // Memory A shares entity "office" with Memory B
        // Memory B shares entity "bookshelf" with Memory C
        // Memory A does NOT share "bookshelf" with Memory C
        fakeRepo.addEntityMemoryLink("office", "M_B");
        fakeRepo.addEntityMemoryLink("bookshelf", "M_B");
        fakeRepo.addEntityMemoryLink("bookshelf", "M_C");

        Memory memoryA = createMemory("M_A", "Laptop is in the office.");
        List<ExtractionResult> extractedA = Collections.singletonList(new ExtractionResult(
                "M_A",
                ExtractionResult.Type.LOCATION,
                "office",
                "office"
        ));

        List<MemoryRelation> relations = inferrer.infer(memoryA, extractedA, fakeRepo);

        // Should strictly link M_A <-> M_B, never reaching M_C
        for (MemoryRelation rel : relations) {
            assertFalse("Depth-1 boundary violated: M_C must not be linked to M_A",
                    "M_C".equals(rel.getSourceMemoryId()) || "M_C".equals(rel.getTargetMemoryId()));
            assertTrue("Relations must only involve M_A and direct neighbor M_B",
                    ("M_A".equals(rel.getSourceMemoryId()) && "M_B".equals(rel.getTargetMemoryId()))
                            || ("M_B".equals(rel.getSourceMemoryId()) && "M_A".equals(rel.getTargetMemoryId())));
        }
    }

    /**
     * In-memory FakeContextRepository stub for JVM testing of RelationshipInferrer.
     */
    private static class FakeContextRepository implements ContextRepository {

        private final Map<String, List<String>> entityToMemories = new HashMap<>();
        private final List<MemoryRelation> storedRelations = new ArrayList<>();

        void addEntityMemoryLink(String canonicalLabel, String memoryId) {
            entityToMemories.computeIfAbsent(canonicalLabel, k -> new ArrayList<>()).add(memoryId);
        }

        @Override
        public void saveContextRecords(List<ExtractionResult> results, String memoryId) {}

        @Override
        public List<ExtractionResult> getContextForMemory(String memoryId) {
            return Collections.emptyList();
        }

        @Override
        public void deleteContextForMemory(String memoryId) {}

        @Override
        public void upsertEntity(String canonicalLabel, String entityType, String originalLabel) {}

        @Override
        public String getOrCreateEntityId(String canonicalLabel, String entityType, String originalLabel) {
            return "entity-" + canonicalLabel;
        }

        @Override
        public void saveMemoryEntityLink(String memoryId, String entityId, String role, String canonicalLabel, String entityType) {}

        @Override
        public List<String> findMemoriesByEntityLabel(String canonicalLabel) {
            List<String> list = entityToMemories.get(canonicalLabel);
            return list != null ? new ArrayList<>(list) : Collections.emptyList();
        }

        @Override
        public void saveMemoryRelations(List<MemoryRelation> relations) {
            if (relations != null) {
                storedRelations.addAll(relations);
            }
        }

        @Override
        public boolean relationExists(String sourceMemoryId, String targetMemoryId, String relationType) {
            for (MemoryRelation r : storedRelations) {
                if (r.getSourceMemoryId().equals(sourceMemoryId)
                        && r.getTargetMemoryId().equals(targetMemoryId)
                        && r.getRelationType().equals(relationType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<MemoryRelation> getRelationsForMemory(String memoryId) {
            List<MemoryRelation> list = new ArrayList<>();
            for (MemoryRelation r : storedRelations) {
                if (r.getSourceMemoryId().equals(memoryId) || r.getTargetMemoryId().equals(memoryId)) {
                    list.add(r);
                }
            }
            return list;
        }

        @Override
        public void deleteRelationsForMemory(String memoryId) {
            storedRelations.removeIf(r -> r.getSourceMemoryId().equals(memoryId) || r.getTargetMemoryId().equals(memoryId));
        }

        @Override
        public void deleteRelationsAndContextForMemory(String memoryId) {
            deleteRelationsForMemory(memoryId);
        }
    }
}
