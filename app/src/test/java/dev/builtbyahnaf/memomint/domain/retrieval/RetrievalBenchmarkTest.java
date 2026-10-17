package dev.builtbyahnaf.memomint.domain.retrieval;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.builtbyahnaf.memomint.application.SearchService;
import dev.builtbyahnaf.memomint.data.repository.SearchRepository;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Standardized retrieval benchmark test evaluating Recall@1, Recall@3, Recall@5, and MRR.
 *
 * <h2>Evaluation Standard (Section 12.6 & Phase 2 Plan §6)</h2>
 * <p>Measures retrieval quality on a curated, version-controlled benchmark fixture:</p>
 * <ul>
 *   <li><b>Direct Queries:</b> Query terms appear verbatim in original text.</li>
 *   <li><b>Indirect Synonym Queries:</b> Query uses alternate terminology ("backup" → "spare",
 *       "earbuds" → "earphones", "wardrobe" → "closet", "dust blower" → "air duster").</li>
 *   <li><b>Typo Queries:</b> Contains single/double character errors ("keybord", "drwer", "earfones").</li>
 *   <li><b>Negative Queries:</b> Irrelevant queries where no match exists; engine must return zero or
 *       low-confidence candidates without hallucinating false positive answers.</li>
 * </ul>
 *
 * <h2>Target Metrics</h2>
 * <ul>
 *   <li>Recall@1 $\ge 70\%$</li>
 *   <li>Recall@3 $\ge 85\%$</li>
 *   <li>Mean Reciprocal Rank (MRR) $\ge 0.75$</li>
 *   <li>Latency under 50ms per query</li>
 * </ul>
 */
public class RetrievalBenchmarkTest {

    private SearchService searchService;
    private final Map<String, Memory> benchmarkDataset = new HashMap<>();

    @Before
    public void setUp() {
        populateBenchmarkDataset();

        FakeBenchmarkSearchRepository fakeRepo = new FakeBenchmarkSearchRepository(
                new ArrayList<>(benchmarkDataset.values())
        );

        searchService = new SearchService(
                fakeRepo,
                new QueryNormalizer(),
                new SynonymDictionary(),
                new FuzzyMatcher(),
                new RelevanceScorer(),
                Runnable::run, // direct executor
                Runnable::run  // direct callback executor
        );
    }

    private void populateBenchmarkDataset() {
        long now = System.currentTimeMillis();

        addMemory("M1", "Desk Cables", "My spare USB-C cable is in the second drawer of my desk.",
                MemoryType.PHYSICAL_ITEM, now - 86400000L);

        addMemory("M2", "Audio Gear", "Wireless earphones and charging case placed on the bedside nightstand.",
                MemoryType.PHYSICAL_ITEM, now - 172800000L);

        addMemory("M3", "Winter Clothing", "Winter coat and thermal jacket stored inside the master bedroom wardrobe.",
                MemoryType.PHYSICAL_ITEM, now - 259200000L);

        addMemory("M4", "Cleaning Supplies", "Blue compressed air duster for mechanical keyboard cleaning is in the storage closet.",
                MemoryType.PHYSICAL_ITEM, now - 345600000L);

        addMemory("M5", "Identity Documents", "Passport and international travel document in the fireproof safe under the bed.",
                MemoryType.PHYSICAL_ITEM, now - 432000000L);

        addMemory("M6", "Key Hook", "House keys and car keychain are on the magnetic hook by the front door.",
                MemoryType.PHYSICAL_ITEM, now - 518400000L);

        addMemory("M7", "Medication", "Prescription allergy medicine and daily pills are in the bathroom medicine cabinet.",
                MemoryType.PHYSICAL_ITEM, now - 604800000L);

        addMemory("M8", "Dental Visit", "Dentist appointment scheduled for next Tuesday at 3pm.",
                MemoryType.REMINDER, now - 691200000L);

        addMemory("M9", "MemoMint Specs", "Architectural notes: Project MemoMint uses offline SQLite with FTS5 and WAL mode.",
                MemoryType.NOTE, now - 777600000L);

        addMemory("M10", "Receipts", "Grocery receipt and appliance purchase invoice inside the tax document folder.",
                MemoryType.NOTE, now - 864000000L);
    }

    private void addMemory(String id, String title, String originalText, MemoryType type, long timestamp) {
        benchmarkDataset.put(id, new Memory(
                id,
                type,
                originalText,
                title,
                timestamp,
                timestamp,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                timestamp
        ));
    }

    private static class TestCase {
        final String query;
        final Set<String> expectedIds;
        final String category;

        TestCase(String query, String category, String... expectedIds) {
            this.query = query;
            this.category = category;
            this.expectedIds = new HashSet<>(Arrays.asList(expectedIds));
        }
    }

    @Test
    public void evaluateRetrievalBenchmark_meetsQualityAndLatencyTargets() {
        List<TestCase> positiveCases = Arrays.asList(
                // 1. Direct Keyword Queries
                new TestCase("spare USB-C cable in drawer", "DIRECT", "M1"),
                new TestCase("wireless earphones on nightstand", "DIRECT", "M2"),
                new TestCase("winter coat and thermal jacket", "DIRECT", "M3"),
                new TestCase("air duster for keyboard", "DIRECT", "M4"),
                new TestCase("passport in fireproof safe", "DIRECT", "M5"),
                new TestCase("house keys on magnetic hook", "DIRECT", "M6"),
                new TestCase("prescription allergy medicine", "DIRECT", "M7"),
                new TestCase("dentist appointment Tuesday", "DIRECT", "M8"),
                new TestCase("MemoMint SQLite FTS5", "DIRECT", "M9"),

                // 2. Indirect Synonym Queries
                new TestCase("Where did I put the backup cable for my desk?", "SYNONYM", "M1"), // backup ↔ spare
                new TestCase("Where are my backup earbuds?", "SYNONYM", "M2"),                 // earbuds ↔ earphones
                new TestCase("Where is my outerwear in the closet?", "SYNONYM", "M3"),         // closet ↔ wardrobe, outerwear ↔ coat
                new TestCase("dust blower for keyboard cleaning", "SYNONYM", "M4"),            // dust blower ↔ air duster
                new TestCase("Where is my travel document?", "SYNONYM", "M5"),                 // travel document ↔ passport
                new TestCase("Where did I leave my keychain?", "SYNONYM", "M6"),               // keychain ↔ keys
                new TestCase("Where did I leave my pills?", "SYNONYM", "M7"),                  // pills ↔ medicine

                // 3. Typo Queries
                new TestCase("keybord cleaner", "TYPO", "M4"),                                 // keybord → keyboard
                new TestCase("cable in drwer", "TYPO", "M1"),                                  // drwer → drawer
                new TestCase("earfones on nightstand", "TYPO", "M2")                           // earfones → earphones
        );

        int totalQueries = positiveCases.size();
        int recallAt1Hits = 0;
        int recallAt3Hits = 0;
        int recallAt5Hits = 0;
        double sumReciprocalRank = 0.0;
        long totalExecutionTimeNs = 0L;

        for (TestCase tc : positiveCases) {
            long start = System.nanoTime();
            List<SearchResult> results = searchService.searchSync(tc.query);
            long elapsed = System.nanoTime() - start;
            totalExecutionTimeNs += elapsed;

            // Evaluate rank of first matching expected memory
            int firstMatchRank = -1;
            for (int r = 0; r < results.size(); r++) {
                if (tc.expectedIds.contains(results.get(r).getMemory().getId())) {
                    firstMatchRank = r + 1; // 1-based rank
                    break;
                }
            }

            if (firstMatchRank == 1) {
                recallAt1Hits++;
            }
            if (firstMatchRank > 0 && firstMatchRank <= 3) {
                recallAt3Hits++;
            }
            if (firstMatchRank > 0 && firstMatchRank <= 5) {
                recallAt5Hits++;
            }
            if (firstMatchRank > 0) {
                sumReciprocalRank += (1.0 / firstMatchRank);
            }
        }

        double recallAt1 = (double) recallAt1Hits / totalQueries;
        double recallAt3 = (double) recallAt3Hits / totalQueries;
        double recallAt5 = (double) recallAt5Hits / totalQueries;
        double mrr = sumReciprocalRank / totalQueries;
        double avgLatencyMs = (totalExecutionTimeNs / 1_000_000.0) / totalQueries;

        System.out.printf(Locale.ROOT,
                "=== Retrieval Benchmark Results ===%n" +
                "Total Positive Queries: %d%n" +
                "Recall@1: %.2f%% (Target: >= 70%%)%n" +
                "Recall@3: %.2f%% (Target: >= 85%%)%n" +
                "Recall@5: %.2f%% (Target: >= 90%%)%n" +
                "Mean Reciprocal Rank (MRR): %.3f (Target: >= 0.75)%n" +
                "Average Latency: %.2f ms (Target: < 50ms)%n",
                totalQueries, recallAt1 * 100, recallAt3 * 100, recallAt5 * 100, mrr, avgLatencyMs
        );

        // Assert Target Standards (Phase 2 Plan §6)
        assertTrue(String.format(Locale.ROOT, "Recall@1 (%.2f) must be >= 0.70", recallAt1), recallAt1 >= 0.70);
        assertTrue(String.format(Locale.ROOT, "Recall@3 (%.2f) must be >= 0.85", recallAt3), recallAt3 >= 0.85);
        assertTrue(String.format(Locale.ROOT, "MRR (%.3f) must be >= 0.75", mrr), mrr >= 0.75);
        assertTrue(String.format(Locale.ROOT, "Average latency (%.2f ms) must be < 50ms", avgLatencyMs), avgLatencyMs < 50.0);

        // 4. Negative / Distractor Queries
        List<String> negativeQueries = Arrays.asList(
                "Where did I leave my scuba diving fins?",
                "Quantum physics textbook third edition",
                "Grandma's recipe for chocolate chip cookies"
        );

        for (String negQuery : negativeQueries) {
            List<SearchResult> negResults = searchService.searchSync(negQuery);
            // Engine must not return high confidence hallucinated matches
            for (SearchResult res : negResults) {
                assertTrue("Distractor query matches must have low score (< 0.50)", res.getFinalScore() < 0.50);
            }
        }
    }

    /**
     * In-memory search repository double supporting JVM test execution.
     * Simulates SQLite FTS full-text matching with BM25 proxy scores.
     */
    private static class FakeBenchmarkSearchRepository implements SearchRepository {

        private final List<Memory> memories;

        FakeBenchmarkSearchRepository(List<Memory> memories) {
            this.memories = memories;
        }

        @Override
        public List<FtsCandidate> searchFts(String ftsQuery, int limit) {
            List<FtsCandidate> candidates = new ArrayList<>();
            String[] queryTerms = ftsQuery.toLowerCase(Locale.ROOT).split("\\s+");

            for (Memory m : memories) {
                String text = ((m.getTitle() != null ? m.getTitle() + " " : "")
                        + m.getOriginalText()).toLowerCase(Locale.ROOT);

                int matchCount = 0;
                for (String term : queryTerms) {
                    if (term.length() >= 3 && text.contains(term)) {
                        matchCount++;
                    }
                }

                if (matchCount > 0) {
                    // SQLite FTS5 returns negative BM25 values
                    double rawBm25 = -1.0 * (matchCount * 1.5);
                    candidates.add(new FtsCandidate(m, rawBm25, m.getOriginalText()));
                }
            }

            // Sort by BM25 ascending (more negative = more relevant)
            candidates.sort((a, b) -> Double.compare(a.getRawBm25(), b.getRawBm25()));

            if (candidates.size() > limit) {
                return new ArrayList<>(candidates.subList(0, limit));
            }
            return candidates;
        }

        @Override
        public List<Memory> searchByMetadata(MemoryType type, int limit) {
            List<Memory> matches = new ArrayList<>();
            for (Memory m : memories) {
                if (type == null || m.getType() == type) {
                    matches.add(m);
                }
            }
            return matches;
        }

        @Override
        public List<Memory> getAllActiveMemories(int limit) {
            List<Memory> active = new ArrayList<>();
            for (Memory m : memories) {
                if (m.getStatus() == MemoryStatus.ACTIVE) {
                    active.add(m);
                }
            }
            return active;
        }
    }
}
