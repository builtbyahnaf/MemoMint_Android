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
 * Extracts relative and absolute temporal mentions, dates, and recurrence schedules from memory text.
 *
 * <h2>Design &amp; Algorithmic Principles (Section 11.5 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Temporal Classification:</b> Detects both relative references (e.g. "tomorrow", "next Monday",
 *       "in 3 days") and absolute calendar dates (e.g. "October 18", "10/18/2026").</li>
 *   <li><b>Scope in Phase 3:</b> Temporal mentions extracted here are stored as auxiliary context records
 *       for display and calendar association. In accordance with Phase 3 design, they do not alter
 *       relevance scoring components.</li>
 *   <li><b>Canonical Normalization:</b> Emits raw verbatim match spans as {@code content} while storing
 *       lowercased, whitespace-normalized strings as {@code canonicalLabel}.</li>
 *   <li><b>Fail-Safe (NFR-08):</b> Protects callers by capturing all runtime exceptions and logging diagnostics.</li>
 * </ul>
 */
public class DateTimeExtractor {

    private static final String TAG = "DateTimeExtractor";

    private static final List<Pattern> TEMPORAL_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            // 1. Relative standalone words
            Pattern.compile("\\b(?:today|tomorrow|yesterday)\\b", Pattern.CASE_INSENSITIVE),

            // 2. Relative future qualifiers (e.g. "next Monday", "next week")
            Pattern.compile("\\bnext\\s+(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|week|month|year)\\b", Pattern.CASE_INSENSITIVE),

            // 3. Offset spans (e.g. "in 3 days", "in 2 weeks")
            Pattern.compile("\\bin\\s+\\d+\\s+(?:day|week|month)s?\\b", Pattern.CASE_INSENSITIVE),

            // 4. Recurrence indicators (e.g. "every day", "every month")
            Pattern.compile("\\bevery\\s+(?:day|week|month)\\b", Pattern.CASE_INSENSITIVE),

            // 5. Absolute calendar dates (e.g. "October 18", "Oct 18th 2026")
            Pattern.compile("\\b(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:\\s+\\d{4})?\\b", Pattern.CASE_INSENSITIVE),

            // 6. Formatted numeric dates (e.g. "10/18/2026", "18/10/26")
            Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b", Pattern.CASE_INSENSITIVE)
    ));

    /**
     * Extracts all temporal mentions from the memory text.
     *
     * @param memory the target memory to inspect
     * @return deduplicated list of {@link ExtractionResult} with type {@link ExtractionResult.Type#TEMPORAL_MENTION}
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

            for (Pattern pattern : TEMPORAL_PATTERNS) {
                Matcher matcher = pattern.matcher(rawText);
                while (matcher.find()) {
                    String rawSpan = matcher.group().trim();
                    String canonical = rawSpan.toLowerCase(Locale.ROOT);

                    if (!deduplicated.containsKey(canonical)) {
                        ExtractionResult result = new ExtractionResult(
                                memoryId,
                                ExtractionResult.Type.TEMPORAL_MENTION,
                                rawSpan,
                                canonical,
                                ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                                ItemVocabulary.VOCABULARY_VERSION
                        );
                        deduplicated.put(canonical, result);
                    }
                }
            }

            results.addAll(deduplicated.values());
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected error in DateTimeExtractor for memory: " + (memory != null ? memory.getId() : "null"), t);
        }

        return results;
    }
}
