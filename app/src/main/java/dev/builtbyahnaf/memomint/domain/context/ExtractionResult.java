package dev.builtbyahnaf.memomint.domain.context;

import java.util.Objects;

/**
 * Domain model representing a single deterministic fact extracted from memory text.
 *
 * <h2>Architectural Principles (FR-03, Section 11 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Original Text Invariance:</b> Extracted facts are purely additive auxiliary metadata.
 *       They never mutate, overwrite, or truncate the user's verbatim original memory text.</li>
 *   <li><b>Trust &amp; Provenance Invariant:</b> Machine-extracted context is always assigned
 *       {@link Provenance#EXTRACTED_BY_RULE}. It is never presented to the user as confirmed ground truth
 *       unless explicitly verified and promoted by user confirmation.</li>
 *   <li><b>Versioned Heuristics:</b> Each extraction records the generator vocabulary version
 *       ({@code generatorVersion}) that produced it, enabling cache invalidation and re-indexing
 *       when extraction rule dictionaries evolve.</li>
 * </ul>
 */
public final class ExtractionResult {

    /**
     * Categorical classification of the extracted entity or attribute.
     */
    public enum Type {
        /** Physical object noun or equipment mention (e.g. "earphones", "cable"). */
        ITEM_NAME,
        /** Spatial placement, container, or location phrase (e.g. "bedside table", "desk drawer"). */
        LOCATION,
        /** Quantified count or container packaging (e.g. "3 boxes of pens", "5 cables"). */
        QUANTITY,
        /** Temporal phrase, date, or relative time mention (e.g. "tomorrow", "next Monday"). */
        TEMPORAL_MENTION,
        /** Normalized keyword token expanded with synonyms for associative retrieval. */
        KEYWORD
    }

    /**
     * Data origin and trust classification for the extracted fact.
     */
    public enum Provenance {
        /** Automatically extracted by deterministic rule, regex, or vocabulary lookup. */
        EXTRACTED_BY_RULE,
        /** Explicitly entered or confirmed by the user. */
        USER_ENTERED
    }

    private final String memoryId;
    private final Type type;
    private final String content;
    private final String canonicalLabel;
    private final Provenance provenance;
    private final int generatorVersion;

    /**
     * Full constructor initializing all extraction result fields.
     *
     * @param memoryId         UUID of the originating memory; may be null if generated prior to persistence
     * @param type             categorical classification of the fact
     * @param content          raw or extracted text segment
     * @param canonicalLabel   canonicalized or normalized representation
     * @param provenance       extraction provenance (defaults to EXTRACTED_BY_RULE)
     * @param generatorVersion vocabulary or rule schema version
     */
    public ExtractionResult(
            String memoryId,
            Type type,
            String content,
            String canonicalLabel,
            Provenance provenance,
            int generatorVersion
    ) {
        this.memoryId = memoryId;
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null").trim();
        this.canonicalLabel = Objects.requireNonNull(canonicalLabel, "canonicalLabel cannot be null").trim();
        this.provenance = (provenance != null) ? provenance : Provenance.EXTRACTED_BY_RULE;
        this.generatorVersion = generatorVersion;
    }

    /**
     * Convenience constructor for rule extractors operating during memory ingestion.
     *
     * @param memoryId       UUID of the originating memory
     * @param type           type of the extracted fact
     * @param content        raw text span
     * @param canonicalLabel normalized form
     */
    public ExtractionResult(String memoryId, Type type, String content, String canonicalLabel) {
        this(memoryId, type, content, canonicalLabel, Provenance.EXTRACTED_BY_RULE, ItemVocabulary.VOCABULARY_VERSION);
    }

    /**
     * Convenience constructor when memory ID is not yet assigned.
     *
     * @param type           type of the extracted fact
     * @param content        raw text span
     * @param canonicalLabel normalized form
     */
    public ExtractionResult(Type type, String content, String canonicalLabel) {
        this(null, type, content, canonicalLabel, Provenance.EXTRACTED_BY_RULE, ItemVocabulary.VOCABULARY_VERSION);
    }

    /**
     * Returns a new ExtractionResult identical to this instance but associated with the given memory ID.
     *
     * @param memoryId target memory UUID
     * @return copy with specified memoryId
     */
    public ExtractionResult withMemoryId(String memoryId) {
        return new ExtractionResult(memoryId, this.type, this.content, this.canonicalLabel, this.provenance, this.generatorVersion);
    }

    public String getMemoryId() {
        return memoryId;
    }

    public Type getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getCanonicalLabel() {
        return canonicalLabel;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public int getGeneratorVersion() {
        return generatorVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExtractionResult)) return false;
        ExtractionResult that = (ExtractionResult) o;
        return generatorVersion == that.generatorVersion
                && Objects.equals(memoryId, that.memoryId)
                && type == that.type
                && Objects.equals(content, that.content)
                && Objects.equals(canonicalLabel, that.canonicalLabel)
                && provenance == that.provenance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(memoryId, type, content, canonicalLabel, provenance, generatorVersion);
    }

    @Override
    public String toString() {
        return "ExtractionResult{" +
                "memoryId='" + memoryId + '\'' +
                ", type=" + type +
                ", content='" + content + '\'' +
                ", canonicalLabel='" + canonicalLabel + '\'' +
                ", provenance=" + provenance +
                ", generatorVersion=" + generatorVersion +
                '}';
    }
}
