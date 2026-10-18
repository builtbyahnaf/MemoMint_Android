package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.retrieval.QueryNormalizer;
import dev.builtbyahnaf.memomint.domain.retrieval.SynonymDictionary;

/**
 * Orchestrator coordinating all deterministic context sub-extractors across memory text.
 *
 * <h2>Architectural Principles &amp; Design Decisions (FR-03, NFR-08 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Sub-Extractor Orchestration:</b> Executes the 5 specialized domain extractors in a defined sequence:
 *     <ol>
 *       <li>{@link ItemExtractor} &mdash; detects physical items and equipment nouns.</li>
 *       <li>{@link LocationExtractor} &mdash; detects containers, furniture, and spatial locations.</li>
 *       <li>{@link QuantityExtractor} &mdash; detects numeric counts and packaging units.</li>
 *       <li>{@link DateTimeExtractor} &mdash; detects relative and absolute temporal mentions.</li>
 *       <li>{@link KeywordExtractor} &mdash; derives normalized, synonym-expanded retrieval tags.</li>
 *     </ol>
 *   </li>
 *   <li><b>Sequential Execution (Why not parallel?):</b> Each sub-extractor runs in &lt; 1ms via precompiled
 *       regex and hash lookups. Thread context-switching overhead and task submission latency on mobile
 *       drastically outweigh any theoretical parallelism speedup. Sequential execution guarantees zero lock
 *       contention and predictable execution time.</li>
 *   <li><b>Fail-Safe Invariant (NFR-08):</b> Individual extractors catch their own failures. In addition,
 *       {@code extractAll} wraps the orchestration loop in a fail-safe boundary ensuring exceptions are never
 *       propagated to callers.</li>
 *   <li><b>Verbatim Memory Invariance (FR-03):</b> Neither this orchestrator nor any sub-extractor ever
 *       modifies the input {@link Memory#getOriginalText()}.</li>
 * </ul>
 */
public class ContextExtractor {

    private static final String TAG = "ContextExtractor";

    private final ItemExtractor itemExtractor;
    private final LocationExtractor locationExtractor;
    private final QuantityExtractor quantityExtractor;
    private final DateTimeExtractor dateTimeExtractor;
    private final KeywordExtractor keywordExtractor;

    /**
     * Constructs a ContextExtractor orchestrator with the specified sub-extractors.
     */
    public ContextExtractor(
            ItemExtractor itemExtractor,
            LocationExtractor locationExtractor,
            QuantityExtractor quantityExtractor,
            DateTimeExtractor dateTimeExtractor,
            KeywordExtractor keywordExtractor
    ) {
        this.itemExtractor = Objects.requireNonNull(itemExtractor, "itemExtractor cannot be null");
        this.locationExtractor = Objects.requireNonNull(locationExtractor, "locationExtractor cannot be null");
        this.quantityExtractor = Objects.requireNonNull(quantityExtractor, "quantityExtractor cannot be null");
        this.dateTimeExtractor = Objects.requireNonNull(dateTimeExtractor, "dateTimeExtractor cannot be null");
        this.keywordExtractor = Objects.requireNonNull(keywordExtractor, "keywordExtractor cannot be null");
    }

    /**
     * Factory method creating a ContextExtractor with specified normalizer and synonym dictionary.
     *
     * @param normalizer        query and text normalizer
     * @param synonymDictionary synonym dictionary
     * @return configured ContextExtractor instance
     */
    public static ContextExtractor create(QueryNormalizer normalizer, SynonymDictionary synonymDictionary) {
        return new ContextExtractor(
                new ItemExtractor(),
                new LocationExtractor(),
                new QuantityExtractor(),
                new DateTimeExtractor(),
                new KeywordExtractor(normalizer, synonymDictionary)
        );
    }

    /**
     * Factory method creating a ContextExtractor with default settings.
     *
     * @return standard ContextExtractor instance
     */
    public static ContextExtractor create() {
        return create(new QueryNormalizer(), new SynonymDictionary());
    }

    /**
     * Sequentially extracts all structured context facts from the given memory.
     *
     * @param memory the target memory to extract context from
     * @return combined list of all extracted facts; never null, never throws
     */
    public List<ExtractionResult> extractAll(Memory memory) {
        if (memory == null) {
            return Collections.emptyList();
        }

        List<ExtractionResult> accumulator = new ArrayList<>();
        try {
            // 1. Physical items
            List<ExtractionResult> items = itemExtractor.extract(memory);
            if (items != null && !items.isEmpty()) {
                accumulator.addAll(items);
            }

            // 2. Spatial locations
            List<ExtractionResult> locations = locationExtractor.extract(memory);
            if (locations != null && !locations.isEmpty()) {
                accumulator.addAll(locations);
            }

            // 3. Quantities
            List<ExtractionResult> quantities = quantityExtractor.extract(memory);
            if (quantities != null && !quantities.isEmpty()) {
                accumulator.addAll(quantities);
            }

            // 4. Temporal mentions
            List<ExtractionResult> temporal = dateTimeExtractor.extract(memory);
            if (temporal != null && !temporal.isEmpty()) {
                accumulator.addAll(temporal);
            }

            // 5. Normalized & expanded keywords
            List<ExtractionResult> keywords = keywordExtractor.extract(memory);
            if (keywords != null && !keywords.isEmpty()) {
                accumulator.addAll(keywords);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error during extractAll for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return accumulator;
    }

    public ItemExtractor getItemExtractor() {
        return itemExtractor;
    }

    public LocationExtractor getLocationExtractor() {
        return locationExtractor;
    }

    public QuantityExtractor getQuantityExtractor() {
        return quantityExtractor;
    }

    public DateTimeExtractor getDateTimeExtractor() {
        return dateTimeExtractor;
    }

    public KeywordExtractor getKeywordExtractor() {
        return keywordExtractor;
    }
}
