package dev.builtbyahnaf.memomint.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a search hit retrieved from indexed document content in the {@code files_fts} table.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §2.3, §6.3)</h2>
 * <p>Represents a matching document candidate with associated snippet context, match score,
 * and terms suitable for highlighting. Can be presented independently or merged into
 * unified search result streams.</p>
 */
public final class FileSearchResult implements Comparable<FileSearchResult> {

    private final String fileId;
    private final String title;
    private final String snippet;
    private final double score;
    private final List<String> matchedTerms;

    public FileSearchResult(
            String fileId,
            String title,
            String snippet,
            double score,
            List<String> matchedTerms
    ) {
        this.fileId = Objects.requireNonNull(fileId, "fileId cannot be null");
        this.title = (title != null) ? title : "";
        this.snippet = (snippet != null) ? snippet : "";
        this.score = score;
        this.matchedTerms = Collections.unmodifiableList(
                new ArrayList<>(matchedTerms != null ? matchedTerms : Collections.emptyList())
        );
    }

    public String getFileId() {
        return fileId;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public double getScore() {
        return score;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    @Override
    public int compareTo(FileSearchResult other) {
        // Higher scores first
        return Double.compare(other.score, this.score);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileSearchResult)) return false;
        FileSearchResult that = (FileSearchResult) o;
        return Objects.equals(fileId, that.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId);
    }

    @Override
    public String toString() {
        return "FileSearchResult{"
                + "fileId='" + fileId + '\''
                + ", title='" + title + '\''
                + ", score=" + score
                + ", terms=" + matchedTerms
                + '}';
    }
}
