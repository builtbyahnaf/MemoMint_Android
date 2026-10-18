package dev.builtbyahnaf.memomint.domain.retrieval;

import java.util.Locale;

/**
 * Hybrid scoring and signal combination model for search candidate ranking.
 *
 * <h2>Problem Solved</h2>
 * <p>Candidate retrieval produces disparate signals with differing scales:
 * unbounded BM25 scores from SQLite full-text search, term overlaps from synonym expansions,
 * categorical metadata matches, graph-hop relationship connections, and character-level edit distance ratios from fuzzy matching.
 * Directly summing these signals leads to one dominating others. This scorer normalizes each component
 * into a uniform $[0.0, 1.0]$ range before applying linear combination weights.</p>
 *
 * <h2>Scoring Formula (Section 12.3 &amp; Phase 3 Plan)</h2>
 * <pre>
 *   S_final = 0.45 * S_text + 0.25 * S_synonym + 0.15 * S_meta + 0.10 * S_relation + 0.05 * S_fuzzy
 * </pre>
 *
 * <h2>Normalization Methods</h2>
 * <ul>
 *   <li><b>BM25 Normalization:</b> SQLite FTS5 returns negative BM25 scores (more negative = stronger match).
 *       Taking the absolute value $|BM25|$ and applying monotonic arctan compression:
 *       <pre>
 *         S_text = (2 / π) * arctan(|raw_bm25|)
 *       </pre>
 *       This maps $[0, \infty)$ strictly into $[0.0, 1.0)$ while preserving rank ordering without saturation.</li>
 *   <li><b>Synonym Score:</b> Ratio of query content tokens that successfully matched via synonym expansion
 *       weighted by equivalence confidence.</li>
 *   <li><b>Metadata Score:</b> Alignment between query intent (e.g., location lookup) and memory attributes
 *       (e.g., {@code MemoryType.PHYSICAL_ITEM} or {@code LOCATION}), plus title matches.</li>
 *   <li><b>Relation Score:</b> Inferred depth-1 knowledge graph connection score ($S_{\text{relation}}$) attenuated by edge confidence.</li>
 *   <li><b>Fuzzy Score:</b> Highest Damerau-Levenshtein similarity achieved for non-exact terms ($\ge 0.75$).</li>
 * </ul>
 */
public final class RelevanceScorer {

    public static final double DEFAULT_WEIGHT_TEXT = 0.45;
    public static final double DEFAULT_WEIGHT_SYNONYM = 0.25;
    public static final double DEFAULT_WEIGHT_META = 0.15;
    public static final double DEFAULT_WEIGHT_RELATION = 0.10;
    public static final double DEFAULT_WEIGHT_FUZZY = 0.05;

    private final double weightText;
    private final double weightSynonym;
    private final double weightMeta;
    private final double weightFuzzy;
    private final double weightRelation;

    /**
     * Constructs a scorer with standard baseline weights for Phase 3.
     */
    public RelevanceScorer() {
        this(DEFAULT_WEIGHT_TEXT, DEFAULT_WEIGHT_SYNONYM, DEFAULT_WEIGHT_META, DEFAULT_WEIGHT_FUZZY, DEFAULT_WEIGHT_RELATION);
    }

    /**
     * Backward-compatible constructor for 4-component scoring without relation weight.
     * Weights must sum to approximately 1.0.
     */
    public RelevanceScorer(double weightText, double weightSynonym, double weightMeta, double weightFuzzy) {
        this(weightText, weightSynonym, weightMeta, weightFuzzy, 0.0);
    }

    /**
     * Constructs a scorer with custom weights for experimental tuning across all 5 signals.
     * Weights must sum to approximately 1.0.
     */
    public RelevanceScorer(
            double weightText,
            double weightSynonym,
            double weightMeta,
            double weightFuzzy,
            double weightRelation
    ) {
        double sum = weightText + weightSynonym + weightMeta + weightFuzzy + weightRelation;
        if (Math.abs(sum - 1.0) > 0.01) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT, "Scoring weights must sum to 1.0 (got %.3f)", sum));
        }
        this.weightText = weightText;
        this.weightSynonym = weightSynonym;
        this.weightMeta = weightMeta;
        this.weightFuzzy = weightFuzzy;
        this.weightRelation = weightRelation;
    }

    public double getWeightText() {
        return weightText;
    }

    public double getWeightSynonym() {
        return weightSynonym;
    }

    public double getWeightMeta() {
        return weightMeta;
    }

    public double getWeightFuzzy() {
        return weightFuzzy;
    }

    public double getWeightRelation() {
        return weightRelation;
    }

    /**
     * Backward-compatible scoring method combining four signals, setting relation score to 0.0.
     *
     * @param sText    normalized text match / BM25 score
     * @param sSynonym normalized synonym match score
     * @param sMeta    normalized metadata match score
     * @param sFuzzy   normalized fuzzy match score
     * @return composite score bounded between 0.0 and 1.0
     */
    public double computeFinalScore(double sText, double sSynonym, double sMeta, double sFuzzy) {
        return computeFinalScore(sText, sSynonym, sMeta, sFuzzy, 0.0);
    }

    /**
     * Combines five normalized signal scores $[0.0, 1.0]$ into a final composite relevance score $[0.0, 1.0]$.
     *
     * @param sText     normalized text match / BM25 score
     * @param sSynonym  normalized synonym match score
     * @param sMeta     normalized metadata match score
     * @param sFuzzy    normalized fuzzy match score
     * @param sRelation normalized knowledge graph relation score
     * @return composite score bounded between 0.0 and 1.0
     */
    public double computeFinalScore(double sText, double sSynonym, double sMeta, double sFuzzy, double sRelation) {
        double text = clamp(sText);
        double syn = clamp(sSynonym);
        double meta = clamp(sMeta);
        double fuzzy = clamp(sFuzzy);
        double rel = clamp(sRelation);

        double composite = (weightText * text)
                + (weightSynonym * syn)
                + (weightMeta * meta)
                + (weightFuzzy * fuzzy)
                + (weightRelation * rel);
        return clamp(composite);
    }

    /**
     * Normalizes a raw SQLite FTS5 BM25 score into $[0.0, 1.0]$.
     *
     * <p>SQLite FTS5's {@code bm25()} returns negative values (e.g. -1.5, -4.2) where more negative
     * indicates higher relevance. We take the absolute value $|bm25|$ and pass it through
     * $(2/\pi) \arctan(|bm25|)$.</p>
     *
     * @param rawBm25 raw score from SQLite FTS5 or Lucene BM25
     * @return normalized value in $[0.0, 1.0)$
     */
    public static double normalizeBm25(double rawBm25) {
        double magnitude = Math.abs(rawBm25);
        if (magnitude <= 0.00001) {
            return 0.0;
        }
        double normalized = (2.0 / Math.PI) * Math.atan(magnitude);
        return clamp(normalized);
    }

    /**
     * Computes term overlap ratio as a normalized score $[0.0, 1.0]$.
     *
     * @param matchedCount number of unique query terms matched
     * @param totalTokens  total count of substantive query content tokens
     * @return ratio clamped between 0.0 and 1.0
     */
    public static double computeTermOverlapRatio(int matchedCount, int totalTokens) {
        if (totalTokens <= 0 || matchedCount <= 0) return 0.0;
        return clamp((double) matchedCount / (double) totalTokens);
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }
}
