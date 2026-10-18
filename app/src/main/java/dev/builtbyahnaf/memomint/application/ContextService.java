package dev.builtbyahnaf.memomint.application;

import android.util.Log;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.data.repository.ContextRepository;
import dev.builtbyahnaf.memomint.data.repository.ContextRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.context.ContextExtractor;
import dev.builtbyahnaf.memomint.domain.context.ExtractionResult;
import dev.builtbyahnaf.memomint.domain.context.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.context.RelationshipInferrer;
import dev.builtbyahnaf.memomint.domain.model.Memory;

/**
 * Application service coordinating asynchronous context extraction, entity linking,
 * and knowledge-graph relationship inference upon memory creation or update.
 *
 * <h2>Architectural Principles (Layer B, FR-03, NFR-08 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Non-Blocking Background Execution:</b> Context extraction and graph inference run
 *       asynchronously on a dedicated background worker {@link Executor}, guaranteeing zero UI-thread
 *       jank or ANRs during memory capture.</li>
 *   <li><b>Fault Isolation Invariant (NFR-08):</b> Extraction failures or SQLite errors never bubble up
 *       or abort memory saving. Verbatim memory persistence is always guaranteed.</li>
 *   <li><b>Idempotent Pipeline:</b> On re-extraction (e.g. following memory updates), existing context
 *       records and derived relations are purged atomically before fresh context is indexed.</li>
 * </ul>
 */
public class ContextService {

    private static final String TAG = "ContextService";

    private final ContextExtractor contextExtractor;
    private final ContextRepository contextRepo;
    private final RelationshipInferrer relationshipInferrer;
    private final Executor backgroundExecutor;

    /**
     * Constructs a ContextService with all dependencies injected.
     *
     * @param contextExtractor     domain context extractor orchestrator; must not be null
     * @param contextRepo          context and relation data repository; must not be null
     * @param relationshipInferrer domain relationship inferrer; must not be null
     * @param backgroundExecutor   executor for running extraction off the UI thread; must not be null
     */
    public ContextService(
            ContextExtractor contextExtractor,
            ContextRepository contextRepo,
            RelationshipInferrer relationshipInferrer,
            Executor backgroundExecutor
    ) {
        this.contextExtractor = Objects.requireNonNull(contextExtractor, "contextExtractor cannot be null");
        this.contextRepo = Objects.requireNonNull(contextRepo, "contextRepo cannot be null");
        this.relationshipInferrer = Objects.requireNonNull(relationshipInferrer, "relationshipInferrer cannot be null");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor cannot be null");
    }

    /**
     * Static factory creating a production-ready ContextService backed by {@link MemoMintDatabase}.
     *
     * @param db                 database helper singleton
     * @param backgroundExecutor worker executor
     * @return configured ContextService instance
     */
    public static ContextService create(MemoMintDatabase db, Executor backgroundExecutor) {
        ContextExtractor extractor = ContextExtractor.create();
        ContextRepository repo = new ContextRepositoryImpl(db);
        RelationshipInferrer inferrer = new RelationshipInferrer();
        return new ContextService(extractor, repo, inferrer, backgroundExecutor);
    }

    /**
     * Asynchronously extracts context facts, links entities, and infers memory relationships
     * on the background executor.
     *
     * @param memory the memory to process; if null, operation is a no-op
     */
    public void extractContextAsync(Memory memory) {
        if (memory == null) {
            return;
        }

        backgroundExecutor.execute(() -> extractContextSync(memory));
    }

    /**
     * Synchronously executes the context extraction and relationship inference pipeline.
     * Suitable for worker tasks, test harnesses, or background batch processors.
     *
     * @param memory the memory to process
     */
    public void extractContextSync(Memory memory) {
        if (memory == null || memory.getId() == null) {
            return;
        }

        try {
            String memoryId = memory.getId();

            // 1. Clean existing derived records on update to avoid duplicate accumulation
            contextRepo.deleteRelationsAndContextForMemory(memoryId);

            // 2. Extract facts using the orchestrator
            List<ExtractionResult> extractedResults = contextExtractor.extractAll(memory);

            // 3. Persist raw context records
            if (extractedResults != null && !extractedResults.isEmpty()) {
                contextRepo.saveContextRecords(extractedResults, memoryId);

                // 4. Upsert entities and link memories for physical items and locations
                for (ExtractionResult result : extractedResults) {
                    if (result.getType() == ExtractionResult.Type.ITEM_NAME || result.getType() == ExtractionResult.Type.LOCATION) {
                        String entityId = contextRepo.getOrCreateEntityId(
                                result.getCanonicalLabel(),
                                result.getType().name(),
                                result.getContent()
                        );
                        contextRepo.saveMemoryEntityLink(
                                memoryId,
                                entityId,
                                result.getType().name(),
                                result.getCanonicalLabel(),
                                result.getType().name()
                        );
                    }
                }

                // 5. Infer depth-1 relationships from shared entities
                List<MemoryRelation> relations = relationshipInferrer.infer(memory, extractedResults, contextRepo);

                // 6. Persist newly inferred relations
                if (relations != null && !relations.isEmpty()) {
                    contextRepo.saveMemoryRelations(relations);
                }
            }
        } catch (Throwable t) {
            // Invariant (FR-03, NFR-08): Never re-throw, never crash caller
            Log.e(TAG, "Failed extracting context for memory: " + (memory != null ? memory.getId() : "null"), t);
        }
    }

    public ContextExtractor getContextExtractor() {
        return contextExtractor;
    }

    public ContextRepository getContextRepo() {
        return contextRepo;
    }

    public RelationshipInferrer getRelationshipInferrer() {
        return relationshipInferrer;
    }
}
