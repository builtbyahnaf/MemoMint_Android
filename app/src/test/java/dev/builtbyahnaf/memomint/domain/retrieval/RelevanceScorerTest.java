package dev.builtbyahnaf.memomint.domain.retrieval;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link RelevanceScorer}.
 *
 * <h2>Verifications</h2>
 * <ul>
 *   <li>Linear combination with Phase 3 default weights (0.45, 0.25, 0.15, 0.05, 0.10).</li>
 *   <li>Backward-compatible 4-component scoring with relation score defaulted to 0.0.</li>
 *   <li>Boundary clamping strictly within $[0.0, 1.0]$.</li>
 *   <li>Arctan normalization for raw SQLite BM25 scores.</li>
 *   <li>Term overlap ratio calculations.</li>
 *   <li>Constructor weight validation (weights must sum to 1.0).</li>
 * </ul>
 */
public class RelevanceScorerTest {

    private RelevanceScorer scorer;

    @Before
    public void setUp() {
        scorer = new RelevanceScorer();
    }

    @Test
    public void computeFinalScore_combinesSignalsAccordingToFormula() {
        // Phase 3 5-component scoring formula:
        // S_text = 0.80, S_syn = 0.90, S_meta = 0.70, S_fuzzy = 0.0, S_relation = 0.85
        // Score = 0.45(0.80) + 0.25(0.90) + 0.15(0.70) + 0.05(0.0) + 0.10(0.85)
        //       = 0.360 + 0.225 + 0.105 + 0.0 + 0.085 = 0.775
        double score5 = scorer.computeFinalScore(0.80, 0.90, 0.70, 0.0, 0.85);
        assertEquals(0.775, score5, 0.001);

        // Backward-compat 4-argument overload (sets sRelation = 0.0):
        // Score = 0.45(0.80) + 0.25(0.90) + 0.15(0.70) + 0.05(0.0) + 0.10(0.0)
        //       = 0.360 + 0.225 + 0.105 = 0.690
        double score4 = scorer.computeFinalScore(0.80, 0.90, 0.70, 0.0);
        assertEquals(0.690, score4, 0.001);
    }

    @Test
    public void computeFinalScore_clampsValuesBetweenZeroAndOne() {
        // Upper overflow test with all 5 signals
        double overflow = scorer.computeFinalScore(1.5, 2.0, 1.2, 1.1, 1.4);
        assertEquals(1.0, overflow, 0.001);

        // Negative underflow test with all 5 signals
        double underflow = scorer.computeFinalScore(-0.5, -1.0, 0.0, 0.0, -0.2);
        assertEquals(0.0, underflow, 0.001);
    }

    @Test
    public void normalizeBm25_compressesUnboundedScoresMonotonically() {
        // Score of 0 maps to 0.0
        assertEquals(0.0, RelevanceScorer.normalizeBm25(0.0), 0.001);

        // Negative BM25 scores (as returned by SQLite FTS5) map smoothly
        double norm1 = RelevanceScorer.normalizeBm25(-1.0);
        double norm2 = RelevanceScorer.normalizeBm25(-2.5);
        double norm3 = RelevanceScorer.normalizeBm25(-10.0);

        assertTrue("Normalized score must be > 0", norm1 > 0.0);
        assertTrue("Higher magnitude BM25 must produce higher normalized score", norm2 > norm1);
        assertTrue("Higher magnitude BM25 must produce higher normalized score", norm3 > norm2);
        assertTrue("Score must remain bounded < 1.0", norm3 < 1.0);
    }

    @Test
    public void computeTermOverlapRatio_computesRatiosAccurately() {
        assertEquals(1.0, RelevanceScorer.computeTermOverlapRatio(3, 3), 0.001);
        assertEquals(0.5, RelevanceScorer.computeTermOverlapRatio(1, 2), 0.001);
        assertEquals(0.0, RelevanceScorer.computeTermOverlapRatio(0, 5), 0.001);
        assertEquals(0.0, RelevanceScorer.computeTermOverlapRatio(2, 0), 0.001);
    }

    @Test
    public void customWeights_validateSumConstraint() {
        // Valid custom weights summing to 1.0 across 5 components
        RelevanceScorer custom = new RelevanceScorer(0.40, 0.30, 0.10, 0.10, 0.10);
        assertEquals(0.40, custom.getWeightText(), 0.001);
        assertEquals(0.10, custom.getWeightRelation(), 0.001);

        // Invalid weights not summing to 1.0 must throw IllegalArgumentException
        try {
            new RelevanceScorer(0.50, 0.50, 0.50, 0.50, 0.50);
            fail("Expected IllegalArgumentException when weights sum to 2.5");
        } catch (IllegalArgumentException expected) {
            // Success
        }
    }
}
