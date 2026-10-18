package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.repository.ContextRepository;
import dev.builtbyahnaf.memomint.domain.model.Memory;

/**
 * Infers depth-1 memory-to-memory relationships based on shared physical items and shared locations.
 *
 * <h2>Design &amp; Algorithmic Principles (FR-07, Section 11.5 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Depth-1 Boundary:</b> Inference is strictly limited to depth-1 direct neighbors sharing an entity.
 *       Transitive multi-hop inferences (e.g. memory A shares with B which shares with C) are disallowed
 *       to prevent graph explosion and semantic drift.</li>
 *   <li><b>Relation Typing:</b>
 *     <ul>
 *       <li>When two memories share a {@link ExtractionResult.Type#LOCATION} entity, they are linked with
 *           {@code "SAME_LOCATION_AS"} (e.g., earphones and backup cable both stored in "desk drawer").</li>
 *       <li>When two memories share an {@link ExtractionResult.Type#ITEM_NAME} entity, they are linked with
 *           {@code "RELATED_ITEM"}.</li>
 *     </ul>
 *   </li>
 *   <li><b>Bidirectional Emission:</b> Emits symmetric edges (A &rarr; B and B &rarr; A) so traversal is
 *       constant-time regardless of search direction.</li>
 *   <li><b>Deduplication &amp; Idempotency:</b> Checks {@link ContextRepository#relationExists} before creating
 *       a relation to prevent duplicate edges in SQLite.</li>
 *   <li><b>Conservative Confidence:</b> Inferred edges are tagged with confidence {@code 0.70} and provenance
 *       {@code "INFERRED"}.</li>
 * </ul>
 */
public class RelationshipInferrer {

    private static final String TAG = "RelationshipInferrer";

    public static final String RELATION_SAME_LOCATION = "SAME_LOCATION_AS";
    public static final String RELATION_RELATED_ITEM   = "RELATED_ITEM";
    public static final double DEFAULT_INFERRED_CONFIDENCE = 0.70;
    public static final String PROVENANCE_INFERRED = "INFERRED";

    /**
     * Infers memory-memory relationships between the given memory and other memories sharing entities.
     *
     * @param memory      the target memory being indexed
     * @param extracted   extracted context facts for this memory
     * @param contextRepo repository for querying existing entities and relations
     * @return list of newly inferred {@link MemoryRelation} instances to persist
     */
    public List<MemoryRelation> infer(Memory memory, List<ExtractionResult> extracted, ContextRepository contextRepo) {
        if (memory == null || memory.getId() == null || extracted == null || extracted.isEmpty() || contextRepo == null) {
            return Collections.emptyList();
        }

        List<MemoryRelation> newRelations = new ArrayList<>();
        try {
            String memoryId = memory.getId();
            long now = System.currentTimeMillis();

            // Set to track relations added in this run to prevent duplicate emissions
            Set<String> inMemoryKeys = new HashSet<>();

            for (ExtractionResult result : extracted) {
                String relationType = null;
                if (result.getType() == ExtractionResult.Type.LOCATION) {
                    relationType = RELATION_SAME_LOCATION;
                } else if (result.getType() == ExtractionResult.Type.ITEM_NAME) {
                    relationType = RELATION_RELATED_ITEM;
                }

                // Only LOCATION and ITEM_NAME entities participate in graph relationship inference
                if (relationType == null) {
                    continue;
                }

                String canonicalLabel = result.getCanonicalLabel();
                if (canonicalLabel == null || canonicalLabel.trim().isEmpty()) {
                    continue;
                }

                List<String> otherMemoryIds = contextRepo.findMemoriesByEntityLabel(canonicalLabel);
                if (otherMemoryIds == null || otherMemoryIds.isEmpty()) {
                    continue;
                }

                for (String otherId : otherMemoryIds) {
                    // Prevent self-loops
                    if (otherId == null || otherId.equals(memoryId)) {
                        continue;
                    }

                    // Direction 1: memoryId -> otherId
                    String keyFwd = memoryId + "|" + otherId + "|" + relationType;
                    if (!inMemoryKeys.contains(keyFwd) && !contextRepo.relationExists(memoryId, otherId, relationType)) {
                        MemoryRelation fwd = new MemoryRelation(
                                UUID.randomUUID().toString(),
                                memoryId,
                                relationType,
                                otherId,
                                DEFAULT_INFERRED_CONFIDENCE,
                                PROVENANCE_INFERRED,
                                now
                        );
                        newRelations.add(fwd);
                        inMemoryKeys.add(keyFwd);
                    }

                    // Direction 2: otherId -> memoryId (bidirectional)
                    String keyRev = otherId + "|" + memoryId + "|" + relationType;
                    if (!inMemoryKeys.contains(keyRev) && !contextRepo.relationExists(otherId, memoryId, relationType)) {
                        MemoryRelation rev = new MemoryRelation(
                                UUID.randomUUID().toString(),
                                otherId,
                                relationType,
                                memoryId,
                                DEFAULT_INFERRED_CONFIDENCE,
                                PROVENANCE_INFERRED,
                                now
                        );
                        newRelations.add(rev);
                        inMemoryKeys.add(keyRev);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in RelationshipInferrer for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return newRelations;
    }
}
