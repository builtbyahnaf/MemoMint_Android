package dev.builtbyahnaf.memomint.data.repository;

import java.util.List;

import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;

/**
 * Repository interface defining candidate retrieval queries for the search and retrieval engine.
 *
 * <h2>Purpose & Architectural Boundary</h2>
 * <p>Belongs to Layer D (Data and Indexing). Decouples candidate queries (FTS5 full-text matching,
 * BM25 relevance scoring, metadata filtering, and candidate extraction) from application services
 * and presentation layers.</p>
 */
public interface SearchRepository {

    /**
     * Encapsulates a candidate memory retrieved from SQLite FTS5 alongside its raw BM25 score
     * and highlighted snippet.
     */
    class FtsCandidate {
        private final Memory memory;
        private final double rawBm25;
        private final String snippet;

        public FtsCandidate(Memory memory, double rawBm25, String snippet) {
            this.memory = memory;
            this.rawBm25 = rawBm25;
            this.snippet = snippet;
        }

        public Memory getMemory() {
            return memory;
        }

        /**
         * Raw BM25 score from SQLite FTS5 {@code bm25()}. Note: SQLite returns negative values
         * where smaller (more negative) indicates higher relevance.
         */
        public double getRawBm25() {
            return rawBm25;
        }

        public String getSnippet() {
            return snippet;
        }
    }

    /**
     * Executes an FTS5 match query against {@code memories_fts} joined with the {@code memories} table.
     * Only non-deleted memories are considered.
     *
     * @param ftsQuery formatted FTS5 MATCH expression (e.g. "cable OR cord OR wire")
     * @param limit    maximum number of candidate rows to return
     * @return list of matching {@link FtsCandidate} instances ordered by BM25 rank
     */
    List<FtsCandidate> searchFts(String ftsQuery, int limit);

    /**
     * Retrieves active memories filtered by metadata (e.g. MemoryType).
     *
     * @param type  memory type filter, or null to retrieve regardless of type
     * @param limit maximum number of candidates
     * @return list of matching active memories
     */
    List<Memory> searchByMetadata(MemoryType type, int limit);

    /**
     * Retrieves all active memories up to a bounded limit for in-memory fuzzy matching and scanning.
     *
     * @param limit maximum number of memories to load
     * @return list of active memories
     */
    List<Memory> getAllActiveMemories(int limit);
}
