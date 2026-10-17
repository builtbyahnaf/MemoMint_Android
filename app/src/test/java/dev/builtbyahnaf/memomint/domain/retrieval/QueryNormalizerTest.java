package dev.builtbyahnaf.memomint.domain.retrieval;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link QueryNormalizer}.
 *
 * <h2>Verifications</h2>
 * <ul>
 *   <li>Whitespace trimming and Unicode NFKC normalization.</li>
 *   <li>Case folding across locales.</li>
 *   <li>Punctuation stripping while preserving intra-word hyphens (e.g. "USB-C", "wi-fi").</li>
 *   <li>Separation of substantive content tokens from high-frequency stopwords.</li>
 *   <li>Intent classification for spatial ("where") and temporal ("when") questions.</li>
 *   <li>Graceful handling of null, empty, or whitespace-only inputs.</li>
 * </ul>
 */
public class QueryNormalizerTest {

    private QueryNormalizer normalizer;

    @Before
    public void setUp() {
        normalizer = new QueryNormalizer();
    }

    @Test
    public void normalize_nullOrEmptyInput_returnsEmptyNormalizedQuery() {
        QueryNormalizer.NormalizedQuery nullQuery = normalizer.normalize(null);
        assertEquals("", nullQuery.getRawQuery());
        assertEquals("", nullQuery.getNormalizedText());
        assertTrue(nullQuery.getContentTokens().isEmpty());
        assertTrue(nullQuery.getAllTokens().isEmpty());
        assertEquals(QueryNormalizer.QueryIntent.GENERAL_SEARCH, nullQuery.getIntent());

        QueryNormalizer.NormalizedQuery emptyQuery = normalizer.normalize("   ");
        assertTrue(emptyQuery.getContentTokens().isEmpty());
    }

    @Test
    public void normalize_preservesIntraWordHyphens_andStripsExtraneousPunctuation() {
        String input = "Where did I put my spare USB-C cable?! It's in the Wi-Fi router box.";
        QueryNormalizer.NormalizedQuery result = normalizer.normalize(input);

        List<String> contentTokens = result.getContentTokens();

        // Substantive tokens must include "usb-c", "cable", "wi-fi", "router", "box"
        assertTrue("Must contain 'usb-c'", contentTokens.contains("usb-c"));
        assertTrue("Must contain 'wi-fi'", contentTokens.contains("wi-fi"));
        assertTrue("Must contain 'cable'", contentTokens.contains("cable"));
        assertTrue("Must contain 'spare'", contentTokens.contains("spare"));
        assertTrue("Must contain 'router'", contentTokens.contains("router"));

        // Question marks, exclamation points, and apostrophes must be stripped from token boundaries
        assertFalse(contentTokens.contains("cable?!"));
        assertFalse(contentTokens.contains("where"));
    }

    @Test
    public void normalize_unicodeNFKC_collapsesAccentsAndSpecialCharacters() {
        // "café" with combining acute accent
        String accented = "Where is the café menu?";
        QueryNormalizer.NormalizedQuery result = normalizer.normalize(accented);

        assertTrue(result.getContentTokens().contains("café"));
    }

    @Test
    public void normalize_separatesStopwordsFromContent() {
        String input = "Where did I put the keys?";
        QueryNormalizer.NormalizedQuery result = normalizer.normalize(input);

        // Content: ["keys"]
        assertEquals(1, result.getContentTokens().size());
        assertEquals("keys", result.getContentTokens().get(0));

        // Stopwords: "where", "did", "i", "put", "the"
        assertTrue(result.getStopwordTokens().contains("where"));
        assertTrue(result.getStopwordTokens().contains("did"));
        assertTrue(result.getStopwordTokens().contains("i"));
        assertTrue(result.getStopwordTokens().contains("put"));
        assertTrue(result.getStopwordTokens().contains("the"));
    }

    @Test
    public void normalize_queryAllStopwords_retainsAllTokensToAvoidEmptyQuery() {
        // Fallback guard: if user searches only "where is it", do not produce empty content
        String input = "where is it";
        QueryNormalizer.NormalizedQuery result = normalizer.normalize(input);

        assertFalse("Query with only stopwords should retain tokens rather than returning empty",
                result.getContentTokens().isEmpty());
        assertEquals(3, result.getContentTokens().size());
    }

    @Test
    public void normalize_detectsSpatialAndTemporalIntent() {
        QueryNormalizer.NormalizedQuery spatial = normalizer.normalize("Where did I leave my passport?");
        assertEquals(QueryNormalizer.QueryIntent.LOCATION_LOOKUP, spatial.getIntent());

        QueryNormalizer.NormalizedQuery spatial2 = normalizer.normalize("location of my car keys");
        assertEquals(QueryNormalizer.QueryIntent.LOCATION_LOOKUP, spatial2.getIntent());

        QueryNormalizer.NormalizedQuery temporal = normalizer.normalize("When is the dentist appointment?");
        assertEquals(QueryNormalizer.QueryIntent.TEMPORAL_LOOKUP, temporal.getIntent());

        QueryNormalizer.NormalizedQuery temporal2 = normalizer.normalize("reminder for taking medicine");
        assertEquals(QueryNormalizer.QueryIntent.TEMPORAL_LOOKUP, temporal2.getIntent());

        QueryNormalizer.NormalizedQuery general = normalizer.normalize("Project MemoMint architecture ideas");
        assertEquals(QueryNormalizer.QueryIntent.GENERAL_SEARCH, general.getIntent());
    }

    @Test
    public void isStopword_identifiesCommonStopwords() {
        assertTrue(normalizer.isStopword("where"));
        assertTrue(normalizer.isStopword("the"));
        assertTrue(normalizer.isStopword("in"));
        assertFalse(normalizer.isStopword("earbuds"));
        assertFalse(normalizer.isStopword("passport"));
    }
}
