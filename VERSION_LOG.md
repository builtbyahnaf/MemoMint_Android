# Version 1.014.7-S

**Build:** `1047648`

Enhanced search result presentation with dynamic, in-place text highlighting, replacing verbose explanatory text with clear visual match indicators. Improved SQLite FTS compatibility, search fallback mechanisms, and prefix/substring matching reliability.

## Summary of Fixes & Enhancements

### 1. Critical Database Crash Fix — `no such module: fts5`

**Root cause**

SQLite engines compiled without `SQLITE_ENABLE_FTS5` support—including certain OEM ROMs, emulators, and JVM testing environments—failed to execute FTS5 virtual table creation statements. Additionally, synchronization triggers (`trigger_memories_ai`, `trigger_memories_ad`, and `trigger_memories_au`) were registered unconditionally, causing database operations to fail when FTS5 was unavailable.

**Fix — `MemoMintDatabase.java`**

- Added SQLite capability detection using `sqlite_compileoption_used` and `PRAGMA module_list`.
- Implemented a cascading fallback strategy:
  1. **FTS5** — BM25 ranking and diacritic stripping.
  2. **FTS4** — Alternative full-text indexing supported by many Android SQLite builds.
  3. **FTS3** — Broader compatibility with SQLite environments, including JVM/Robolectric testing.
  4. **Relational mode** — Ensures basic memory operations remain available without full-text indexing.
- Made synchronization trigger creation conditional on successful FTS virtual table initialization, preventing unsupported FTS modules from breaking regular CRUD operations.
- Added automatic schema recovery through `repairFtsSchemaIfUnsupported()` in `onOpen`, allowing unsupported existing FTS schemas to be repaired and re-indexed without requiring users to clear application data.
- Preserved native Android `android.database.sqlite` imports throughout the application.

### 2. Case-Insensitive Prefix & Substring Search

**Example:** Searching for `air` can now match `Airpods`.

- **`SearchRepositoryImpl.java`**
  - Corrected `sanitizeFtsQuery()` to generate bareword prefix queries such as `air*` instead of quoted prefix expressions such as `"air"*`.
  - Added a multi-tier retrieval strategy:
    1. FTS5 with BM25 ranking.
    2. FTS4/FTS3 fallback.
    3. Case-insensitive SQL `LIKE '%token%'` matching against `memories.title` and `memories.original_text`.

- **`SearchService.java`**
  - Populates `matchedTerms` using search tokens and matching raw-query substrings found in memory content.

### 3. In-Place Visual Match Highlighting

**Files:** `SearchAdapter.java`, `colors.xml`

- Reworked `highlightTerms()` to highlight matching words and substrings directly within search result titles and body text.
- Introduced mint-green highlighting with the following styles:
  - **Background:** `search_highlight_bg` — `#A7F3D0`
  - **Text:** `search_highlight_text` — `#064E3B`
  - **Emphasis:** `StyleSpan(Typeface.BOLD)`
- Removed redundant provenance labels, such as `Matched memory title...`, allowing users to identify relevant matches directly within the result cards.

### 4. Verification & Testing

- **Java toolchain:** Configured Java 17 in `build.gradle`.
- **New unit test:** Added `search_caseInsensitivePrefixMatching_matchesAirpodsForAir` to `RetrievalBenchmarkTest.java`.
- **Test results:** All **67 unit tests passed**, covering the database, repository, service, and retrieval engine.
- **Build verification:** `.\gradlew.bat assembleDebug` completed successfully, producing the debug APK with zero build errors.



# Version 1.014.2-D

**Build:** `1042486`

## Bug Fix: SQLite FTS5 Initialization Failure

### Root Cause

`MemoMintDatabase` relied on Android's `android.database.sqlite.SQLiteOpenHelper`, which delegates database operations to the system SQLite implementation.

On Android API levels 30–35, OEM-provided SQLite builds may lack FTS5 support. Consequently, executing the following statement caused a database initialization failure:

```sql
CREATE VIRTUAL TABLE ... USING fts5(...)
```

**Error:**

```text
SQLiteException: no such module: fts5
```

This prevented the database from initializing correctly, causing `loadMemories()` to fail immediately on application launch.

### Fix

- Resolved dependency resolution errors.
- Refactored the relevant database code to address the initialization failure.


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

