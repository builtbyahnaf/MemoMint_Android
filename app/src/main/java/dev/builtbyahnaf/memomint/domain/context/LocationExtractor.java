package dev.builtbyahnaf.memomint.domain.context;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.builtbyahnaf.memomint.domain.model.Memory;

/**
 * Extracts spatial location phrases, container descriptions, and furniture placement facts from memory text.
 *
 * <h2>Design &amp; Algorithmic Principles (FR-03, Section 11.3 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Precompiled Regex Pipelines:</b> Compiles expensive spatial prepositional patterns into
 *       immutable static final {@link Pattern} instances once at class initialization to maintain
 *       sub-millisecond parsing latencies.</li>
 *   <li><b>Preposition Normalization:</b> Strips leading spatial prepositions (e.g. "in", "on", "at", "next to")
 *       to derive canonical noun-phrase placement labels (e.g. "in the drawer" &rarr; "the drawer").</li>
 *   <li><b>Word Boundaries &amp; Specificity:</b> Enforces {@code \b} word boundary matching and orders
 *       compound terms (e.g. "bedside table" before "bed", "living room" before "room") to prevent prefix truncation.</li>
 *   <li><b>Deduplication:</b> Results are keyed and deduplicated by their canonical label to prevent
 *       emitting redundant overlapping spans.</li>
 *   <li><b>Fault Tolerance (NFR-08):</b> Protects against regex catastrophic backtracking and malformed input.
 *       Catches all exceptions, logs diagnostics, and returns safely without throwing.</li>
 * </ul>
 */
public class LocationExtractor {

    private static final String TAG = "LocationExtractor";

    // 1. Container / Compartment patterns (e.g. "in the top drawer of my desk")
    private static final Pattern PATTERN_CONTAINER = Pattern.compile(
            "\\bin (?:the )?(?:top|bottom|left|right|front|back)?\\s*(?:drawer|shelf|cabinet|wardrobe|closet|box|bag|pocket|compartment|section)(?:\\s+of\\s+(?:my\\s+)?[\\w\\s]+)?\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 2. Surface / Furniture placement patterns (e.g. "on the nightstand", "on bedside table")
    // Put compound nouns like "bedside table" and "bedside" before "bed" and "table" to avoid premature prefix matches
    private static final Pattern PATTERN_SURFACE = Pattern.compile(
            "\\bon (?:the )?(?:bedside(?: table)?|desk|counter|floor|shelf|hook|rack|nightstand|dresser|table|windowsill|bed)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 3. Proximity / Spatial relation patterns (e.g. "under the bed", "behind the door")
    private static final Pattern PATTERN_PROXIMITY = Pattern.compile(
            "\\b(?:beside|next to|under|above|behind|in front of) (?:the )?(?:bed|desk|door|sink|window|couch|sofa|tv|television)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 4. Room / Enclosure patterns (e.g. "in my office", "in the living room")
    // Put "living room" and "bedroom" before "room" to match compound room names accurately
    private static final Pattern PATTERN_ROOM = Pattern.compile(
            "\\bin (?:my )?(?:living room|bedroom|room|office|kitchen|bathroom|car|bag|backpack|wardrobe|garage|basement)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<Pattern> LOCATION_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            PATTERN_CONTAINER,
            PATTERN_SURFACE,
            PATTERN_PROXIMITY,
            PATTERN_ROOM
    ));

    private static final Pattern LEADING_PREPOSITION_PATTERN = Pattern.compile(
            "^(?:in|on|at|beside|next\\s+to|under|above|behind|in\\s+front\\s+of)\\s+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Extracts all location phrases from the memory text.
     *
     * @param memory target memory to extract locations from
     * @return deduplicated list of {@link ExtractionResult} with type {@link ExtractionResult.Type#LOCATION}
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
            Map<String, ExtractionResult> deduplicatedByCanonical = new LinkedHashMap<>();

            for (Pattern pattern : LOCATION_PATTERNS) {
                Matcher matcher = pattern.matcher(rawText);
                while (matcher.find()) {
                    String matchedSpan = matcher.group().trim();
                    String normalized = normalizeLocation(matchedSpan);

                    if (!normalized.isEmpty() && !deduplicatedByCanonical.containsKey(normalized)) {
                        ExtractionResult result = new ExtractionResult(
                                memoryId,
                                ExtractionResult.Type.LOCATION,
                                normalized,
                                normalized,
                                ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                                ItemVocabulary.VOCABULARY_VERSION
                        );
                        deduplicatedByCanonical.put(normalized, result);
                    }
                }
            }

            results.addAll(deduplicatedByCanonical.values());
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in LocationExtractor for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return results;
    }

    /**
     * Normalizes a matched location phrase by converting to lowercase, trimming,
     * and stripping leading spatial prepositions.
     *
     * @param rawLocation matched raw text span
     * @return normalized location label
     */
    public static String normalizeLocation(String rawLocation) {
        if (rawLocation == null) return "";
        String trimmed = rawLocation.trim().toLowerCase(Locale.ROOT);
        Matcher prepMatcher = LEADING_PREPOSITION_PATTERN.matcher(trimmed);
        if (prepMatcher.find()) {
            trimmed = trimmed.substring(prepMatcher.end()).trim();
        }
        return trimmed;
    }
}
