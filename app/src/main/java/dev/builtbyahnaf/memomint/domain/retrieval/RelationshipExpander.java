package dev.builtbyahnaf.memomint.domain.retrieval;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.builtbyahnaf.memomint.data.repository.ContextRepository;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.domain.context.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.model.Memory;

/**
 * Depth-1 relationship graph hop expander for associative candidate retrieval.
 *
 * <h2>Problem Solved &amp; Architectural Rationale (FR-07, Section 12.3 &amp; Phase 3 Plan)</h2>
 * <p>Natural human inquiries often recall an entity indirectly connected to the desired fact
 * (e.g. searching for "spare earbuds" when the target information is stored in a separate memory about
 * the "nightstand drawer"). By following depth-1 {@link MemoryRelation} edges from initial candidates,
 * the search engine expands recall to include physically and semantically related memories.</p>
 *
 * <h2>Algorithm &amp; Traversal Guarantees</h2>
 * <ol>
 *   <li><b>Snapshot Iteration:</b> Captures a snapshot of the initial candidate IDs to prevent
 *       {@link java.util.ConcurrentModificationException} while dynamically inserting new candidates.</li>
 *   <li><b>Depth-1 Constraint:</b> Only explores immediate adjacent neighbors of existing candidates.
 *       Does not traverse secondary edges, bounding graph exploration to $O(deg(c))$.</li>
 *   <li><b>Relation Attenuation:</b> Inferred relation confidence is attenuated by a decay factor ($0.80$):
 *       <pre>
 *         S_relation = relation.confidence * 0.80
 *       </pre>
 *   </li>
 *   <li><b>Active-Status Filter:</b> Newly loaded candidate memories are verified to be in
 *       {@link dev.builtbyahnaf.memomint.domain.model.MemoryStatus#ACTIVE} state before inclusion.</li>
 * </ol>
 */
public class RelationshipExpander {

    private static final String TAG = "RelationshipExpander";

    /** Attenuation factor applied to graph edge confidence during candidate retrieval hop. */
    public static final double RELATION_ATTENUATION_FACTOR = 0.80;

    private final ContextRepository contextRepository;
    private final MemoryRepository memoryRepository;

    /**
     * Constructs a RelationshipExpander with optional default repositories.
     *
     * @param contextRepository context repository for relation traversal; may be null if passed per query
     * @param memoryRepository  memory repository for loading neighbor rows; may be null
     */
    public RelationshipExpander(ContextRepository contextRepository, MemoryRepository memoryRepository) {
        this.contextRepository = contextRepository;
        this.memoryRepository = memoryRepository;
    }

    /**
     * Default constructor for test harnesses or stateless invocation.
     */
    public RelationshipExpander() {
        this(null, null);
    }

    /**
     * Expands search candidate memories by traversing depth-1 knowledge graph relations.
     *
     * @param candidateMemories map of memory ID to Memory instances (mutated by adding new candidates)
     * @param relationScores    output map mapping memory ID to its highest computed relation signal score
     * @param contextRepo       repository for querying memory relations; must not be null
     * @param memoryRepo        repository for loading un-hydrated memory instances; may be null
     */
    public void expandCandidates(
            Map<String, Memory> candidateMemories,
            Map<String, Double> relationScores,
            ContextRepository contextRepo,
            MemoryRepository memoryRepo
    ) {
        if (candidateMemories == null || relationScores == null || contextRepo == null) {
            return;
        }

        try {
            // Snapshot initial candidate IDs to avoid ConcurrentModificationException during map mutation
            List<String> snapshotIds = new ArrayList<>(candidateMemories.keySet());

            for (String candidateMemoryId : snapshotIds) {
                if (candidateMemoryId == null) continue;

                List<MemoryRelation> relations = contextRepo.getRelationsForMemory(candidateMemoryId);
                if (relations == null || relations.isEmpty()) continue;

                for (MemoryRelation relation : relations) {
                    // Identify the adjacent neighbor memory ID
                    String otherId;
                    if (candidateMemoryId.equals(relation.getSourceMemoryId())) {
                        otherId = relation.getTargetMemoryId();
                    } else {
                        otherId = relation.getSourceMemoryId();
                    }

                    if (otherId == null || otherId.equals(candidateMemoryId)) {
                        continue;
                    }

                    // Hydrate memory if not already in candidate pool
                    if (!candidateMemories.containsKey(otherId) && memoryRepo != null) {
                        Memory otherMemory = memoryRepo.findById(otherId);
                        if (otherMemory != null && otherMemory.isActive()) {
                            candidateMemories.put(otherId, otherMemory);
                        }
                    }

                    // Compute attenuated relation score: relation.confidence * 0.80
                    double sRelation = relation.getConfidence() * RELATION_ATTENUATION_FACTOR;

                    Double currentMax = relationScores.get(otherId);
                    if (currentMax == null || sRelation > currentMax) {
                        relationScores.put(otherId, sRelation);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in RelationshipExpander.expandCandidates", t);
        }
    }

    /**
     * Convenience method delegating to the 4-arg expandCandidates using the configured memoryRepository.
     */
    public void expandCandidates(
            Map<String, Memory> candidateMemories,
            Map<String, Double> relationScores,
            ContextRepository contextRepo
    ) {
        expandCandidates(candidateMemories, relationScores, contextRepo, this.memoryRepository);
    }
}
