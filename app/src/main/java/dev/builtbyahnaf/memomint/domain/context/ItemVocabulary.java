package dev.builtbyahnaf.memomint.domain.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated, version-controlled vocabulary of known physical item nouns and their morphological variants.
 *
 * <h2>Design &amp; Conservative Boundary Principles (Section 11.2 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Physical Object Specificity:</b> Only nouns with exceptionally high confidence of representing
 *       concrete physical objects are included. Ambiguous abstract nouns or verbs are strictly excluded
 *       to prevent spurious entity extraction.</li>
 *   <li><b>Canonical Normalization:</b> Each physical item entity is indexed under a canonical singular
 *       form (e.g. "earphone", "cable", "drawer"), mapping to all common pluralizations, abbreviations,
 *       and compound variants (e.g. "earbuds", "usb-c", "mice").</li>
 *   <li><b>Deterministic Fast Lookup:</b> Inverted index mappings provide $O(1)$ variant-to-canonical
 *       resolution without requiring runtime network calls or heavy NLP dependencies.</li>
 *   <li><b>Versioned Vocabulary:</b> Governed by {@link #VOCABULARY_VERSION}. Upgrading this version
 *       signals to background indexing workers that context records may be re-extracted.</li>
 * </ul>
 */
public final class ItemVocabulary {

    /**
     * Version of the item vocabulary schema. Increment whenever items or variants are added or altered.
     */
    public static final int VOCABULARY_VERSION = 1;

    /** Map from canonical item name to its set of morphological and colloquial variants. */
    private static final Map<String, Set<String>> CANONICAL_TO_VARIANTS = new HashMap<>();

    /** Inverted index mapping every variant back to its canonical form. */
    private static final Map<String, String> VARIANT_TO_CANONICAL = new HashMap<>();

    static {
        // --- Category: Electronics & Peripherals ---
        registerItem("earphone", "earphone", "earphones", "earbuds");
        registerItem("cable", "cable", "cables", "usb-c", "usb");
        registerItem("charger", "charger", "chargers", "adapter", "adaptor");
        registerItem("keyboard", "keyboard", "keyboards");
        registerItem("mouse", "mouse", "mice");
        registerItem("laptop", "laptop", "laptops");
        registerItem("phone", "phone", "phones");
        registerItem("tablet", "tablet", "tablets");
        registerItem("blower", "blower", "duster", "air-duster");
        registerItem("airpod", "airpod", "airpods");

        // --- Category: Furniture & Storage ---
        registerItem("drawer", "drawer", "drawers");
        registerItem("shelf", "shelf", "shelves");
        registerItem("cabinet", "cabinet", "cabinets");
        registerItem("closet", "closet");
        registerItem("wardrobe", "wardrobe");
        registerItem("box", "box", "boxes");
        registerItem("bag", "bag", "bags");
        registerItem("case", "case", "cases");
        registerItem("pouch", "pouch", "pouches");
        registerItem("rack", "rack");

        // --- Category: Personal Belongings ---
        registerItem("key", "key", "keys", "keychain");
        registerItem("wallet", "wallet", "wallets");
        registerItem("passport", "passport");
        registerItem("medication", "medication", "medicine", "pills");
        registerItem("glasses", "glasses", "spectacles");

        // --- Category: Office Supplies ---
        registerItem("pen", "pen", "pens");
        registerItem("pencil", "pencil", "pencils");
        registerItem("notebook", "notebook", "notebooks");
        registerItem("stapler", "stapler");
        registerItem("tape", "tape");
        registerItem("scissors", "scissors");
        registerItem("folder", "folder", "folders");
    }

    private ItemVocabulary() {
        // Utility class
    }

    private static void registerItem(String canonical, String... variants) {
        String canonicalLower = canonical.toLowerCase(Locale.ROOT);
        Set<String> variantSet = new HashSet<>();
        variantSet.add(canonicalLower);
        for (String v : variants) {
            if (v != null && !v.trim().isEmpty()) {
                variantSet.add(v.trim().toLowerCase(Locale.ROOT));
            }
        }

        Set<String> unmodifiableVariants = Collections.unmodifiableSet(variantSet);
        CANONICAL_TO_VARIANTS.put(canonicalLower, unmodifiableVariants);

        for (String v : variantSet) {
            VARIANT_TO_CANONICAL.put(v, canonicalLower);
        }
    }

    /**
     * Returns an unmodifiable set of all morphological variants for a given canonical item.
     *
     * @param canonical canonical item noun (e.g. "earphone")
     * @return set of variants (e.g. ["earphone", "earphones", "earbuds"]), or empty set if unknown
     */
    public static Set<String> getVariants(String canonical) {
        if (canonical == null) return Collections.emptySet();
        Set<String> variants = CANONICAL_TO_VARIANTS.get(canonical.trim().toLowerCase(Locale.ROOT));
        return (variants != null) ? variants : Collections.emptySet();
    }

    /**
     * Resolves a variant word or phrase to its canonical dictionary form.
     *
     * @param variant word or phrase variant (e.g. "earbuds", "usb-c", "mice")
     * @return the canonical item label (e.g. "earphone", "cable", "mouse"), or null if unrecognized
     */
    public static String getCanonicalFor(String variant) {
        if (variant == null) return null;
        return VARIANT_TO_CANONICAL.get(variant.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Checks whether a given word or phrase matches any known item variant in the vocabulary.
     *
     * @param word word or phrase to test
     * @return true if the word is an indexed physical item noun
     */
    public static boolean isKnownItem(String word) {
        if (word == null) return false;
        return VARIANT_TO_CANONICAL.containsKey(word.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns an unmodifiable set of all canonical item names.
     *
     * @return set of canonical labels
     */
    public static Set<String> getAllCanonicalItems() {
        return Collections.unmodifiableSet(CANONICAL_TO_VARIANTS.keySet());
    }

    /**
     * Returns the complete unmodifiable map of canonical items to their variant sets.
     *
     * @return vocabulary map
     */
    public static Map<String, Set<String>> getVocabulary() {
        return Collections.unmodifiableMap(CANONICAL_TO_VARIANTS);
    }
}
