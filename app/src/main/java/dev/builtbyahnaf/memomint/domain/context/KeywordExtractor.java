package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.retrieval.QueryNormalizer;
import dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary;

/**
 * Derives normalized, stopword-filtered, synonym-expanded keyword tags from memory text.
 *
 * <h2>Design &amp; Algorithmic Principles (Section 11.6 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Content Token Normalization:</b> Employs {@link QueryNormalizer} to strip punctuation,
 *       fold case, and filter out high-frequency grammatical stopwords (e.g. "where", "the", "in").</li>
 *   <li><b>Bidirectional Synonym Expansion:</b> For each substantive content token, queries
 *       {@link SynonymDictionary} to expand the term into equivalent synonyms (e.g. "backup" &rarr; "spare"),
 *       enabling lexical bridging during full-text and entity retrieval.</li>
 *   <li><b>Strict Deduplication:</b> Collects original tokens and all derived synonyms into a unified,
 *       order-preserving set to prevent redundant tags.</li>
 *   <li><b>Fail-Safe (NFR-08):</b> Catches all exceptions, logs diagnostics, and returns safely.</li>
 * </ul>
 */
public class KeywordExtractor {

    private static final String TAG = "KeywordExtractor";

    private final QueryNormalizer normalizer;
    private final SynonymDictionary synonymDictionary;

    /**
     * Constructs a KeywordExtractor with specified normalizer and synonym dictionary.
     *
     * @param normalizer        query and text normalizer; must not be null
     * @param synonymDictionary synonym dictionary; must not be null
     */
    public KeywordExtractor(QueryNormalizer normalizer, SynonymDictionary synonymDictionary) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer cannot be null");
        this.synonymDictionary = Objects.requireNonNull(synonymDictionary, "synonymDictionary cannot be null");
    }

    /**
     * Constructs a KeywordExtractor using default QueryNormalizer and SynonymDictionary instances.
     */
    public KeywordExtractor() {
        this(new QueryNormalizer(), new SynonymDictionary());
    }

    /**
     * Extracts normalized, synonym-expanded keyword facts from the memory text.
     *
     * @param memory the target memory to extract keywords from
     * @return list of {@link ExtractionResult} instances with type {@link ExtractionResult.Type#KEYWORD}
     */
    public List<ExtractionResult> extract(Memory memory) {
        if (memory == null) {
            return Collections.emptyList();
        }

        String rawText = memory.getOriginalText();
        if (rawText == null || rawText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ExtractionResult> results = new ArrayList<>();
        try {
            String memoryId = memory.getId();
            QueryNormalizer.NormalizedQuery normalized = normalizer.normalize(rawText);

            List<String> contentTokens = normalized.getContentTokens();
            if (contentTokens.isEmpty()) {
                contentTokens = normalized.getAllTokens();
            }

            Set<String> uniqueKeywords = new LinkedHashSet<>();
            for (String token : contentTokens) {
                if (token == null || token.trim().isEmpty()) continue;
                String cleanToken = token.trim();
                uniqueKeywords.add(cleanToken);

                Set<String> synonyms = synonymDictionary.getSynonyms(cleanToken);
                if (synonyms != null) {
                    for (String syn : synonyms) {
                        if (syn != null && !syn.trim().isEmpty()) {
                            uniqueKeywords.add(syn.trim());
                        }
                    }
                }
            }

            for (String keyword : uniqueKeywords) {
                results.add(new ExtractionResult(
                        memoryId,
                        ExtractionResult.Type.KEYWORD,
                        keyword,
                        keyword,
                        ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                        ItemVocabulary.VOCABULARY_VERSION
                ));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in KeywordExtractor for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return results;
    }
}
