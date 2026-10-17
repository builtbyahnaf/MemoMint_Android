package dev.builtbyahnaf.memomint.domain.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Scored, explainable search result encapsulating a retrieved {@link Memory}, its component
 * relevance scores, verifiable explanation tags, and separate trust/freshness status.
 *
 * <h2>Architectural Principles (Sections 12.4, 12.5 & Phase 2 Plan §3.6)</h2>
 * <ul>
 *   <li><b>Explainable Provenance:</b> Every result must provide transparent, verifiable reasons
 *       for why it matched (e.g., "Exact match on 'cable'", "Synonym match: 'backup' ↔ 'spare'").
 *       The engine must never fabricate reasons post-hoc.</li>
 *   <li><b>Relevance vs. Trust Separation:</b> Semantic relevance rank ($[0.0, 1.0]$) is strictly
 *       separated from temporal freshness and user confirmation. A memory describing an old location
 *       is not semantically downgraded simply because it hasn't been confirmed recently; instead,
 *       its freshness status is reported explicitly via {@link FreshnessStatus}.</li>
 * </ul>
 */
public final class SearchResult implements Comparable<SearchResult> {

    /**
     * Temporal freshness and confirmation state for the retrieved memory.
     */
    public enum FreshnessStatus {
        /** Physical item or location confirmed by user within the last 30 days. */
        CONFIRMED_RECENT,
        /** Physical item or location whose confirmation is older than 30 days. */
        CONFIRMED_STALE,
        /** Physical item or location never confirmed by user. */
        UNCONFIRMED,
        /** Non-physical memory where confirmation is not applicable (e.g. general notes). */
        NOT_APPLICABLE
    }

    private final Memory memory;
    private final double finalScore;
    private final double textScore;
    private final double synonymScore;
    private final double metadataScore;
    private final double fuzzyScore;
    private final List<String> explanations;
    private final List<String> matchedTerms;
    private final FreshnessStatus freshnessStatus;

    /**
     * Constructs a SearchResult with all constituent scoring signals and explanations.
     */
    public SearchResult(
            Memory memory,
            double finalScore,
            double textScore,
            double synonymScore,
            double metadataScore,
            double fuzzyScore,
            List<String> explanations,
            List<String> matchedTerms,
            FreshnessStatus freshnessStatus
    ) {
        this.memory = Objects.requireNonNull(memory, "memory cannot be null");
        this.finalScore = Math.max(0.0, Math.min(1.0, finalScore));
        this.textScore = Math.max(0.0, Math.min(1.0, textScore));
        this.synonymScore = Math.max(0.0, Math.min(1.0, synonymScore));
        this.metadataScore = Math.max(0.0, Math.min(1.0, metadataScore));
        this.fuzzyScore = Math.max(0.0, Math.min(1.0, fuzzyScore));
        this.explanations = Collections.unmodifiableList(new ArrayList<>(explanations != null ? explanations : Collections.emptyList()));
        this.matchedTerms = Collections.unmodifiableList(new ArrayList<>(matchedTerms != null ? matchedTerms : Collections.emptyList()));
        this.freshnessStatus = (freshnessStatus != null) ? freshnessStatus : calculateFreshness(memory);
    }

    /**
     * Computes the freshness status for a memory based on its type and lastConfirmedAt timestamp.
     *
     * @param memory target memory
     * @return evaluated {@link FreshnessStatus}
     */
    public static FreshnessStatus calculateFreshness(Memory memory) {
        if (memory == null) return FreshnessStatus.NOT_APPLICABLE;

        boolean isPhysical = (memory.getType() == MemoryType.PHYSICAL_ITEM || memory.getType() == MemoryType.LOCATION);
        if (!isPhysical) {
            return FreshnessStatus.NOT_APPLICABLE;
        }

        Long confirmedAt = memory.getLastConfirmedAt();
        if (confirmedAt == null) {
            return FreshnessStatus.UNCONFIRMED;
        }

        long thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L;
        long elapsed = System.currentTimeMillis() - confirmedAt;
        if (elapsed <= thirtyDaysMillis && elapsed >= 0) {
            return FreshnessStatus.CONFIRMED_RECENT;
        } else {
            return FreshnessStatus.CONFIRMED_STALE;
        }
    }

    public Memory getMemory() {
        return memory;
    }

    /** Combined final relevance score in range $[0.0, 1.0]$. */
    public double getFinalScore() {
        return finalScore;
    }

    public double getTextScore() {
        return textScore;
    }

    public double getSynonymScore() {
        return synonymScore;
    }

    public double getMetadataScore() {
        return metadataScore;
    }

    public double getFuzzyScore() {
        return fuzzyScore;
    }

    public List<String> getExplanations() {
        return explanations;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    public FreshnessStatus getFreshnessStatus() {
        return freshnessStatus;
    }

    /**
     * Natural order sorts descending by {@link #getFinalScore()}.
     */
    @Override
    public int compareTo(SearchResult other) {
        if (other == null) return -1;
        // Higher score comes first (descending)
        return Double.compare(other.finalScore, this.finalScore);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchResult)) return false;
        SearchResult that = (SearchResult) o;
        return memory.getId().equals(that.memory.getId());
    }

    @Override
    public int hashCode() {
        return memory.getId().hashCode();
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "id='" + memory.getId() + '\'' +
                ", score=" + String.format(java.util.Locale.ROOT, "%.3f", finalScore) +
                ", freshness=" + freshnessStatus +
                ", explanations=" + explanations +
                '}';
    }
}
