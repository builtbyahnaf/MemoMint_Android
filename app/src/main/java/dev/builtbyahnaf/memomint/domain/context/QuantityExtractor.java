package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.builtbyahnaf.memomint.domain.model.Memory;

/**
 * Extracts numeric quantity facts and container packaging counts from memory text.
 *
 * <h2>Design &amp; Algorithmic Principles (FR-03, Section 11.4 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Word-to-Number Normalization:</b> Converts natural language number words ("one" through "ten")
 *       into standardized Arabic numerals (1 through 10) for uniform entity indexing.</li>
 *   <li><b>Unit Packaging Support:</b> Captures quantifying container prefixes (e.g., "boxes of", "bags of",
 *       "bottles of", "packs of") alongside the target noun phrase.</li>
 *   <li><b>Stopword Boundary Guard:</b> Employs negative lookahead to prevent trailing prepositional phrases
 *       (such as "in", "on", "for", "at") from being greedily swallowed into the quantity noun phrase.</li>
 *   <li><b>Canonical Formatting:</b> Produces normalized labels such as {@code "3 boxes of pens"} or
 *       {@code "3 pens"} consistently across verbatim digit and word-number representations.</li>
 *   <li><b>Safe Execution (NFR-08):</b> Catches all parsing anomalies and returns empty or partial results
 *       without throwing exceptions.</li>
 * </ul>
 */
public class QuantityExtractor {

    private static final String TAG = "QuantityExtractor";

    private static final Map<String, Integer> WORD_TO_INT = new HashMap<>();

    static {
        WORD_TO_INT.put("one", 1);
        WORD_TO_INT.put("two", 2);
        WORD_TO_INT.put("three", 3);
        WORD_TO_INT.put("four", 4);
        WORD_TO_INT.put("five", 5);
        WORD_TO_INT.put("six", 6);
        WORD_TO_INT.put("seven", 7);
        WORD_TO_INT.put("eight", 8);
        WORD_TO_INT.put("nine", 9);
        WORD_TO_INT.put("ten", 10);
    }

    private static final Pattern QUANTITY_PATTERN = Pattern.compile(
            "\\b(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+((?:(?:boxes?|bags?|bottles?|pieces?|rolls?|packs?)\\s+of\\s+)?[a-zA-Z0-9_-]+(?:\\s+(?!(?:for|in|on|at|to|from|by|with|of|the|a|an)\\b)[a-zA-Z0-9_-]+)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts quantity mentions from memory text.
     *
     * @param memory the target memory to extract from
     * @return deduplicated list of {@link ExtractionResult} with type {@link ExtractionResult.Type#QUANTITY}
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
            Map<String, ExtractionResult> deduplicated = new LinkedHashMap<>();

            Matcher matcher = QUANTITY_PATTERN.matcher(rawText);
            while (matcher.find()) {
                String numToken = matcher.group(1).trim().toLowerCase(Locale.ROOT);
                String nounPhrase = matcher.group(2).trim().toLowerCase(Locale.ROOT);

                // Convert word-numbers to integer digits; keep digit numbers as-is
                String numericString;
                if (WORD_TO_INT.containsKey(numToken)) {
                    numericString = String.valueOf(WORD_TO_INT.get(numToken));
                } else {
                    numericString = numToken;
                }

                String formatted = numericString + " " + nounPhrase;

                if (!deduplicated.containsKey(formatted)) {
                    ExtractionResult result = new ExtractionResult(
                            memoryId,
                            ExtractionResult.Type.QUANTITY,
                            formatted,
                            formatted,
                            ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                            ItemVocabulary.VOCABULARY_VERSION
                    );
                    deduplicated.put(formatted, result);
                }
            }

            results.addAll(deduplicated.values());
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in QuantityExtractor for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return results;
    }
}
