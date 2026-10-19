# Version 1.016.2-D

**Build:** `1062487`

## Implementation Summary

### 1. Scoped File Access & Offline Architecture

- Integrates Android's Storage Access Framework (SAF) for user-selected files and directories.
- Uses persisted URI permissions to maintain authorized access across application sessions.
- Restricts document access to user-authorized locations without requiring broad filesystem permissions.
- Keeps document parsing, indexing, and retrieval entirely on-device, with no cloud processing or heavy neural models.

### 2. Multi-Format Document Parsing

Introduces support for extracting searchable text from common local document formats:

- **Plain text:** `.txt`, `.md`, `.json`, `.csv`
- **Microsoft Word:** `.docx`
- **PDF documents:** `.pdf`

#### PDF Parsing — `PdfStreamTextParser`

- Implements a lightweight, pure Java streaming parser for PDF text objects, including `BT`/`ET` blocks and `Tj`/`TJ` operators.
- Supports Deflate decompression without requiring external binaries or heavyweight parsing dependencies.
- Detects encrypted PDFs and classifies failures using `ERR_PDF_ENCRYPTED`.
- Identifies image-only scanned PDFs when no extractable text is found, marking them as `SCANNED_NEEDS_OCR`.
- Never fabricates document content when text extraction is unavailable.
- Falls back to metadata indexing when parsing fails because of malformed cross-reference tables or corrupted streams.

#### DOCX Parsing — `DocxSaxParser`

- Parses `.docx` files as ZIP archives using standard Java ZIP streams and lightweight XML parsing.
- Extracts paragraphs (`<w:p>`) and text runs (`<w:t>`) from `word/document.xml`.
- Avoids heavyweight Apache POI dependencies.
- Detects malformed ZIP entries and XML, classifies failures using `ERR_DOCX_CORRUPT`, and preserves metadata-based discoverability.

#### Plain Text & Markdown Decoding

- Uses UTF-8 decoding with replacement handling for invalid byte sequences.
- Detects UTF-16LE and UTF-16BE byte-order marks.
- Provides ISO-8859-1 as a fallback decoding strategy when required.

### 3. Full-Text Document Indexing & Search

- Introduces the SQLite `files_fts` virtual table for indexing document metadata and extracted content.
- Uses BM25 ranking for relevance-based document retrieval.
- Implements the established SQLite compatibility cascade:
  1. **FTS5** — Full-text search with Unicode normalization and diacritic removal.
  2. **FTS4** — Alternative full-text indexing when FTS5 is unavailable.
  3. **FTS3** — Compatibility fallback for additional SQLite environments, including JVM/Robolectric testing.
  4. **Relational `LIKE` queries** — Final fallback when virtual-table indexing is unavailable.
- Detects FTS capabilities before selecting the indexing strategy.
- Preserves document discoverability through filename, directory path, and modification-time metadata when content extraction fails.

### 4. Incremental Indexing & Deduplication

- Tracks file metadata and modification timestamps to identify changed documents.
- Uses SHA-256 content fingerprints to detect duplicate or unchanged content.
- Avoids unnecessary parsing and indexing work for unchanged files.
- Supports incremental index updates to reduce processing overhead and memory consumption.

### 5. File Health Monitoring & Permission Recovery

- Tracks file availability and access permissions independently from indexing status.
- Distinguishes permission revocation and temporary device/provider unavailability from actual file deletion.
- Supports recovery workflows for files that become inaccessible after initial indexing.
- Preserves existing index records when access is revoked or temporarily interrupted, avoiding premature deletion of searchable references.

### 6. Structured Error Handling & Graceful Degradation

Introduces explicit error classification through `IndexErrorCode`, enabling predictable recovery behavior across file access, parsing, and resource constraints.

| Error Code | Cause | Handling Strategy |
|---|---|---|
| `ERR_PERMISSION_REVOKED` | Persisted URI permission revoked | Mark access as `PERMISSION_REVOKED`; offer permission re-granting. |
| `ERR_FILE_NOT_FOUND` | File moved, renamed, or deleted externally | Mark as `MISSING_OR_UNAVAILABLE`; offer file location or reference removal. |
| `ERR_FILE_TOO_LARGE` | File exceeds the default 25 MB indexing limit | Mark as `EXCLUDED`; retain metadata for search. |
| `ERR_EMPTY_FILE` | File contains zero bytes | Complete indexing without content rows. |
| `ERR_PDF_ENCRYPTED` | PDF is password-protected | Record the failure and retain metadata-based discoverability. |
| `ERR_PDF_SCANNED` | PDF contains no extractable text | Mark as `DONE_WITHOUT_TEXT` and explain the OCR requirement. |
| `ERR_DOCX_CORRUPT` | ZIP structure or document XML is malformed | Record the parsing failure and retain indexed metadata. |
| `ERR_IO_TIMEOUT` | External or cloud-backed provider stalls | Retry once with exponential backoff; mark persistent failures as `TEMPORARILY_INACCESSIBLE`. |
| `ERR_OUT_OF_MEMORY` | Memory pressure during document processing | Release resources where possible, record the failure, and skip content indexing. |

- Separates file access state from indexing status to improve diagnostic accuracy.
- Uses metadata-only indexing as a fallback when content extraction is impossible.
- Provides actionable recovery options instead of silently treating inaccessible documents as deleted.

### 7. Debugging & Diagnostic Support

- Adds dedicated diagnostic workflows for inspecting document indexing operations.
- Supports examination of indexing job queues and extracted text.
- Enables testing of file-health state transitions and permission revocation scenarios.
- Facilitates verification of parser behavior, error classification, and fallback mechanisms.



# Version 1.015.3-C

**Build:** `1053108`

## Implementation Summary

Introduced a structured domain context layer for extracting facts from memories, linking entities, inferring relationships, and improving search through knowledge graph traversal.

### 1. Domain Context Layer

**Package:** `dev.builtbyahnaf.memomint.domain.context`

- **`ExtractionResult`**
  - Introduces an immutable domain model for extracted facts, including `ITEM_NAME`, `LOCATION`, `QUANTITY`, `TEMPORAL_MENTION`, and `KEYWORD`.
  - Tracks provenance (`EXTRACTED_BY_RULE`, `USER_ENTERED`), canonical labels, and generator versions.

- **`ItemVocabulary`**
  - Adds a versioned vocabulary (`VOCABULARY_VERSION = 1`) covering physical items across electronics, furniture, personal belongings, and office supplies.
  - Uses O(1) variant-to-canonical mappings for efficient item normalization.

- **`ItemExtractor`**
  - Implements regex-based, word-boundary extraction for `PHYSICAL_ITEM` memories.
  - Prioritizes the longest matching variant and emits canonical `ITEM_NAME` facts.

- **`LocationExtractor`**
  - Uses precompiled regular expressions to identify containers, surfaces, proximity phrases, and room enclosures.
  - Normalizes leading prepositions such as `in`, `on`, and `at`.

- **`QuantityExtractor`**
  - Extracts numeric quantities and container packaging expressions, such as `3 boxes of pens`.
  - Converts number words from `one` through `ten` into digits.

- **`DateTimeExtractor`**
  - Extracts relative dates (`tomorrow`, `next Monday`, `in 3 days`) and absolute dates.
  - Stores extracted temporal information as `TEMPORAL_MENTION` context records.

- **`KeywordExtractor`**
  - Integrates `QueryNormalizer` and `SynonymDictionary` to generate normalized, stopword-filtered, synonym-expanded `KEYWORD` facts.

- **`ContextExtractor`**
  - Orchestrates all five sub-extractors sequentially.
  - Implements fail-safe error isolation to prevent individual extraction failures from disrupting the overall pipeline.

- **`MemoryEntityLink`**
  - Introduces a model linking memories to extracted entities and their semantic roles.

- **`MemoryRelation`**
  - Defines directed edges for representing relationships in the memory knowledge graph.

- **`RelationshipInferrer`**
  - Performs depth-1 relationship inference using shared entities.
  - Generates bidirectional `SAME_LOCATION_AS` and `RELATED_ITEM` relations with a confidence score of `0.70`.

### 2. Persistence Layer

**Package:** `dev.builtbyahnaf.memomint.data.repository`

- **`ContextRepository`**
  - Defines the repository contract for context records, entity resolution, and relationship graph persistence.

- **`ContextRepositoryImpl`**
  - Implements persistence using standard Android `android.database.sqlite` APIs.
  - Integrates with `DatabaseConstants` and `MemoMintDatabase` for SQLite schema and database access.

### 3. Application Services

**Package:** `dev.builtbyahnaf.memomint.application`

- **`ContextService`**
  - Coordinates asynchronous background context extraction, entity linking, and relationship persistence.
  - Processes newly captured or updated memories without blocking the UI thread.

- **`MemoryService`**
  - Adds an optional `ContextService` integration hook.
  - Triggers background context extraction through `createMemorySync()` and `updateMemorySync()`.

### 4. Retrieval & Knowledge Graph Integration

**Package:** `dev.builtbyahnaf.memomint.domain.retrieval`

- **`RelationshipExpander`**
  - Traverses depth-1 knowledge graph relationships.
  - Applies a `0.80` score attenuation factor to graph-derived matches, ensuring directly matched memories retain their ranking advantage over relationship-traversed results.

- **`RelevanceScorer`**
  - Updates the hybrid relevance scoring formula to incorporate relationship-based relevance:

    \[
    \begin{aligned}
    S_{\text{final}} ={}&
    0.45S_{\text{text}} +
    0.25S_{\text{synonym}} +\\
    &0.15S_{\text{meta}} +
    0.10S_{\text{relation}} +\\
    &0.05S_{\text{fuzzy}}
    \end{aligned}
    \]

- **`SearchResult`**
  - Adds `relationScore` to represent relationship-based relevance.
  - Preserves existing constructors for backward compatibility.

- **`SearchService`**
  - Integrates **Stage 3b: Graph Expansion** into the retrieval pipeline.
  - Adds explainable provenance tagging through `Related memory match (graph hop)` to distinguish graph-derived results from direct matches.


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

