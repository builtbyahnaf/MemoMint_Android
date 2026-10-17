# Version 1.013.7-C

**Build:** `1031415`

## Changelog

### 1. Query Normalization & Intent Tagging

- **`QueryNormalizer.java`**
  - Strips boundary punctuation while preserving intra-word hyphens (`usb-c`, `wi-fi`).
  - Normalizes Unicode to NFKC form and folds case.
  - Extracts substantive content tokens and tags stopwords.
  - Classifies query intent into:
    - `LOCATION_LOOKUP`
    - `TEMPORAL_LOOKUP`
    - `GENERAL_SEARCH`

### 2. Curated Concept & Synonym Dictionary

- **`SynonymDictionary.java`**
  - Implements a fully offline, version-tracked, bidirectional synonym dictionary using equivalence clusters.
  - Supports curated synonym relationships, including:
    - `earbuds` ↔ `earphones` ↔ `headphones`
    - `cable` ↔ `cord` ↔ `wire`
    - `wardrobe` ↔ `closet`
    - `spare` ↔ `backup`
    - `dust blower` ↔ `air duster`
  - Bounds synonym expansion to prevent semantic drift.

### 3. Typo-Tolerant Matching

- **`FuzzyMatcher.java`**
  - Uses the Damerau–Levenshtein distance metric to handle:
    - Substitutions
    - Insertions
    - Deletions
    - Adjacent character transpositions
  - Enforces a minimum word length of **4 characters**.
  - Requires a similarity threshold of **≥ 0.75** to reduce false-positive matches on short words.

### 4. Normalized Hybrid Scoring & Trust Separation

- **`RelevanceScorer.java`**
  - Combines multiple relevance signals using the following weighted scoring formula:

    \[
    S_{\text{final}} =
    0.50S_{\text{text}} +
    0.30S_{\text{synonym}} +
    0.15S_{\text{meta}} +
    0.05S_{\text{fuzzy}}
    \]

  - Applies monotonic arctangent compression to normalize unbounded raw BM25 scores:

    \[
    S_{\text{BM25}} =
    \frac{2}{\pi}\arctan(|\text{BM25}|)
    \]

    This maps raw BM25 scores into the range \([0.0, 1.0)\).

- **`SearchResult.java`**
  - Represents scored search results alongside a separate freshness status:
    - `CONFIRMED_RECENT`
    - `CONFIRMED_STALE`
    - `UNCONFIRMED`
  - Provides verifiable explanations of match provenance, keeping relevance scoring separate from freshness and trust information.

### 5. SQLite Schema Migration & FTS5 Indexing

- **`DatabaseConstants.java`**
  - Bumps the database schema to **Version 2**.
  - Adds the `memories_fts` virtual table using SQLite FTS5 with the `unicode61` tokenizer and `remove_diacritics 2`.
  - Defines automatic synchronization triggers:
    - `AFTER INSERT`
    - `AFTER UPDATE`
    - `AFTER DELETE`

- **`MemoMintDatabase.java`**
  - Implements a safe, transactional migration from schema v1 to v2.
  - Populates the full-text search index with existing memory records.

- **`SearchRepository.java`** & **`SearchRepositoryImpl.java`**
  - Implement candidate retrieval queries with FTS query sanitization.
  - Integrate BM25 relevance scoring.
  - Support retrieval from active memory pools.

### 6. Application Service & Presentation

- **`SearchService.java`**
  - Coordinates the seven-stage retrieval pipeline asynchronously, keeping search processing off the UI thread.

- **`SearchAdapter.java`** & **`item_search_result.xml`**
  - Display percentage-based match badges.
  - Highlight matched terms in bold.
  - Show freshness indicators.
  - Present transparent, bullet-point explanations of why each result matched.

- **`MainActivity.java`** & **`activity_main.xml`**
  - Integrate the search bar with real-time query listening.
  - Support seamless transitions between the normal feed and search mode.

---

## Retrieval Evaluation Benchmark Results

The standardized evaluation suite in **`RetrievalBenchmarkTest.java`** was executed against **19 labeled test cases** spanning the following categories:

- Direct matches
- Indirect synonym matches
- Typographical errors
- Negative distractors

### Benchmark Summary

| Metric | Target Standard | Measured Result | Status |
|---|---:|---:|:---:|
| Recall@1 | ≥ 70.0% | 84.21% | ✅ Passed |
| Recall@3 | ≥ 85.0% | 94.74% | ✅ Passed |
| Recall@5 | ≥ 90.0% | 94.74% | ✅ Passed |
| Mean Reciprocal Rank (MRR) | ≥ 0.75 | 0.895 | ✅ Passed |
| Average Query Latency | < 50.0 ms | 3.46 ms | ✅ Passed |
| Distractor Query Rejection | Score < 0.50 | 100% rejected | ✅ Passed |

### Key Results

- **High retrieval accuracy:** Recall@1 reached 84.21%, while Recall@3 and Recall@5 both reached 94.74%.
- **Strong ranking quality:** Mean Reciprocal Rank (MRR) measured 0.895, exceeding the target of 0.75.
- **Low search latency:** Average query latency was 3.46 ms, well below the 50 ms target.
- **Effective distractor rejection:** All evaluated distractor queries were rejected.

