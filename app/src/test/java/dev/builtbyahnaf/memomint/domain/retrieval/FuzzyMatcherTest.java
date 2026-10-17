package dev.builtbyahnaf.memomint.domain.retrieval;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FuzzyMatcher}.
 *
 * <h2>Verifications</h2>
 * <ul>
 *   <li>Damerau-Levenshtein edit operations (insertion, deletion, substitution, transposition).</li>
 *   <li>Normalized similarity calculations ($[0.0, 1.0]$).</li>
 *   <li>Short-word guards ($\ge 4$ characters) preventing false positive matches.</li>
 *   <li>Similarity threshold enforcement ($\ge 0.75$).</li>
 *   <li>Candidate selection via {@link FuzzyMatcher#findBestMatch}.</li>
 * </ul>
 */
public class FuzzyMatcherTest {

    private FuzzyMatcher matcher;

    @Before
    public void setUp() {
        matcher = new FuzzyMatcher();
    }

    @Test
    public void editDistance_calculatesFundamentalOperations() {
        // Exact match
        assertEquals(0, FuzzyMatcher.editDistance("drawer", "drawer"));

        // Single character substitution
        assertEquals(1, FuzzyMatcher.editDistance("desk", "disk"));

        // Single character insertion
        assertEquals(1, FuzzyMatcher.editDistance("drawr", "drawer"));

        // Single character deletion
        assertEquals(1, FuzzyMatcher.editDistance("drawer", "drawe"));

        // Adjacent character transposition (Damerau check)
        assertEquals(1, FuzzyMatcher.editDistance("teh", "the"));
        assertEquals(1, FuzzyMatcher.editDistance("drwaer", "drawer"));
    }

    @Test
    public void similarity_normalizedBetweenZeroAndOne() {
        assertEquals(1.0, FuzzyMatcher.similarity("keyboard", "keyboard"), 0.001);
        assertEquals(0.0, FuzzyMatcher.similarity("", "keyboard"), 0.001);
        assertEquals(0.0, FuzzyMatcher.similarity(null, "keyboard"), 0.001);

        // "keybord" vs "keyboard": length 8, distance 1 -> similarity = 1 - 1/8 = 0.875
        assertEquals(0.875, FuzzyMatcher.similarity("keybord", "keyboard"), 0.001);
    }

    @Test
    public void evaluate_identifiesTypoMatchesAboveThreshold() {
        // "keybord" -> "keyboard" (87.5% similarity)
        FuzzyMatcher.MatchResult r1 = matcher.evaluate("keybord", "keyboard");
        assertTrue("keybord should match keyboard", r1.isMatch());
        assertEquals(1, r1.getDistance());
        assertTrue(r1.getSimilarity() >= 0.75);

        // "drwer" -> "drawer" (83.3% similarity)
        FuzzyMatcher.MatchResult r2 = matcher.evaluate("drwer", "drawer");
        assertTrue("drwer should match drawer", r2.isMatch());

        // "blowr" -> "blower" (83.3% similarity)
        FuzzyMatcher.MatchResult r3 = matcher.evaluate("blowr", "blower");
        assertTrue("blowr should match blower", r3.isMatch());

        // "earfone" -> "earphone" (75.0% similarity)
        FuzzyMatcher.MatchResult r4 = matcher.evaluate("earfone", "earphone");
        assertTrue("earfone should match earphone", r4.isMatch());
    }

    @Test
    public void evaluate_rejectsDissimilarWords() {
        FuzzyMatcher.MatchResult result = matcher.evaluate("laptop", "bottle");
        assertFalse("laptop must not match bottle", result.isMatch());
        assertTrue(result.getSimilarity() < 0.50);
    }

    @Test
    public void evaluate_enforcesMinWordLengthGuard_preventingFalsePositives() {
        // "in" vs "on" has edit distance 1 and 50% similarity, but both words are < 4 chars
        FuzzyMatcher.MatchResult r1 = matcher.evaluate("in", "on");
        assertFalse("Short words (< 4 chars) must not match fuzzily", r1.isMatch());

        // "cat" vs "car" (< 4 chars)
        FuzzyMatcher.MatchResult r2 = matcher.evaluate("cat", "car");
        assertFalse("3-character words must not match fuzzily", r2.isMatch());
    }

    @Test
    public void findBestMatch_selectsHighestSimilarityCandidate() {
        List<String> candidates = Arrays.asList("desktop", "drawer", "wardrobe", "keyboard");

        FuzzyMatcher.MatchResult best = matcher.findBestMatch("keybord", candidates);
        assertNotNull(best);
        assertEquals("keyboard", best.getMatchedTerm());
        assertTrue(best.isMatch());

        FuzzyMatcher.MatchResult noMatch = matcher.findBestMatch("submarine", candidates);
        assertNull("Unrelated word should yield null", noMatch);
    }
}
