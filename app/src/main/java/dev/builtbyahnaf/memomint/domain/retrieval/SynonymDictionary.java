package dev.builtbyahnaf.memomint.domain.retrieval;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated, local, offline bidirectional concept and synonym dictionary.
 *
 * <h2>Problem Solved</h2>
 * <p>Human memory recalls concepts rather than verbatim keywords (e.g., remembering "backup earphones"
 * when the note was captured as "spare earbuds"). Standard keyword search fails when vocabulary mismatches.
 * This dictionary bridges vocabulary gaps entirely offline without external network queries or heavy neural weights.</p>
 *
 * <h2>Design & Invariants (Section 12.2 & Phase 2 Plan)</h2>
 * <ul>
 *   <li><b>Bidirectional Clusters:</b> Equivalence is transitive and symmetric within defined clusters
 *       (e.g., if A ↔ B and B ↔ C, then A, B, and C are mutually synonymous).</li>
 *   <li><b>Conservative Boundaries:</b> Avoids semantic drift. Concepts that are only loosely associated
 *       (e.g., "laptop" vs. "desktop") are NOT grouped together.</li>
 *   <li><b>Multi-Word Phrase Support:</b> Handles compound phrases such as "dust blower" ↔ "air duster"
 *       and "power adapter" ↔ "charger".</li>
 *   <li><b>Versioned Vocabulary:</b> Governed by {@link #DICTIONARY_VERSION} for reproducible retrieval benchmarks.</li>
 * </ul>
 *
 * <h2>Algorithm & Complexity</h2>
 * <p>Clusters are initialized at class construction into a bipartite inverted index map:
 * {@code Map<String, Set<String>>}. Lookup for any term is $O(1)$ average time, returning the set
 * of equivalent terms.</p>
 */
public final class SynonymDictionary {

    /**
     * Version of the vocabulary schema. Increment whenever clusters are added or modified
     * to preserve benchmark test comparability (Section 12.6).
     */
    public static final int DICTIONARY_VERSION = 1;

    /** Inverted index mapping each normalized term/phrase to its full synonym cluster set. */
    private final Map<String, Set<String>> synonymMap = new HashMap<>();

    /**
     * Constructs a SynonymDictionary populated with MemoMint's default curated concept clusters.
     */
    public SynonymDictionary() {
        initializeDefaultClusters();
    }

    /**
     * Initializes the default concept equivalence clusters.
     */
    private void initializeDefaultClusters() {
        // Tech & Peripheral Accessories
        addCluster("earbuds", "earphones", "headphones", "headset");
        addCluster("cable", "cord", "wire");
        addCluster("charger", "power adapter", "charging brick", "adapter", "power brick");
        addCluster("dust blower", "air duster", "keyboard cleaner", "compressed air");
        addCluster("pen drive", "flash drive", "thumb drive", "usb drive");

        // Spatial & Furniture Concepts
        addCluster("wardrobe", "closet", "cupboard", "armoire");
        addCluster("drawer", "desk drawer", "cabinet drawer");
        addCluster("desk", "workstation", "study table");
        addCluster("couch", "sofa", "settee");
        addCluster("bin", "trash can", "dustbin", "wastebasket");

        // Everyday Belongings & Personal Items
        addCluster("spare", "backup", "extra", "reserve");
        addCluster("passport", "travel document", "identification", "id card");
        addCluster("glasses", "spectacles", "eyewear", "reading glasses");
        addCluster("keys", "keychain", "house keys", "car keys");
        addCluster("wallet", "purse", "billfold");
        addCluster("bag", "backpack", "rucksack", "knapsack", "satchel");
        addCluster("medicine", "medication", "pills", "prescription", "drugs");
        addCluster("receipt", "invoice", "bill", "voucher");
        addCluster("umbrella", "parasol", "brolly");
        addCluster("jacket", "coat", "outerwear", "parka");
    }

    /**
     * Registers a symmetric equivalence cluster where all terms are mutual synonyms.
     *
     * @param terms variable list of equivalent phrases/words; must have at least 2 terms
     */
    public void addCluster(String... terms) {
        if (terms == null || terms.length < 2) {
            return;
        }

        Set<String> normalizedSet = new HashSet<>();
        for (String term : terms) {
            if (term != null && !term.trim().isEmpty()) {
                normalizedSet.add(term.trim().toLowerCase(Locale.ROOT));
            }
        }

        Set<String> unmodifiableSet = Collections.unmodifiableSet(normalizedSet);
        for (String term : normalizedSet) {
            // If the term was already in a cluster, merge them
            Set<String> existing = synonymMap.get(term);
            if (existing != null) {
                Set<String> merged = new HashSet<>(existing);
                merged.addAll(normalizedSet);
                Set<String> unmodifiableMerged = Collections.unmodifiableSet(merged);
                for (String t : merged) {
                    synonymMap.put(t, unmodifiableMerged);
                }
            } else {
                synonymMap.put(term, unmodifiableSet);
            }
        }
    }

    /**
     * Retrieves all known synonyms for a given term, excluding the term itself.
     *
     * @param term the query term (single word or phrase)
     * @return unmodifiable set of synonyms, or empty set if the term is unknown
     */
    public Set<String> getSynonyms(String term) {
        if (term == null) return Collections.emptySet();
        String normalized = term.trim().toLowerCase(Locale.ROOT);
        Set<String> cluster = synonymMap.get(normalized);
        if (cluster == null) {
            return Collections.emptySet();
        }

        Set<String> results = new HashSet<>(cluster);
        results.remove(normalized);
        return Collections.unmodifiableSet(results);
    }

    /**
     * Checks whether two terms are known synonyms within the dictionary.
     *
     * @param term1 first term
     * @param term2 second term
     * @return true if both terms belong to the same equivalence cluster
     */
    public boolean areSynonyms(String term1, String term2) {
        if (term1 == null || term2 == null) return false;
        String n1 = term1.trim().toLowerCase(Locale.ROOT);
        String n2 = term2.trim().toLowerCase(Locale.ROOT);
        if (n1.equals(n2)) return true;

        Set<String> cluster = synonymMap.get(n1);
        return cluster != null && cluster.contains(n2);
    }

    /**
     * Expands a list of tokens by appending all known synonyms for each token.
     *
     * @param tokens input content tokens
     * @return a map from original token to its synonym expansion set
     */
    public Map<String, Set<String>> expandTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<String>> expansions = new HashMap<>();
        for (String token : tokens) {
            Set<String> syns = getSynonyms(token);
            if (!syns.isEmpty()) {
                expansions.put(token, syns);
            }
        }
        return expansions;
    }

    /**
     * Returns the total number of unique terms registered in the dictionary.
     */
    public int getTermCount() {
        return synonymMap.size();
    }
}
