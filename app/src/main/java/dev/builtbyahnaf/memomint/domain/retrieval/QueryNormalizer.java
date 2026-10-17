package dev.builtbyahnaf.memomint.domain.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes and tokenizes raw search queries and text content into a standardized canonical form.
 *
 * <h2>Problem Solved</h2>
 * <p>In human note-taking and search, text exhibits vast typographical variability: mixed casing,
 * diacritics/accents, irregular punctuation, varied spacing, and ubiquitous stopword filler words
 * (e.g., "Where did I put my..."). Without normalization, exact-match and full-text engines fail
 * on trivial discrepancies.</p>
 *
 * <h2>Algorithm & Pipeline</h2>
 * <ol>
 *   <li><b>Trimming:</b> Strip leading and trailing whitespace.</li>
 *   <li><b>Unicode Normalization:</b> Apply {@link Normalizer.Form#NFKC} compatibility decomposition
 *       followed by canonical composition. This collapses accented characters, ligatures, and full-width forms.</li>
 *   <li><b>Case Folding:</b> Convert text to lower case using {@link Locale#ROOT} to prevent locale-specific
 *       casing anomalies (such as Turkish dotted/dotless 'i').</li>
 *   <li><b>Punctuation Stripping with Hyphen Preservation:</b> Retain alphanumeric characters and intra-word
 *       hyphens (e.g., "USB-C" becomes "usb-c", "wi-fi" stays "wi-fi"), while stripping surrounding punctuation,
 *       quotes, and symbols.</li>
 *   <li><b>Tokenization:</b> Split cleaned text into discrete whitespace-delimited tokens.</li>
 *   <li><b>Stopword Separation:</b> Tag and separate common functional stopwords (e.g. "where", "did", "i", "put", "the")
 *       from substantive content tokens (e.g. "spare", "usb-c", "cable").</li>
 *   <li><b>Intent Tagging:</b> Inspect question starters to classify query intent (e.g., "where" indicates
 *       spatial location lookup; "when" indicates temporal lookup).</li>
 * </ol>
 *
 * <h2>Assumptions & Limitations</h2>
 * <ul>
 *   <li>Intra-word hyphens connecting words of 1+ alphanumeric characters are preserved; isolated dashes are stripped.</li>
 *   <li>Does not perform morphological stemming or lemmatization (e.g., "cables" is not reduced to "cable" here;
 *       stemming variations are handled cooperatively by SQLite FTS tokenizers and the synonym engine).</li>
 * </ul>
 */
public final class QueryNormalizer {

    /**
     * Common English stopwords frequently present in natural-language retrieval queries.
     * Filtered to content tokens so that high-frequency functional words do not artificially skew BM25/scoring.
     */
    public static final Set<String> DEFAULT_STOPWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "all", "am", "an", "and", "any", "are",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both",
            "but", "by", "can", "did", "do", "does", "doing", "down", "during", "each", "few",
            "for", "from", "further", "had", "has", "have", "having", "he", "her", "here", "hers",
            "herself", "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it", "its",
            "itself", "just", "me", "more", "most", "my", "myself", "no", "nor", "not", "now",
            "of", "off", "on", "once", "only", "or", "other", "our", "ours", "ourselves", "out",
            "over", "own", "put", "same", "she", "should", "so", "some", "such", "than", "that",
            "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they",
            "this", "those", "through", "to", "too", "under", "until", "up", "very", "was",
            "we", "were", "what", "when", "where", "which", "while", "who", "whom", "why",
            "will", "with", "you", "your", "yours", "yourself", "yourselves"
    )));

    /**
     * Pattern matching valid tokens: alphanumeric words potentially containing internal hyphens
     * (e.g. "usb-c", "wi-fi", "type-c", "e-mail").
     */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*");

    private final Set<String> stopwords;

    /**
     * Constructs a normalizer using the default stopword vocabulary.
     */
    public QueryNormalizer() {
        this(DEFAULT_STOPWORDS);
    }

    /**
     * Constructs a normalizer using a custom stopword set.
     *
     * @param stopwords custom set of stopwords to tag; must not be null
     */
    public QueryNormalizer(Set<String> stopwords) {
        if (stopwords == null) {
            throw new IllegalArgumentException("stopwords cannot be null");
        }
        this.stopwords = new HashSet<>(stopwords);
    }

    /**
     * Query intent classification based on natural language cues.
     */
    public enum QueryIntent {
        /** Query inquires about physical placement or spatial location (e.g., "Where is...", "find..."). */
        LOCATION_LOOKUP,
        /** Query inquires about timestamps, dates, or reminder times (e.g., "When did...", "when is..."). */
        TEMPORAL_LOOKUP,
        /** General informational search or keyword query. */
        GENERAL_SEARCH
    }

    /**
     * Structured result containing normalized text, tokens, content tokens, and detected query intent.
     */
    public static final class NormalizedQuery {
        private final String rawQuery;
        private final String normalizedText;
        private final List<String> allTokens;
        private final List<String> contentTokens;
        private final List<String> stopwordTokens;
        private final QueryIntent intent;

        public NormalizedQuery(
                String rawQuery,
                String normalizedText,
                List<String> allTokens,
                List<String> contentTokens,
                List<String> stopwordTokens,
                QueryIntent intent
        ) {
            this.rawQuery = rawQuery;
            this.normalizedText = normalizedText;
            this.allTokens = Collections.unmodifiableList(new ArrayList<>(allTokens));
            this.contentTokens = Collections.unmodifiableList(new ArrayList<>(contentTokens));
            this.stopwordTokens = Collections.unmodifiableList(new ArrayList<>(stopwordTokens));
            this.intent = intent;
        }

        public String getRawQuery() {
            return rawQuery;
        }

        public String getNormalizedText() {
            return normalizedText;
        }

        public List<String> getAllTokens() {
            return allTokens;
        }

        /** Non-stopword tokens representing semantic keywords. */
        public List<String> getContentTokens() {
            return contentTokens;
        }

        public List<String> getStopwordTokens() {
            return stopwordTokens;
        }

        public QueryIntent getIntent() {
            return intent;
        }

        @Override
        public String toString() {
            return "NormalizedQuery{" +
                    "normalizedText='" + normalizedText + '\'' +
                    ", contentTokens=" + contentTokens +
                    ", intent=" + intent +
                    '}';
        }
    }

    /**
     * Normalizes a raw input query string into structured canonical components.
     *
     * @param rawQuery raw user input query; null or blank produces an empty NormalizedQuery
     * @return a {@link NormalizedQuery} instance
     */
    public NormalizedQuery normalize(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return new NormalizedQuery("", "", Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), QueryIntent.GENERAL_SEARCH);
        }

        // 1. Unicode NFKC normalization and lowercase conversion
        String nfkc = Normalizer.normalize(rawQuery.trim(), Normalizer.Form.NFKC);
        String lower = nfkc.toLowerCase(Locale.ROOT);

        // 2. Token extraction preserving intra-word hyphens
        List<String> allTokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(lower);
        while (matcher.find()) {
            allTokens.add(matcher.group());
        }

        // 3. Separate content tokens from stopwords
        List<String> contentTokens = new ArrayList<>();
        List<String> stopwordTokens = new ArrayList<>();
        for (String token : allTokens) {
            if (stopwords.contains(token)) {
                stopwordTokens.add(token);
            } else {
                contentTokens.add(token);
            }
        }

        // If all tokens were stopwords (e.g. query was just "where is it"), preserve all tokens as content
        // to avoid returning a completely empty query.
        if (contentTokens.isEmpty() && !allTokens.isEmpty()) {
            contentTokens.addAll(allTokens);
        }

        // 4. Determine intent
        QueryIntent intent = detectIntent(allTokens, lower);

        // 5. Build clean normalized text representation
        String normalizedText = String.join(" ", allTokens);

        return new NormalizedQuery(rawQuery, normalizedText, allTokens, contentTokens, stopwordTokens, intent);
    }

    /**
     * Determines whether a token is recognized as a stopword.
     *
     * @param token lowercase token string
     * @return true if the token is a stopword
     */
    public boolean isStopword(String token) {
        if (token == null) return false;
        return stopwords.contains(token.toLowerCase(Locale.ROOT));
    }

    private QueryIntent detectIntent(List<String> tokens, String lowerText) {
        if (tokens.isEmpty()) {
            return QueryIntent.GENERAL_SEARCH;
        }

        String firstToken = tokens.get(0);
        if ("where".equals(firstToken) || lowerText.contains("location of") || lowerText.contains("where is") || lowerText.contains("where did")) {
            return QueryIntent.LOCATION_LOOKUP;
        }

        if ("when".equals(firstToken) || lowerText.contains("what time") || lowerText.contains("reminder for")) {
            return QueryIntent.TEMPORAL_LOOKUP;
        }

        return QueryIntent.GENERAL_SEARCH;
    }
}
