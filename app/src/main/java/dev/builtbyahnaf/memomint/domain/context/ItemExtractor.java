package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Extracts physical item entity nouns and equipment mentions from memory text.
 *
 * <h2>Design &amp; Algorithmic Principles (FR-03, Section 11.2 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Type Specificity:</b> Only executes for {@link MemoryType#PHYSICAL_ITEM} memories.
 *       Other categories (e.g. general {@link MemoryType#NOTE}) are skipped to prevent false positive
 *       physical entity assertions on abstract thoughts.</li>
 *   <li><b>Word Boundary Matching:</b> Ensures substrings within words (e.g., "pen" in "happen") do not
 *       trigger erroneous entity extractions by enforcing regex {@code \b} word boundary checks.</li>
 *   <li><b>Longest-Match Preference:</b> Variants for a canonical item are inspected in descending order of
 *       length (e.g., "usb-c" is evaluated before "usb") to match the most specific descriptor.</li>
 *   <li><b>Fail-Safe Invariant (NFR-08):</b> Never propagates runtime exceptions to callers.
 *       Any unexpected matching anomaly is caught, logged, and partial or empty results are returned.</li>
 * </ul>
 */
public class ItemExtractor {

    private static final String TAG = "ItemExtractor";

    /** Precompiled regex patterns for each variant to maximize execution speed (< 1ms). */
    private static final Map<String, Pattern> VARIANT_PATTERNS = new HashMap<>();

    static {
        for (String canonical : ItemVocabulary.getAllCanonicalItems()) {
            for (String variant : ItemVocabulary.getVariants(canonical)) {
                String regex = "\\b" + Pattern.quote(variant) + "\\b";
                VARIANT_PATTERNS.put(variant, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            }
        }
    }

    /**
     * Extracts physical item entities from the provided memory.
     *
     * @param memory the memory to inspect; if null, not PHYSICAL_ITEM, or without text, returns empty list
     * @return list of {@link ExtractionResult} instances representing detected physical items
     */
    public List<ExtractionResult> extract(Memory memory) {
        if (memory == null) {
            return Collections.emptyList();
        }

        // Invariant: only PHYSICAL_ITEM memories are evaluated for physical item entity extraction
        if (memory.getType() != MemoryType.PHYSICAL_ITEM) {
            return Collections.emptyList();
        }

        String rawText = memory.getOriginalText();
        if (rawText == null || rawText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ExtractionResult> results = new ArrayList<>();
        try {
            String lowerText = rawText.toLowerCase(Locale.ROOT);
            String memoryId = memory.getId();

            // Track matched canonicals to avoid duplicate emissions per memory
            Map<String, ExtractionResult> matchedByCanonical = new LinkedHashMap<>();

            for (String canonical : ItemVocabulary.getAllCanonicalItems()) {
                Set<String> variants = ItemVocabulary.getVariants(canonical);

                // Sort variants by length descending so longer compound phrases match first
                List<String> sortedVariants = new ArrayList<>(variants);
                sortedVariants.sort(Comparator.comparingInt(String::length).reversed());

                for (String variant : sortedVariants) {
                    Pattern pattern = VARIANT_PATTERNS.get(variant);
                    if (pattern == null) {
                        pattern = Pattern.compile("\\b" + Pattern.quote(variant) + "\\b", Pattern.CASE_INSENSITIVE);
                    }

                    Matcher matcher = pattern.matcher(lowerText);
                    if (matcher.find()) {
                        // Preserve original casing of the matched text span from raw text if possible
                        int start = matcher.start();
                        int end = matcher.end();
                        String matchedSpan = rawText.substring(start, end).trim();

                        ExtractionResult result = new ExtractionResult(
                                memoryId,
                                ExtractionResult.Type.ITEM_NAME,
                                matchedSpan,
                                canonical,
                                ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                                ItemVocabulary.VOCABULARY_VERSION
                        );

                        matchedByCanonical.put(canonical, result);
                        // Once the longest matching variant for this canonical is found, proceed to next canonical
                        break;
                    }
                }
            }

            results.addAll(matchedByCanonical.values());
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in ItemExtractor for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return results;
    }
}
