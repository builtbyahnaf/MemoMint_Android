package dev.builtbyahnaf.memomint.domain.retrieval;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SynonymDictionary}.
 *
 * <h2>Verifications</h2>
 * <ul>
 *   <li>Bidirectional symmetry and cluster completeness.</li>
 *   <li>Multi-word phrase matching ("dust blower" ↔ "air duster").</li>
 *   <li>Exclusion of query term itself from its synonym set.</li>
 *   <li>Conservative boundary enforcement (no semantic drift to distant concepts).</li>
 *   <li>Token expansion mapping.</li>
 *   <li>Version constant tracking.</li>
 * </ul>
 */
public class SynonymDictionaryTest {

    private SynonymDictionary dictionary;

    @Before
    public void setUp() {
        dictionary = new SynonymDictionary();
    }

    @Test
    public void dictionaryVersion_isDefinedAndPositive() {
        assertTrue("Dictionary version must be >= 1", SynonymDictionary.DICTIONARY_VERSION >= 1);
    }

    @Test
    public void getSynonyms_returnsBidirectionalEquivalents_excludingQueriedTerm() {
        Set<String> syns = dictionary.getSynonyms("earbuds");
        assertFalse("Synonym set must not contain the query term itself", syns.contains("earbuds"));
        assertTrue(syns.contains("earphones"));
        assertTrue(syns.contains("headphones"));

        // Reverse direction
        Set<String> reverse = dictionary.getSynonyms("earphones");
        assertFalse(reverse.contains("earphones"));
        assertTrue(reverse.contains("earbuds"));
        assertTrue(reverse.contains("headphones"));
    }

    @Test
    public void areSynonyms_evaluatesTransitiveEquivalenceClusters() {
        // wardrobe ↔ closet ↔ cupboard
        assertTrue(dictionary.areSynonyms("wardrobe", "closet"));
        assertTrue(dictionary.areSynonyms("closet", "wardrobe"));
        assertTrue(dictionary.areSynonyms("wardrobe", "cupboard"));
        assertTrue(dictionary.areSynonyms("closet", "cupboard"));

        // spare ↔ backup ↔ extra
        assertTrue(dictionary.areSynonyms("spare", "backup"));
        assertTrue(dictionary.areSynonyms("backup", "extra"));
        assertTrue(dictionary.areSynonyms("spare", "extra"));

        // cable ↔ cord ↔ wire
        assertTrue(dictionary.areSynonyms("cable", "cord"));
        assertTrue(dictionary.areSynonyms("wire", "cable"));
    }

    @Test
    public void multiWordPhrases_areMappedCorrectly() {
        Set<String> syns = dictionary.getSynonyms("dust blower");
        assertTrue(syns.contains("air duster"));
        assertTrue(syns.contains("keyboard cleaner"));

        Set<String> chargerSyns = dictionary.getSynonyms("charger");
        assertTrue(chargerSyns.contains("power adapter"));
        assertTrue(chargerSyns.contains("charging brick"));
    }

    @Test
    public void conservativeBoundaries_preventSemanticDrift() {
        // "laptop" vs "desktop" or "phone" should not be marked as synonyms
        assertFalse(dictionary.areSynonyms("laptop", "desktop"));
        assertFalse(dictionary.areSynonyms("phone", "computer"));
        assertFalse(dictionary.areSynonyms("keys", "wallet"));

        // Unknown term yields empty set
        Set<String> unknown = dictionary.getSynonyms("supercalifragilistic");
        assertTrue(unknown.isEmpty());
    }

    @Test
    public void expandTokens_mapsContentTokensToSynonyms() {
        Map<String, Set<String>> expansions = dictionary.expandTokens(Arrays.asList("backup", "cable", "passport"));

        assertTrue(expansions.containsKey("backup"));
        assertTrue(expansions.get("backup").contains("spare"));

        assertTrue(expansions.containsKey("cable"));
        assertTrue(expansions.get("cable").contains("cord"));

        assertTrue(expansions.containsKey("passport"));
        assertTrue(expansions.get("passport").contains("travel document"));
    }

    @Test
    public void addCluster_mergesOverlappingClusters() {
        SynonymDictionary customDict = new SynonymDictionary();
        customDict.addCluster("auto", "car");
        customDict.addCluster("car", "vehicle");

        assertTrue(customDict.areSynonyms("auto", "vehicle"));
        assertTrue(customDict.areSynonyms("vehicle", "car"));
    }
}
