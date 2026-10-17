package dev.builtbyahnaf.memomint.domain.retrieval;

import java.util.Locale;

/**
 * Typo-tolerant string similarity utility using the Damerau-Levenshtein metric.
 *
 * <h2>Problem Solved</h2>
 * <p>Typographical errors (e.g. "keybord" for "keyboard", "drwer" for "drawer", "earfone" for "earphone")
 * frequently cause exact-match queries to fail. Fuzzy matching recovers intended words despite minor
 * keystroke errors.</p>
 *
 * <h2>Algorithm & Invariants</h2>
 * <p>Uses the optimal string alignment / Damerau-Levenshtein distance algorithm which accounts for
 * four edit operations:</p>
 * <ol>
 *   <li><b>Insertion:</b> Adding a missing character.</li>
 *   <li><b>Deletion:</b> Omitting an extraneous character.</li>
 *   <li><b>Substitution:</b> Replacing one character with another.</li>
 *   <li><b>Transposition:</b> Swapping two adjacent characters (e.g., "teh" ↔ "the").</li>
 * </ol>
 *
 * <p>The similarity is normalized into $[0.0, 1.0]$ via:</p>
 * <pre>
 *   similarity(s1, s2) = 1.0 - (distance(s1, s2) / max(len(s1), len(s2)))
 * </pre>
 *
 * <h2>Guards Against False Positives (Phase 2 Plan §3.4)</h2>
 * <ul>
 *   <li><b>Minimum Word Length:</b> Enforces {@link #DEFAULT_MIN_WORD_LENGTH} ($\ge 4$ characters).
 *       Short words like "in" vs "on" or "cat" vs "car" must not match fuzzily to avoid semantic corruption.</li>
 *   <li><b>Similarity Threshold:</b> Candidates must achieve $\ge 0.75$ normalized similarity.</li>
 *   <li><b>Not Semantic Understanding:</b> In accordance with Section 12.2 and Section 25.4, fuzzy matching
 *       is strictly an orthographic correction tool, not a semantic reasoning engine.</li>
 * </ul>
 */
public final class FuzzyMatcher {

    /** Default similarity threshold: candidates must achieve at least 75% similarity. */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.75;

    /** Minimum word length to prevent false positive matches on short words (e.g. "at" vs "it"). */
    public static final int DEFAULT_MIN_WORD_LENGTH = 4;

    private final double similarityThreshold;
    private final int minWordLength;

    public FuzzyMatcher() {
        this(DEFAULT_SIMILARITY_THRESHOLD, DEFAULT_MIN_WORD_LENGTH);
    }

    public FuzzyMatcher(double similarityThreshold, int minWordLength) {
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0.0 and 1.0");
        }
        if (minWordLength < 1) {
            throw new IllegalArgumentException("minWordLength must be at least 1");
        }
        this.similarityThreshold = similarityThreshold;
        this.minWordLength = minWordLength;
    }

    /**
     * Encapsulates the result of a fuzzy match evaluation.
     */
    public static final class MatchResult {
        private final String queryTerm;
        private final String matchedTerm;
        private final int distance;
        private final double similarity;
        private final boolean isMatch;

        public MatchResult(String queryTerm, String matchedTerm, int distance, double similarity, boolean isMatch) {
            this.queryTerm = queryTerm;
            this.matchedTerm = matchedTerm;
            this.distance = distance;
            this.similarity = similarity;
            this.isMatch = isMatch;
        }

        public String getQueryTerm() {
            return queryTerm;
        }

        public String getMatchedTerm() {
            return matchedTerm;
        }

        public int getDistance() {
            return distance;
        }

        public double getSimilarity() {
            return similarity;
        }

        public boolean isMatch() {
            return isMatch;
        }

        @Override
        public String toString() {
            return "MatchResult{" +
                    "query='" + queryTerm + '\'' +
                    ", matched='" + matchedTerm + '\'' +
                    ", similarity=" + String.format(Locale.ROOT, "%.2f", similarity) +
                    ", match=" + isMatch +
                    '}';
        }
    }

    /**
     * Computes the Damerau-Levenshtein edit distance between two strings.
     *
     * @param s1 first string
     * @param s2 second string
     * @return non-negative integer representing the minimum number of edits required
     */
    public static int editDistance(String s1, String s2) {
        if (s1 == null && s2 == null) return 0;
        if (s1 == null) return s2.length();
        if (s2 == null) return s1.length();

        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        // DP table with border for 0-length prefixes
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            d[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;

                // Deletion, insertion, substitution
                int deletion = d[i - 1][j] + 1;
                int insertion = d[i][j - 1] + 1;
                int substitution = d[i - 1][j - 1] + cost;

                int min = Math.min(Math.min(deletion, insertion), substitution);

                // Adjacent transposition (Damerau addition)
                if (i > 1 && j > 1 && c1 == s2.charAt(j - 2) && s1.charAt(i - 2) == c2) {
                    min = Math.min(min, d[i - 2][j - 2] + 1);
                }

                d[i][j] = min;
            }
        }

        return d[len1][len2];
    }

    /**
     * Calculates the normalized similarity score $[0.0, 1.0]$ between two strings.
     *
     * @param s1 first string
     * @param s2 second string
     * @return similarity score between 0.0 (completely dissimilar) and 1.0 (exact match)
     */
    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        String norm1 = s1.toLowerCase(Locale.ROOT).trim();
        String norm2 = s2.toLowerCase(Locale.ROOT).trim();

        if (norm1.equals(norm2)) return 1.0;

        int maxLen = Math.max(norm1.length(), norm2.length());
        if (maxLen == 0) return 1.0;

        int dist = editDistance(norm1, norm2);
        double sim = 1.0 - ((double) dist / (double) maxLen);
        return Math.max(0.0, Math.min(1.0, sim));
    }

    /**
     * Evaluates whether two terms match within the configured similarity and word-length thresholds.
     *
     * @param queryTerm  user query term
     * @param targetTerm stored memory or candidate term
     * @return structured {@link MatchResult}
     */
    public MatchResult evaluate(String queryTerm, String targetTerm) {
        if (queryTerm == null || targetTerm == null) {
            return new MatchResult(queryTerm, targetTerm, Integer.MAX_VALUE, 0.0, false);
        }

        String q = queryTerm.toLowerCase(Locale.ROOT).trim();
        String t = targetTerm.toLowerCase(Locale.ROOT).trim();

        // Exact match short-circuit
        if (q.equals(t)) {
            return new MatchResult(queryTerm, targetTerm, 0, 1.0, true);
        }

        // Word length guard: ignore words shorter than minimum threshold
        if (q.length() < minWordLength || t.length() < minWordLength) {
            return new MatchResult(queryTerm, targetTerm, editDistance(q, t), 0.0, false);
        }

        int distance = editDistance(q, t);
        int maxLen = Math.max(q.length(), t.length());
        double sim = 1.0 - ((double) distance / (double) maxLen);

        boolean isMatch = sim >= similarityThreshold;
        return new MatchResult(queryTerm, targetTerm, distance, sim, isMatch);
    }

    /**
     * Searches a collection of candidate words to find the best fuzzy match for a query term.
     *
     * @param queryTerm  query term
     * @param candidates iterable of target candidate terms
     * @return best {@link MatchResult} meeting the threshold, or null if no candidate qualifies
     */
    public MatchResult findBestMatch(String queryTerm, Iterable<String> candidates) {
        if (queryTerm == null || candidates == null) return null;

        MatchResult best = null;
        for (String candidate : candidates) {
            MatchResult res = evaluate(queryTerm, candidate);
            if (res.isMatch()) {
                if (best == null || res.getSimilarity() > best.getSimilarity()) {
                    best = res;
                }
            }
        }
        return best;
    }
}
