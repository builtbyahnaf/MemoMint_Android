package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.FileSearchResult;

/**
 * Repository interface for document full-text search indexing and retrieval via {@code files_fts}.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.3)</h2>
 * <ul>
 *   <li><b>Full-Text Indexing:</b> Indexes document body text into SQLite virtual table
 *       ({@code files_fts}) for rapid, zero-cloud search retrieval.</li>
 *   <li><b>Cascade Fallback:</b> Supports FTS5 BM25 queries, FTS4/FTS3 prefix queries, and
 *       relational LIKE queries when virtual tables are unavailable.</li>
 *   <li><b>Snippet Generation:</b> Generates contextual preview snippets with matched term metadata.</li>
 * </ul>
 */
public interface FileSearchRepository {

    /**
     * Stores or replaces indexed text content for a document in the search index.
     *
     * @param fileId      file reference UUID
     * @param title       display name or document title
     * @param textContent extracted plain text content
     */
    void indexDocumentContent(String fileId, String title, String textContent);

    /**
     * Removes all indexed content associated with the specified file reference.
     *
     * @param fileId file reference UUID
     */
    void removeDocumentContent(String fileId);

    /**
     * Queries indexed documents for terms matching the user query.
     *
     * @param query raw or normalized search query string
     * @param limit maximum matching documents to return
     * @return list of ranked FileSearchResult hits
     */
    List<FileSearchResult> searchFiles(String query, int limit);
}
