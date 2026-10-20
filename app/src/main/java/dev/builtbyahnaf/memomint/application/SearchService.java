package dev.builtbyahnaf.memomint.application;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dev.builtbyahnaf.memomint.data.repository.ContextRepository;
import dev.builtbyahnaf.memomint.data.repository.FileReferenceRepository;
import dev.builtbyahnaf.memomint.data.repository.FileSearchRepository;
import dev.builtbyahnaf.memomint.data.repository.SearchRepository;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.FileSearchResult;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.retrieval.FuzzyMatcher;
import dev.builtbyahnaf.memomint.domain.retrieval.QueryNormalizer;
import dev.builtbyahnaf.memomint.domain.retrieval.RelationshipExpander;
import dev.builtbyahnaf.memomint.domain.retrieval.RelevanceScorer;
import dev.builtbyahnaf.memomint.domain.retrieval.SearchResult;

/**
 * Application service coordinating the deterministic retrieval pipeline with Phase 3 relationship graph expansion.
 *
 * <h2>Purpose &amp; Architectural Invariants (Section 12 &amp; Phase 3 Plan)</h2>
 * <p>In accordance with Layer B (Application Services), this service coordinates candidate queries,
 * synonym expansion, fuzzy typo matching, knowledge-graph depth-1 relationship hops, hybrid score calculation,
 * and verifiable explanation generation. It ensures all heavy search computation and database I/O run off the UI thread (NFR-02, NFR-03).</p>
 *
 * <h2>Pipeline Stages</h2>
 * <ol>
 *   <li><b>1. Normalization &amp; Tokenization:</b> {@link QueryNormalizer} strips punctuation, normalizes
 *       Unicode, folds case, extracts substantive content tokens, and classifies intent.</li>
 *   <li><b>2. Synonym &amp; Concept Expansion:</b> {@link dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary}
 *       expands query tokens into bidirectional equivalent sets (e.g. "backup" &rarr; "spare").</li>
 *   <li><b>3. Candidate Retrieval:</b> Retrieves candidate memories from SQLite FTS5 (BM25 ranking),
 *       metadata matching, and active memory pools.</li>
 *   <li><b>3b. Relationship Graph Expansion:</b> {@link RelationshipExpander} traverses depth-1 {@link dev.builtbyahnaf.memomint.domain.context.MemoryRelation}
 *       edges to discover linked memories (e.g. items in the same location).</li>
 *   <li><b>4. Scoring &amp; Normalization:</b> {@link RelevanceScorer} combines FTS text matches ($S_{\text{text}}$),
 *       synonym matches ($S_{\text{synonym}}$), metadata alignment ($S_{\text{meta}}$), fuzzy matches ($S_{\text{fuzzy}}$),
 *       and relation graph matches ($S_{\text{relation}}$).</li>
 *   <li><b>5. Relevance vs Trust Separation:</b> Freshness status is assigned independently without corrupting semantic rank.</li>
 *   <li><b>6. Explanation Generation:</b> Provenance tags are constructed describing actual matched signals.</li>
 *   <li><b>7. Ranked Result Delivery:</b> Candidates are sorted descending by composite score and returned.</li>
 * </ol>
 */
public class SearchService {

    /** Minimum composite score threshold required for a result to be presented. */
    public static final double MINIMUM_CONFIDENCE_THRESHOLD = 0.20;

    /** Default candidate pool size. */
    public static final int DEFAULT_CANDIDATE_LIMIT = 50;

    /**
     * Callback interface delivering search results to presentation callers on the UI thread.
     */
    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Throwable throwable);
    }

    private final SearchRepository searchRepository;
    private final ContextRepository contextRepository;
    private final RelationshipExpander relationshipExpander;
    private final FileSearchRepository fileSearchRepository;
    private final FileReferenceRepository fileReferenceRepository;
    private final QueryNormalizer normalizer;
    private final dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary synonymDictionary;
    private final FuzzyMatcher fuzzyMatcher;
    private final RelevanceScorer relevanceScorer;
    private final Executor backgroundExecutor;
    private final Executor callbackExecutor;
    private dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepository memoryRelationRepository;

    /**
     * Sets the relation repository to enable Phase 5 supersession history filtering.
     *
     * @param memoryRelationRepository repository for checking superseded memories
     */
    public void setMemoryRelationRepository(dev.builtbyahnaf.memomint.data.repository.MemoryRelationRepository memoryRelationRepository) {
        this.memoryRelationRepository = memoryRelationRepository;
    }

    /**
     * Production constructor initializing default background executor and main thread callback delivery.
     */
    public SearchService(SearchRepository searchRepository) {
        this(
                searchRepository,
                null,
                null,
                null,
                null,
                new QueryNormalizer(),
                new dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary(),
                new FuzzyMatcher(),
                new RelevanceScorer(),
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper())::post
        );
    }

    /**
     * Overload accepting context repository and relationship expander for Phase 3 graph search.
     */
    public SearchService(
            SearchRepository searchRepository,
            ContextRepository contextRepository,
            RelationshipExpander relationshipExpander
    ) {
        this(
                searchRepository,
                contextRepository,
                relationshipExpander,
                null,
                null,
                new QueryNormalizer(),
                new dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary(),
                new FuzzyMatcher(),
                new RelevanceScorer(),
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper())::post
        );
    }

    /**
     * Overload accepting file repositories for Phase 4 unified document and memory search.
     */
    public SearchService(
            SearchRepository searchRepository,
            ContextRepository contextRepository,
            RelationshipExpander relationshipExpander,
            FileSearchRepository fileSearchRepository,
            FileReferenceRepository fileReferenceRepository
    ) {
        this(
                searchRepository,
                contextRepository,
                relationshipExpander,
                fileSearchRepository,
                fileReferenceRepository,
                new QueryNormalizer(),
                new dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary(),
                new FuzzyMatcher(),
                new RelevanceScorer(),
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper())::post
        );
    }

    /**
     * Constructor allowing injection of custom executors (e.g. for testing) without context repo.
     */
    public SearchService(
            SearchRepository searchRepository,
            QueryNormalizer normalizer,
            dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary synonymDictionary,
            FuzzyMatcher fuzzyMatcher,
            RelevanceScorer relevanceScorer,
            Executor backgroundExecutor,
            Executor callbackExecutor
    ) {
        this(
                searchRepository,
                null,
                null,
                null,
                null,
                normalizer,
                synonymDictionary,
                fuzzyMatcher,
                relevanceScorer,
                backgroundExecutor,
                callbackExecutor
        );
    }

    /**
     * Constructor allowing injection of all Phase 1-3 pipeline components.
     */
    public SearchService(
            SearchRepository searchRepository,
            ContextRepository contextRepository,
            RelationshipExpander relationshipExpander,
            QueryNormalizer normalizer,
            dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary synonymDictionary,
            FuzzyMatcher fuzzyMatcher,
            RelevanceScorer relevanceScorer,
            Executor backgroundExecutor,
            Executor callbackExecutor
    ) {
        this(
                searchRepository,
                contextRepository,
                relationshipExpander,
                null,
                null,
                normalizer,
                synonymDictionary,
                fuzzyMatcher,
                relevanceScorer,
                backgroundExecutor,
                callbackExecutor
        );
    }

    /**
     * Full constructor allowing complete injection of all Phase 1-4 pipeline components.
     */
    public SearchService(
            SearchRepository searchRepository,
            ContextRepository contextRepository,
            RelationshipExpander relationshipExpander,
            FileSearchRepository fileSearchRepository,
            FileReferenceRepository fileReferenceRepository,
            QueryNormalizer normalizer,
            dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary synonymDictionary,
            FuzzyMatcher fuzzyMatcher,
            RelevanceScorer relevanceScorer,
            Executor backgroundExecutor,
            Executor callbackExecutor
    ) {
        this.searchRepository = Objects.requireNonNull(searchRepository, "searchRepository cannot be null");
        this.contextRepository = contextRepository;
        this.relationshipExpander = relationshipExpander;
        this.fileSearchRepository = fileSearchRepository;
        this.fileReferenceRepository = fileReferenceRepository;
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer cannot be null");
        this.synonymDictionary = Objects.requireNonNull(synonymDictionary, "synonymDictionary cannot be null");
        this.fuzzyMatcher = Objects.requireNonNull(fuzzyMatcher, "fuzzyMatcher cannot be null");
        this.relevanceScorer = Objects.requireNonNull(relevanceScorer, "relevanceScorer cannot be null");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor cannot be null");
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor cannot be null");
    }

    /**
     * Asynchronously executes a search query and returns ranked results to the callback.
     *
     * @param rawQuery user search query
     * @param callback consumer for ranked SearchResult items
     */
    public void search(String rawQuery, Callback<List<SearchResult>> callback) {
        backgroundExecutor.execute(() -> {
            try {
                List<SearchResult> results = searchSync(rawQuery);
                deliverSuccess(callback, results);
            } catch (Throwable t) {
                deliverError(callback, t);
            }
        });
    }

    /**
     * Synchronously executes the retrieval pipeline.
     * Safe to invoke on background threads, in workers, or during unit test execution.
     *
     * @param rawQuery raw search text
     * @return sorted list of {@link SearchResult} items meeting the minimum confidence threshold
     */
    public List<SearchResult> searchSync(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Stage 1: Query Normalization & Tokenization
        QueryNormalizer.NormalizedQuery normalized = normalizer.normalize(rawQuery);
        List<String> contentTokens = normalized.getContentTokens();
        if (contentTokens.isEmpty()) {
            return Collections.emptyList();
        }

        // Stage 2: Synonym & Concept Expansion
        Map<String, Set<String>> expansions = synonymDictionary.expandTokens(contentTokens);
        Set<String> allQueryTerms = new HashSet<>(contentTokens);
        for (Set<String> syns : expansions.values()) {
            allQueryTerms.addAll(syns);
        }

        // Stage 3: Candidate Retrieval
        StringBuilder ftsQueryBuilder = new StringBuilder();
        for (String term : allQueryTerms) {
            if (ftsQueryBuilder.length() > 0) ftsQueryBuilder.append(" ");
            ftsQueryBuilder.append(term);
        }

        List<SearchRepository.FtsCandidate> ftsCandidates = searchRepository.searchFts(
                ftsQueryBuilder.toString(), DEFAULT_CANDIDATE_LIMIT);

        // Map candidate memories by ID to avoid duplicates
        Map<String, Memory> candidateMemories = new HashMap<>();
        Map<String, Double> ftsBm25Scores = new HashMap<>();

        for (SearchRepository.FtsCandidate fc : ftsCandidates) {
            candidateMemories.put(fc.getMemory().getId(), fc.getMemory());
            ftsBm25Scores.put(fc.getMemory().getId(), fc.getRawBm25());
        }

        // Supplementary candidates: all active memories for fuzzy matching and fallback
        List<Memory> activePool = searchRepository.getAllActiveMemories(100);
        for (Memory m : activePool) {
            if (!candidateMemories.containsKey(m.getId())) {
                candidateMemories.put(m.getId(), m);
            }
        }

        // Stage 3b: Relationship Graph Hop Expansion
        Map<String, Double> relationScores = new HashMap<>();
        if (relationshipExpander != null && contextRepository != null) {
            relationshipExpander.expandCandidates(candidateMemories, relationScores, contextRepository, null);
        }

        // Stage 4 & 5 & 6: Score Normalization, Trust Separation, and Explanation Generation
        List<SearchResult> scoredResults = new ArrayList<>();

        for (Memory memory : candidateMemories.values()) {
            Double sRel = relationScores.get(memory.getId());
            double relationScore = (sRel != null) ? sRel : 0.0;
            SearchResult result = scoreCandidate(
                    memory,
                    normalized,
                    expansions,
                    ftsBm25Scores.get(memory.getId()),
                    relationScore
            );
            if (result.getFinalScore() >= MINIMUM_CONFIDENCE_THRESHOLD) {
                scoredResults.add(result);
            }
        }

        // Phase 4: Document Retrieval (files_fts)
        if (fileSearchRepository != null) {
            List<FileSearchResult> fileHits = fileSearchRepository.searchFiles(rawQuery, 20);
            for (FileSearchResult hit : fileHits) {
                if (fileReferenceRepository != null) {
                    FileReference ref = fileReferenceRepository.findById(hit.getFileId());
                    if (ref != null && !ref.isReadyToSearch()) {
                        continue; // skip inaccessible or excluded documents
                    }
                }

                Memory docMemory = new Memory(
                        UUID.randomUUID().toString(),
                        MemoryType.DOCUMENT_CONTEXT,
                        hit.getSnippet(),
                        hit.getTitle(),
                        System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        dev.builtbyahnaf.memomint.domain.model.SourceType.IMPORTED,
                        hit.getFileId(),
                        dev.builtbyahnaf.memomint.domain.model.MemoryStatus.ACTIVE,
                        null
                );

                List<String> explanations = new ArrayList<>();
                explanations.add("Found in document '" + hit.getTitle() + "'");

                SearchResult docResult = new SearchResult(
                        docMemory,
                        hit.getScore(),
                        hit.getScore(),
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        explanations,
                        hit.getMatchedTerms(),
                        SearchResult.FreshnessStatus.NOT_APPLICABLE
                );

                if (docResult.getFinalScore() >= MINIMUM_CONFIDENCE_THRESHOLD) {
                    scoredResults.add(docResult);
                }
            }
        }

        // Stage 7: Ranked Delivery (sort descending by final score)
        Collections.sort(scoredResults);

        return scoredResults;
    }

    /**
     * Evaluates a single candidate memory against the query signals and constructs an explainable result.
     */
    private SearchResult scoreCandidate(
            Memory memory,
            QueryNormalizer.NormalizedQuery query,
            Map<String, Set<String>> expansions,
            Double rawBm25,
            double sRelation
    ) {
        List<String> contentTokens = query.getContentTokens();
        String fullMemoryText = ((memory.getTitle() != null ? memory.getTitle() + " " : "")
                + memory.getOriginalText()).toLowerCase(Locale.ROOT);

        QueryNormalizer.NormalizedQuery memoryNorm = normalizer.normalize(fullMemoryText);
        Set<String> memoryTokens = new HashSet<>(memoryNorm.getAllTokens());

        List<String> explanations = new ArrayList<>();
        List<String> matchedTerms = new ArrayList<>();

        // Signal A: Exact Text Overlap
        int exactMatches = 0;
        List<String> exactMatchTokens = new ArrayList<>();
        for (String qToken : contentTokens) {
            if (memoryTokens.contains(qToken) || fullMemoryText.contains(qToken)) {
                exactMatches++;
                exactMatchTokens.add(qToken);
                if (!matchedTerms.contains(qToken)) {
                    matchedTerms.add(qToken);
                }
            }
        }

        for (String token : query.getAllTokens()) {
            if (fullMemoryText.contains(token) && !matchedTerms.contains(token)) {
                matchedTerms.add(token);
            }
        }
        String cleanRaw = query.getRawQuery().trim().toLowerCase(Locale.ROOT);
        if (!cleanRaw.isEmpty() && fullMemoryText.contains(cleanRaw) && !matchedTerms.contains(cleanRaw)) {
            matchedTerms.add(cleanRaw);
        }

        double sText;
        if (rawBm25 != null) {
            sText = RelevanceScorer.normalizeBm25(rawBm25);
        } else {
            sText = RelevanceScorer.computeTermOverlapRatio(exactMatches, contentTokens.size());
        }

        if (!exactMatchTokens.isEmpty()) {
            explanations.add("Exact match on " + formatTermsList(exactMatchTokens));
        }

        // Signal B: Synonym Match
        int synonymMatches = 0;
        List<String> synonymExplanations = new ArrayList<>();
        for (String qToken : contentTokens) {
            Set<String> syns = expansions.get(qToken);
            if (syns != null) {
                for (String syn : syns) {
                    if (memoryTokens.contains(syn) || fullMemoryText.contains(syn)) {
                        synonymMatches++;
                        matchedTerms.add(syn);
                        synonymExplanations.add("'" + qToken + "' matched '" + syn + "'");
                        break;
                    }
                }
            }
        }

        double sSynonym = RelevanceScorer.computeTermOverlapRatio(synonymMatches, contentTokens.size());
        if (!synonymExplanations.isEmpty()) {
            explanations.add("Synonym match: " + String.join(", ", synonymExplanations));
        }

        // Signal C: Metadata Alignment
        double sMeta = 0.0;
        if (query.getIntent() == QueryNormalizer.QueryIntent.LOCATION_LOOKUP) {
            if (memory.getType() == MemoryType.PHYSICAL_ITEM || memory.getType() == MemoryType.LOCATION) {
                sMeta += 0.70;
                explanations.add("Matched physical item / location intent");
            }
        } else if (query.getIntent() == QueryNormalizer.QueryIntent.TEMPORAL_LOOKUP) {
            if (memory.getType() == MemoryType.REMINDER) {
                sMeta += 0.70;
                explanations.add("Matched reminder intent");
            }
        }

        if (memory.getTitle() != null && !memory.getTitle().isEmpty()) {
            String titleLower = memory.getTitle().toLowerCase(Locale.ROOT);
            for (String qToken : contentTokens) {
                if (titleLower.contains(qToken)) {
                    sMeta = Math.min(1.0, sMeta + 0.30);
                    break;
                }
            }
        }

        // Signal D: Fuzzy Matching
        double maxFuzzySim = 0.0;
        List<String> fuzzyExplanations = new ArrayList<>();

        for (String qToken : contentTokens) {
            if (!exactMatchTokens.contains(qToken)) {
                FuzzyMatcher.MatchResult match = fuzzyMatcher.findBestMatch(qToken, memoryTokens);
                if (match != null && match.isMatch()) {
                    if (match.getSimilarity() > maxFuzzySim) {
                        maxFuzzySim = match.getSimilarity();
                    }
                    matchedTerms.add(match.getMatchedTerm());
                    int percent = (int) Math.round(match.getSimilarity() * 100);
                    fuzzyExplanations.add("'" + qToken + "' matched '" + match.getMatchedTerm() + "' (" + percent + "% similarity)");
                }
            }
        }

        double sFuzzy = maxFuzzySim;
        if (!fuzzyExplanations.isEmpty()) {
            explanations.add("Fuzzy match: " + String.join(", ", fuzzyExplanations));
        }

        // Signal E: Relationship graph match explanation
        if (sRelation > 0.0) {
            explanations.add("Related memory match (graph hop)");
        }

        // Combine all 5 signals via RelevanceScorer
        double finalScore = relevanceScorer.computeFinalScore(sText, sSynonym, sMeta, sFuzzy, sRelation);

        // Phase 5: Check if candidate memory is superseded by a newer record
        if (memoryRelationRepository != null && memoryRelationRepository.isSuperseded(memory.getId())) {
            explanations.add("Historical record (superseded by newer memory)");
            finalScore = finalScore * 0.6; // de-prioritize superseded record in ranking
        }

        // Freshness status calculation
        SearchResult.FreshnessStatus freshness = SearchResult.calculateFreshness(memory);

        return new SearchResult(
                memory,
                finalScore,
                sText,
                sSynonym,
                sMeta,
                sFuzzy,
                sRelation,
                explanations,
                matchedTerms,
                freshness
        );
    }

    private String formatTermsList(List<String> terms) {
        List<String> quoted = new ArrayList<>();
        for (String t : terms) {
            quoted.add("'" + t + "'");
        }
        return String.join(", ", quoted);
    }

    private <T> void deliverSuccess(Callback<T> callback, T data) {
        if (callback != null) {
            callbackExecutor.execute(() -> callback.onSuccess(data));
        }
    }

    private <T> void deliverError(Callback<T> callback, Throwable t) {
        if (callback != null) {
            callbackExecutor.execute(() -> callback.onError(t));
        }
    }
}
