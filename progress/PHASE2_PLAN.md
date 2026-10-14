# Project MemoMint — Phase 2: Retrieval Foundation Plan

> **Phase Status:** Planned  
> **Roadmap Target:** Phase 2 (Section 21 of `PROJECT_MEMOMINT.md`)  
> **Completion Criterion:** A comprehensive automated test suite demonstrates that relevant memories can be retrieved through both direct keyword queries and selected indirect/synonym queries entirely offline.  
> **Guiding Principles:** Section 12 (Retrieval Engine), Section 25.6 (Implementation Workflow), and Section 25.7 (AI Coding Agent Guidelines).

---

## 1. Executive Summary & Purpose

In conventional note-taking and file applications, retrieval relies heavily on users remembering the exact keywords, filenames, or verbatim phrasing of a note. Human memory, however, recalls **concepts, functions, and contextual associations** rather than verbatim strings (Section 2.2).

Phase 2 builds the core deterministic **Retrieval Engine** for MemoMint. It enables users to search their memories using natural phrasing, partial recollections, common synonyms, and minor typographical errors without requiring cloud infrastructure, external APIs, or heavy neural networks.

---

## 2. Architectural Design & Component Pipeline

The retrieval pipeline processes incoming queries through seven sequential, deterministic stages:

```
User Query (e.g., "Where are my backup earbuds?")
   │
   ▼
1. Query Normalization & Tokenization
   │   (lowercasing, unicode normalization, punctuation stripping, stopword tagging)
   ▼
2. Synonym & Concept Expansion
   │   (maps "earbuds" ↔ "earphones", "backup" ↔ "spare", "closet" ↔ "wardrobe")
   ▼
3. Candidate Retrieval
   ├── A. SQLite Full-Text Index (FTS5 / FTS4 match on original text & title)
   ├── B. Metadata Query (matching memory type, source, timestamps)
   └── C. Fuzzy Matching (Levenshtein distance for typos on non-matched terms)
   │
   ▼
4. Score Normalization & Combination
   │   (converts raw BM25, term frequencies, and edit distances into [0.0, 1.0])
   │   Formula: S_final = 0.50*S_text + 0.30*S_synonym + 0.15*S_meta + 0.05*S_fuzzy
   ▼
5. Relevance vs. Trust Separation
   │   (computes separate Freshness / Confirmation Confidence without skewing semantic rank)
   ▼
6. Explanation Generation
   │   (constructs verifiable provenance tags: e.g. "Matched synonym 'earphones'")
   ▼
7. Ranked Candidate Delivery
   (sorted descending by S_final, presented to UI / Presentation layer)
```

---

## 3. Detailed Component Specifications

### 3.1 Query Normalizer (`domain/retrieval/QueryNormalizer.java`)
- **Purpose:** Standardizes raw user text so queries and stored memories share a uniform representation.
- **Algorithm:**
  1. Strip leading and trailing whitespace.
  2. Normalize Unicode characters to NFKC form (collapsing accents, ligatures).
  3. Convert to lowercase according to the device's default locale.
  4. Strip non-alphanumeric punctuation while preserving intra-word hyphens (e.g. `USB-C` stays `usb-c`).
  5. Split into individual tokens on whitespace boundaries.
  6. Filter or tag high-frequency stopwords (e.g. "where", "did", "i", "put", "the") so they do not artificially dominate relevance scoring.
- **Example:**
  `"Where did I put my spare USB-C cable?"`  
  → Tokens: `["spare", "usb-c", "cable"]` (with query intent flagged as `LOCATION_LOOKUP`).

### 3.2 Local Synonym & Concept Dictionary (`domain/retrieval/SynonymDictionary.java`)
- **Purpose:** Bridges vocabulary mismatch between what the user typed when capturing a memory and what they search later (Section 12.2).
- **Design & Invariants:**
  - Fully offline, version-controlled lookup table.
  - Bidirectional equivalence clusters:
    - `earbuds` ↔ `earphones` ↔ `headphones`
    - `cable` ↔ `cord` ↔ `wire`
    - `wardrobe` ↔ `closet` ↔ `cupboard`
    - `dust blower` ↔ `air duster` ↔ `keyboard cleaner`
    - `spare` ↔ `backup` ↔ `extra`
    - `drawer` ↔ `desk drawer`
  - Versioned schema: includes a `DICTIONARY_VERSION` constant to track vocabulary updates.
  - Tradeoff: Kept deterministic and conservative. Does NOT map distant concepts (e.g., `laptop` is not unconditionally synonymous with `notebook`) to prevent semantic drift.

### 3.3 SQLite Full-Text Search (FTS) Integration (`data/database/FtsTableManager.java`)
- **Purpose:** High-performance inverted index for fast keyword matching and BM25 relevance scoring.
- **Schema Migration (Version 1 → Version 2):**
  - Create virtual table `memories_fts` using `FTS5` (or fallback to `FTS4` where SQLite engine requires):
    ```sql
    CREATE VIRTUAL TABLE IF NOT EXISTS memories_fts USING fts5(
        id UNINDEXED,
        title,
        original_text,
        tokenize = 'unicode61 remove_diacritics 2'
    );
    ```
  - Triggers to keep `memories_fts` synchronized automatically on `INSERT`, `UPDATE`, and `DELETE` on `memories`.
- **BM25 Scoring:**
  - Standard BM25 balances Term Frequency ($TF$) and Inverse Document Frequency ($IDF$), ensuring rare query words contribute more weight than common words.

### 3.4 Fuzzy Matcher (`domain/retrieval/FuzzyMatcher.java`)
- **Purpose:** Handles misspellings, typos, and minor character transpositions (e.g. "earfone" -> "earphone", "bluer" -> "blower").
- **Algorithm:**
  - Normalized Levenshtein / Damerau-Levenshtein distance:
    $$\text{similarity}(s_1, s_2) = 1.0 - \frac{\text{edit\_distance}(s_1, s_2)}{\max(\text{len}(s_1), \text{len}(s_2))}$$
  - Applied only to candidate terms with similarity $\ge 0.75$ and minimum word length $\ge 4$ characters to prevent false-positive explosion on short words.

### 3.5 Hybrid Scoring & Ranking Model (`domain/retrieval/RelevanceScorer.java`)
- **Purpose:** Combines disparate matching signals into a normalized [0.0, 1.0] score.
- **Scoring Function:**
  $$\text{Score}_{\text{final}} = 0.50 \cdot S_{\text{text}} + 0.30 \cdot S_{\text{synonym}} + 0.15 \cdot S_{\text{meta}} + 0.05 \cdot S_{\text{fuzzy}}$$
- **Normalization Strategy:**
  - BM25 yields unbounded positive scores; map via hyperbolic tangent or min-max scaling:
    $$S_{\text{text}} = \frac{2}{\pi} \arctan(\text{raw\_bm25})$$
  - Each component is strictly bounded in $[0.0, 1.0]$ before weight multiplication.

### 3.6 Explainable Search Results (`domain/retrieval/SearchResult.java`)
- **Purpose:** Upholds Section 12.5 (Search Explanations). The engine must never fabricate reasons.
- **Attributes:**
  - `memory`: The retrieved `Memory` instance.
  - `score`: Combined relevance score $[0.0, 1.0]$.
  - `explanation`: List of explicit match signals:
    - `"Exact match on 'cable'"`
    - `"Synonym match: 'earbuds' matched 'earphones'"`
    - `"Fuzzy match: 'erfone' matched 'earphone' (88% similarity)"`
    - `"Matched physical item category"`
  - `freshnessStatus`: Unconfirmed, Confirmed (with relative timestamp), or Stale.

---

## 4. End-to-End Walkthrough Example

### Input Memory in Database:
- **Title:** `"Desk Items"`
- **Original Text:** `"My spare USB-C cable is in the second drawer of my desk."`
- **Type:** `PHYSICAL_ITEM`
- **Created:** 3 days ago | **Confirmed:** 1 day ago

### User Query:
> `"Where did I put the backup cable for my desk?"`

1. **Normalization:**
   - Stopwords tagged: `["where", "did", "i", "put", "the", "for", "my"]`
   - Content tokens: `["backup", "cable", "desk"]`
2. **Expansion:**
   - `"backup"` expands to synonym set `{"backup", "spare", "extra"}`
   - `"cable"` expands to `{"cable", "cord", "wire"}`
   - `"desk"` expands to `{"desk", "workstation"}`
3. **Retrieval & Scoring:**
   - `memories_fts` matches `"cable"` (exact, $S_{\text{text}} = 0.82$) and `"desk"` (exact, $S_{\text{text}} = 0.78$).
   - Synonym module matches `"backup"` to stored `"spare"` ($S_{\text{synonym}} = 0.90$).
   - Metadata matches `PHYSICAL_ITEM` type ($S_{\text{meta}} = 0.70$).
   - Fuzzy score: No typo correction needed ($S_{\text{fuzzy}} = 0.0$).
4. **Final Combined Score:**
   $$\text{Score} = 0.50 \cdot (0.80) + 0.30 \cdot (0.90) + 0.15 \cdot (0.70) + 0.05 \cdot (0.0) = 0.40 + 0.27 + 0.105 = 0.775$$
5. **Output Presentation:**
   - Top Result: `"My spare USB-C cable is in the second drawer of my desk."`
   - Badges: `[PHYSICAL ITEM]`, `[Confirmed yesterday]`
   - Explanation: `"Matched keywords 'cable', 'desk' · Matched synonym 'spare' from 'backup'"`

---

## 5. File Inventory to Implement

| Package | Class / File | Purpose |
|---|---|---|
| `domain/retrieval` | `QueryNormalizer.java` | Text normalization, tokenization, stopword extraction |
| `domain/retrieval` | `SynonymDictionary.java` | Local bidirectional concept vocabulary |
| `domain/retrieval` | `FuzzyMatcher.java` | Damerau-Levenshtein string distance utility |
| `domain/retrieval` | `SearchResult.java` | Domain model for scored results with explanation tags |
| `domain/retrieval` | `RelevanceScorer.java` | Signal weight combiner and score normalizer |
| `data/database` | `DatabaseConstants.java` | Schema v2 update: FTS virtual table & trigger DDL |
| `data/database` | `MemoMintDatabase.java` | `onUpgrade` v1 → v2 applying FTS tables and triggers |
| `data/repository` | `SearchRepository.java` | Interface for candidate query execution |
| `data/repository` | `SearchRepositoryImpl.java` | SQLite FTS and metadata search implementation |
| `application` | `SearchService.java` | Orchestrates retrieval pipeline off UI thread |
| `ui/search` | `SearchAdapter.java` | RecyclerView adapter rendering matches with highlighted terms & badges |
| `ui` | `MainActivity.java` | Search bar integration in AppBar with real-time or submit search |
| `src/test/java` | `QueryNormalizerTest.java` | Unit tests for punctuation, diacritics, and stopword tagging |
| `src/test/java` | `SynonymDictionaryTest.java` | Unit tests for bidirectional cluster expansion |
| `src/test/java` | `FuzzyMatcherTest.java` | Unit tests for typo tolerance and edit distance thresholds |
| `src/test/java` | `RetrievalBenchmarkTest.java` | Labeled benchmark evaluating Recall@1, Recall@3, Recall@5, MRR |

---

## 6. Labeled Evaluation Benchmark (`RetrievalBenchmarkTest`)

Section 12.6 requires measuring retrieval on a version-controlled benchmark fixture. The test dataset will contain:
1. **Direct Queries:** Query words exist directly in memory text.
2. **Indirect Synonym Queries:** Key concepts use alternate terminology (`backup` → `spare`, `earbuds` → `earphones`, `wardrobe` → `closet`).
3. **Typo Queries:** Intentional single and double character edits (`keybord` → `keyboard`, `drwer` → `drawer`).
4. **Negative / Distractor Queries:** Queries where no relevant memory exists; engine must return zero or low-confidence matches without hallucinating.

**Target Evaluation Metrics:**
- **Recall@1:** $\ge 70\%$ on benchmark query set.
- **Recall@3:** $\ge 85\%$.
- **Mean Reciprocal Rank (MRR):** $\ge 0.75$.
- **Latency:** Execution under 50ms for local index of 1,000 memories on JVM.

---

## 7. Phase 2 Completion Criteria Checklist

- [ ] SQLite schema version 2 created with full-text search table (`memories_fts`) and synchronization triggers.
- [ ] Safe `onUpgrade` migration from schema v1 to v2 implemented and tested.
- [ ] Pure JVM `QueryNormalizer` implemented with Unicode folding and stopword tagging.
- [ ] Curated, versioned `SynonymDictionary` implemented.
- [ ] `FuzzyMatcher` implemented with Levenshtein metrics.
- [ ] `RelevanceScorer` combining FTS, synonym, metadata, and fuzzy signals.
- [ ] Search explanation module generating transparent reason strings.
- [ ] `SearchService` providing asynchronous search APIs to presentation layer.
- [ ] Search bar integrated into `MainActivity` with real-time/instant filtering.
- [ ] Benchmark test suite verifying Recall@1, Recall@3, and MRR.
