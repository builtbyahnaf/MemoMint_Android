# Project MemoMint — Phase 3: Context & Memory Relationships

> **Phase Status:** Planned
> **Roadmap Target:** Phase 3 (Section 21 of `PROJECT_MEMOMINT.md`)
> **Completion Criterion:** A query can retrieve useful information through supported synonyms and
> explicit memory relationships without relying solely on exact phrase matches.
> **Guiding Principles:** Section 13 (Context Extraction), Section 7.2 (Recall), FR-04, FR-07,
> Section 25.2 (Algorithm Documentation), Section 25.7 (AI Coding Agent Guidelines).

---

## 0. Critical Bug Fixed (Pre-Phase 3 Prerequisite)

> **IMPORTANT:** Before Phase 3 work begins, the following critical runtime crash was diagnosed
> and resolved.

### Bug: `SQLiteException: no such module: fts5`

**Symptom:** The app crashes on launch with:
```
Error loading memories: No such module fts5
android.database.sqlite.SQLiteException: no such module: fts5 (code 1)
```

**Root Cause:** `MemoMintDatabase` extended `android.database.sqlite.SQLiteOpenHelper`, which
delegates to the **system-bundled SQLite**. On Android API 30–35, the system SQLite is compiled
by the OEM or AOSP without the FTS5 extension enabled. Executing
`CREATE VIRTUAL TABLE ... USING fts5(...)` on such a device raises the fatal exception above.

**Fix Applied:** Added the `io.requery:sqlite-android` AAR (`3.49.1.0`) as an `implementation`
dependency. This library ships a **pre-built SQLite native binary** with FTS5, FTS4, JSON1, rtree,
and other extensions always compiled in. The requery `SQLiteOpenHelper` and `SQLiteDatabase` classes
are drop-in, API-compatible replacements for the Android framework equivalents.

**Files Changed:**

| File | Change |
|---|---|
| `gradle/libs.versions.toml` | Added `sqliteAndroid = "3.49.1.0"` version + `sqlite-android` library alias |
| `app/build.gradle` | Added `implementation libs.sqlite.android` |
| `data/database/MemoMintDatabase.java` | Imports switched from `android.database.sqlite.*` to `io.requery.android.database.sqlite.*` |
| `data/repository/MemoryRepositoryImpl.java` | Same import switch |
| `data/repository/SearchRepositoryImpl.java` | Same import switch |
| `data/database/MemoMintDatabaseTest.java` | Same import switch |

**Trade-off Accepted:** The AAR adds ~2–3 MB to the APK for the native `.so`. This is acceptable
because:
1. FTS5 is **mandatory** for BM25-ranked full-text search (Section 12.3 of PROJECT_MEMOMINT.md).
2. FTS4 lacks the `bm25()` scoring function used by `SearchRepositoryImpl` and cannot be shimmed.
3. `sqlite-android` is a mature, widely-deployed library with a transparent, inspectable native binary.

---

## 1. Executive Summary & Purpose

Phase 2 gave MemoMint the ability to retrieve memories using keywords, synonyms, and fuzzy
matching. However, the app still stores memories as flat, unrelated text blobs. It cannot answer:

- *"What memories mention the same location as my earphones memory?"*
- *"Which of my notes reference my desk?"*
- *"Find memories related to my USB-C cable note."*

Phase 3 adds two orthogonal but complementary capabilities:

1. **Context Extraction** — Parse stored memories at save-time to extract structured facts: item
   names, locations, quantities, dates, and keyword tags. Store these as typed `context_records`
   and `entity` rows in the existing schema (the tables were designed for this in Phase 1).

2. **Memory Relationships** — Explicitly link related memories using the `memory_relations` table.
   Augment the search pipeline to traverse these links when ranking results.

Both capabilities are **fully offline and deterministic** — no cloud AI, no neural models.
The context extractor uses pattern-based rule engines and regex grammars operating on the device.

---

## 2. Architectural Design & Component Pipeline

### 2.1 Context Extraction Pipeline

When a memory is saved or updated, the `ContextService` runs the extraction pipeline asynchronously
off the UI thread. The pipeline is as follows:

```
Memory saved (original_text, type, id)
   |
   v
ContextExtractor.extractAll(memory)
   |
   |-- 1. ItemExtractor          -> Extracts physical item names (entities)
   |-- 2. LocationExtractor      -> Extracts location phrases (room, container, surface)
   |-- 3. QuantityExtractor      -> Extracts numeric quantities and units
   |-- 4. DateTimeExtractor      -> Extracts relative/absolute date-time mentions
   |-- 5. KeywordExtractor       -> Derives searchable keyword tags from the full text
   |
   v
ExtractionResult rows  -> saved to context_records table
Entity rows            -> upserted into entities table
MemoryEntity rows      -> saved to memory_entities join table
   |
   v
RelationshipInferrer.infer(memory, extractedEntities)
   |
   --> Emits MemoryRelation rows (STORED_AT, RELATED_ITEM, SAME_LOCATION_AS)
       saved to memory_relations table
```

### 2.2 Search Pipeline Augmentation (Graph Hop)

The Phase 2 search pipeline (7 stages) gains one additional stage between stages 3 and 4:

```
3. Candidate Retrieval (FTS5 + Metadata + Fuzzy)  <- Phase 2
   |
   v
3b. Relationship Expansion  <- NEW in Phase 3
   |   (for each candidate memory, follow outbound + inbound memory_relations
   |    edges up to depth=1; add related memories as additional candidates)
   v
4. Score Normalization & Combination  <- Phase 2 formula extended with S_relation
   |
   |   New formula:
   |   S_final = 0.45*S_text + 0.25*S_synonym + 0.15*S_meta + 0.10*S_relation + 0.05*S_fuzzy
   v
5. Relevance vs. Trust Separation  <- Phase 2
   |
   v
6. Explanation Generation  <- Extended for Phase 3
   |   (adds "Related to memory: 'Desk Items'" provenance tag)
   v
7. Ranked Candidate Delivery
```

---

## 3. Detailed Component Specifications

### 3.1 ContextExtractor (`domain/context/ContextExtractor.java`)

**Purpose:** Orchestrates the full context extraction pipeline over a single `Memory` object.

**Algorithm:**
1. Accept the `Memory` and an empty `List<ExtractionResult>` accumulator.
2. Delegate to each specialized extractor (`ItemExtractor`, `LocationExtractor`, etc.) in sequence.
3. Each sub-extractor appends zero or more `ExtractionResult` objects to the accumulator.
4. Return the accumulated list; the caller persists them via `ContextRepository`.

**Why sequential, not parallel?** Each extractor is stateless and fast (pure regex on a single text
field, typically < 1 ms). Parallelism would add thread-management overhead with no measurable
benefit for short memory texts. This can be revisited if profiling shows extraction exceeding 10 ms.

**Invariants:**
- Never modifies the `Memory` object's `originalText` (FR-03).
- Each `ExtractionResult` carries a `Provenance` of `EXTRACTED_BY_RULE` to distinguish it
  from `USER_ENTERED` context — these must never be presented as confirmed fact.
- Failed or empty extractions produce zero rows; they never throw exceptions to the caller.
- On any extraction failure, the original `Memory` row is left intact (NFR-08).

---

### 3.2 ItemExtractor (`domain/context/ItemExtractor.java`)

**Purpose:** Identifies physical item nouns from memory text for `PHYSICAL_ITEM` type memories.

**Algorithm:**
1. Apply the `SynonymDictionary` (Phase 2) to build a candidate item vocabulary from the
   versioned `ItemVocabulary` list.
2. Scan the lowercased text for noun phrases matching known item categories:
   - Electronics: `earphones`, `earbuds`, `cable`, `charger`, `USB-C`, `adapter`, `blower`, etc.
   - Office supplies: `pen`, `pencil`, `notebook`, `stapler`, `tape`, etc.
   - Storage: `box`, `bag`, `case`, `pouch`, etc.
3. For each matched noun phrase, emit an `ExtractionResult` of type `ITEM_NAME` with
   `canonicalLabel` normalized to lowercase singular form (e.g. `"earphones"` -> `"earphone"`).
4. Also emit an `Entity` record (type `ITEM`) and a `MemoryEntity` join row with role `ITEM`.

**Limitation:** This is a vocabulary-driven recognizer, not a general NLP parser.
Items not in the vocabulary will not be extracted. The vocabulary is versioned in
`ItemVocabulary.java` (`VOCABULARY_VERSION` constant) and extended over time.

---

### 3.3 LocationExtractor (`domain/context/LocationExtractor.java`)

**Purpose:** Extracts location phrases representing where a physical item or piece of information is.

**Algorithm:**
1. Match prepositional location patterns using compiled Java `Pattern` (regex):
   - `in the (top|bottom|left|right)? (drawer|shelf|cabinet|wardrobe|closet|box|bag|pocket)`
   - `on the (desk|table|counter|floor|bed|shelf|hook|rack)`
   - `beside|next to|under|above the (bed|desk|door|sink|...)`
   - `in my (room|office|bedroom|living room|kitchen|car|bag|backpack|wardrobe)`
2. Normalize extracted phrase: strip leading prepositions, lowercase, trim whitespace.
3. Emit an `ExtractionResult` of type `LOCATION` and a corresponding `Entity` of type `LOCATION`.
4. If both an item entity and a location entity are extracted from the same memory, the
   `RelationshipInferrer` will also emit a `STORED_AT` relation between them.

**Example:**
- Input: `"My spare earphones are in the left drawer of my bedside table."`
- Item entity: `earphone`
- Location entity: `left drawer bedside table` (normalized from `"in the left drawer of my bedside table"`)
- Relation emitted later by `RelationshipInferrer`: `earphone -> STORED_AT -> left drawer bedside table`

---

### 3.4 QuantityExtractor (`domain/context/QuantityExtractor.java`)

**Purpose:** Captures numeric inventory facts (quantities of physical items).

**Algorithm:**
1. Match patterns: `(\d+|one|two|three|four|five|six|seven|eight|nine|ten) (boxes? of|bags? of|
   bottles? of|pieces? of|rolls? of)?` followed by a noun phrase.
2. Convert word-form numbers to integers (`"three"` -> 3, `"ten"` -> 10).
3. Emit `ExtractionResult` of type `QUANTITY` with `content` = `"3 boxes of pens"`.
4. The quantity is stored as a searchable keyword string, not a typed integer, to keep the
   schema simple for Phase 3. Structured quantity querying can be added in a later phase.

---

### 3.5 DateTimeExtractor (`domain/context/DateTimeExtractor.java`)

**Purpose:** Extracts temporal mentions from memory text. These will be used for freshness and
scheduling in Phase 5 (Trust & Freshness).

**Algorithm:**
1. Match relative date expressions: `today`, `tomorrow`, `yesterday`, `next (Monday|week|month)`,
   `in (N) days`, `every (day|week|month)`.
2. Match absolute patterns: `(January|Jan|...) (1-31)`, `(MM/DD/YYYY)`, `(DD/MM/YYYY)`.
3. For each match, store the raw text span and (where resolvable at extraction time) the
   UTC epoch millisecond timestamp computed relative to the memory's `created_at`.
4. Emit as `TEMPORAL_MENTION` context records.

**Note:** Temporal records do not affect retrieval scoring in Phase 3 — their integration with
freshness indicators is planned for Phase 5.

---

### 3.6 KeywordExtractor (`domain/context/KeywordExtractor.java`)

**Purpose:** Derives high-quality searchable keyword tags from the full memory text. These
supplement the FTS5 index with normalized, deduplicated, stopword-free, synonym-expanded terms.

**Algorithm:**
1. Delegate to `QueryNormalizer.tokenize()` (Phase 2) to get clean, stopword-filtered tokens.
2. Expand each token through `SynonymDictionary.getCluster(token)` to obtain synonym variants.
3. Deduplicate all tokens across the original set and all synonym expansions.
4. Emit each unique keyword as an `ExtractionResult` of type `KEYWORD`.

**Why store keywords when FTS5 already indexes the text?**

FTS5 indexes the raw `original_text` column and matches search queries against it. If the memory
text contains `"earphones"` and the user searches for `"earbuds"`, FTS5 alone will not find it
because the token `"earbuds"` is not in the index. The synonym expansion in `SearchService` covers
this at query time, but keyword extraction at write-time provides a second, persistent coverage layer:
the keyword `"earbuds"` is stored as a `context_records` row for the memory, making it discoverable
even through metadata-only queries that don't go through FTS5.

---

### 3.7 ItemVocabulary (`domain/context/ItemVocabulary.java`)

**Purpose:** Centralized, versioned list of known physical item nouns and their canonical forms.
Shared by `ItemExtractor` and `SynonymDictionary`.

**Design:**
- Immutable static set, loaded once at class initialization.
- Each entry: `(canonical_label, Set<variant_labels>)`.
- Version constant `VOCABULARY_VERSION` (integer) incremented on each vocabulary update.
- Kept conservative: only add items with high confidence of being physical objects.
  Do not add ambiguous nouns (e.g. `"table"` alone could be a database table).

---

### 3.8 RelationshipInferrer (`domain/context/RelationshipInferrer.java`)

**Purpose:** Creates explicit `memory_relations` rows linking related memories based on shared
entities, especially shared location entities.

**Algorithm (Depth-1 Shared-Entity Inference):**
1. After context extraction for memory M produces entities `E1, E2, ..., En`:
2. For each entity `Ei`, query `memory_entities` for all other memories `M'` that also link
   to an entity with the same `canonical_label` as `Ei`.
3. For each discovered pair `(M, M')`:
   - If `Ei` is type `LOCATION`: emit `M SAME_LOCATION_AS M'` (bidirectional).
   - If `Ei` is type `ITEM`: emit `M RELATED_ITEM M'` (bidirectional).
4. Before inserting, check for existing relation rows to prevent duplicates.
5. Set `provenance = INFERRED` and `confidence = 0.70` on all inferred relations.
   Only `USER_ENTERED` relations should carry `confidence = 1.0`.

**Why depth = 1?** Transitive graph traversal (following relations of relations) can produce
exponentially many candidate relations. Depth-1 (direct shared entity) is conservative and
verifiable. Deeper traversal should be enabled in a later phase after benchmarking shows it
improves retrieval quality without introducing false positives.

---

### 3.9 ContextRepository (`data/repository/ContextRepository.java`)

**Interface:**
```java
// Context records (extraction results)
void saveContextRecords(List<ExtractionResult> results, String memoryId);
List<ContextRecord> getContextForMemory(String memoryId);
void deleteContextForMemory(String memoryId);

// Entity vocabulary
void upsertEntities(List<Entity> entities);
List<Entity> findEntitiesByCanonicalLabel(String canonicalLabel);

// Memory-entity join
void saveMemoryEntities(List<MemoryEntityLink> links);
List<Memory> findMemoriesByEntityLabel(String canonicalLabel);

// Memory relationships
void saveMemoryRelations(List<MemoryRelation> relations);
boolean relationExists(String sourceId, String targetId, String relationType);
List<MemoryRelation> getRelationsForMemory(String memoryId);
void deleteRelationsForMemory(String memoryId);
```

**Implementation** (`ContextRepositoryImpl.java`) uses raw SQLite via `MemoMintDatabase`
— consistent with the Phase 1–2 pattern; no ORM.

---

### 3.10 ContextService (`application/ContextService.java`)

**Purpose:** Orchestrates the full context extraction pipeline asynchronously off the UI thread.
Called by `MemoryService` after every `createMemory` or `updateMemory` operation.

**Algorithm & Threading Model:**
1. Accept the just-saved `Memory` object.
2. Submit a background `Runnable` to the shared `backgroundExecutor`.
3. Inside the background task:
   a. Run `ContextExtractor.extractAll(memory)` -> `List<ExtractionResult>`.
   b. Persist `ExtractionResult` objects via `ContextRepository.saveContextRecords(...)`.
   c. Upsert entity rows via `ContextRepository.upsertEntities(...)`.
   d. Save `MemoryEntityLink` rows via `ContextRepository.saveMemoryEntities(...)`.
   e. Run `RelationshipInferrer.infer(memory, entities)` -> `List<MemoryRelation>`.
   f. Persist relation rows via `ContextRepository.saveMemoryRelations(...)`.
4. On any failure: log the error but **do not corrupt the original Memory** (FR-03, NFR-08).
   Context extraction is best-effort; the memory row is authoritative.

**On memory update:** Before re-extracting, delete the previous context records for the
memory ID (`ContextRepository.deleteContextForMemory(id)`) to prevent stale context accumulation.
Relations inferred from the old context are also deleted and rebuilt from the new extraction.

---

### 3.11 RelationshipExpander (`domain/retrieval/RelationshipExpander.java`)

**Purpose:** Adds relation-traversal as a new scoring signal (`S_relation`) in the retrieval pipeline.
Integrated into `SearchService` between candidate retrieval and score normalization.

**Algorithm (Graph Hop — Depth 1):**
1. Accept the current candidate set (FTS5 + metadata + fuzzy hits) as a `Map<String, SearchCandidate>`.
2. For each candidate memory ID, query `ContextRepository.getRelationsForMemory(id)`.
3. For each relation, load the linked memory and add it to the candidate pool if not already present.
4. Score each relation-expanded candidate:
   `S_relation = relation.confidence * 0.80`
   The `0.80` dampening ensures directly-matched memories always rank above relation-traversed ones
   (since `S_relation` cannot reach 1.0 for inferred relations with confidence <= 0.70).
5. Return the augmented candidate pool with `S_relation` populated.

**Updated Scoring Formula:**
```
S_final = 0.45 * S_text  +  0.25 * S_synonym  +  0.15 * S_meta  +  0.10 * S_relation  +  0.05 * S_fuzzy
```

*Weight rationale:* `S_text` decreases from 0.50 to 0.45 and `S_synonym` from 0.30 to 0.25 to
accommodate `S_relation = 0.10`. The relationship signal is intentionally a *supporting* weight,
not a dominant one, since inferred relations carry inherent uncertainty.

---

### 3.12 Search Explanation Extension

The `SearchResult.explanation` list gains Phase 3 provenance tags:
- `"Context keyword match: 'earphone'"`
- `"Related memory: 'Desk Items' (shared location: 'desk drawer')"`
- `"Inferred relation: SAME_LOCATION_AS 'bedside table'"`

These tags must be generated from actual extraction and relation data — never fabricated.

---

## 4. End-to-End Walkthrough Example

### Stored Memories:
**Memory A** (id = `a1`):
- Title: `"Earphones"` | Text: `"My spare earphones are in the left drawer of my bedside table."`
- Extracted entities: `earphone` (ITEM), `left drawer bedside table` (LOCATION)
- Relation to B: `SAME_LOCATION_AS` (shared `bedside table` entity, confidence 0.70)

**Memory B** (id = `b1`):
- Title: `"Bedside Table"` | Text: `"I keep my notebook and charger in the top drawer of my bedside table."`
- Extracted entities: `notebook` (ITEM), `charger` (ITEM), `top drawer bedside table` (LOCATION)

### User Query: `"What's in my bedside table?"`

1. **Normalization:** tokens `["bedside", "table"]`
2. **FTS5 Retrieval:** Both A and B match (both contain "bedside table").
   A: `S_text = 0.72`, B: `S_text = 0.78`
3. **Relationship Expansion:** A and B are already candidates; no new additions.
4. **Final Scores** (S_synonym=0, S_meta=0.6, S_relation=0, S_fuzzy=0):
   - A: `0.45*0.72 + 0.15*0.6 = 0.324 + 0.090 = 0.414`
   - B: `0.45*0.78 + 0.15*0.6 = 0.351 + 0.090 = 0.441`
5. **Output:** Memory B ranked first (notebook + charger), Memory A second (earphones).
   Both show explanation: `"Matched keywords 'bedside', 'table'"`.

---

## 5. File Inventory to Implement

| Package | Class / File | Purpose |
|---|---|---|
| `domain/context` | `ExtractionResult.java` | Domain model: a single extracted fact (item, location, keyword, quantity, temporal) |
| `domain/context` | `ContextExtractor.java` | Orchestrates all sub-extractors in sequence |
| `domain/context` | `ItemExtractor.java` | Extracts physical item entity names using vocabulary |
| `domain/context` | `ItemVocabulary.java` | Versioned vocabulary of known physical item nouns |
| `domain/context` | `LocationExtractor.java` | Extracts location phrases using regex grammars |
| `domain/context` | `QuantityExtractor.java` | Extracts numeric quantity facts with word-numeral conversion |
| `domain/context` | `DateTimeExtractor.java` | Extracts temporal mentions (relative + absolute) |
| `domain/context` | `KeywordExtractor.java` | Derives normalized, synonym-expanded keyword tags |
| `domain/context` | `RelationshipInferrer.java` | Infers memory-memory relations from shared entities (depth-1) |
| `data/repository` | `ContextRepository.java` | Interface for context record, entity, and relation persistence |
| `data/repository` | `ContextRepositoryImpl.java` | SQLite implementation (raw SQL, no ORM) |
| `application` | `ContextService.java` | Asynchronous orchestrator: extracts context after every memory save/update |
| `application` | `MemoryService.java` | Updated to trigger `ContextService` after create and update operations |
| `domain/retrieval` | `RelationshipExpander.java` | Depth-1 graph hop: adds related-memory candidates to search results |
| `domain/retrieval` | `RelevanceScorer.java` | Updated 5-component scoring formula with `S_relation` |
| `domain/retrieval` | `SearchResult.java` | Extended explanation tags for context and relation matches |
| `src/test/java` | `ContextExtractorTest.java` | Unit tests for all sub-extractors with labeled examples |
| `src/test/java` | `RelationshipInferrerTest.java` | Unit tests: shared-entity detection, deduplication, depth-1 boundary |
| `src/test/java` | `ContextRepositoryTest.java` | Integration tests: persist and retrieve context records and relations |
| `src/test/java` | `RetrievalBenchmarkTest.java` | Extended Phase 3 benchmark cases (context match, relation traversal) |

---

## 6. Schema Compatibility

**Phase 3 does NOT require a database schema version bump.**

All tables needed for Phase 3 already exist from Phase 1:
- `context_records` — stores extraction results as typed rows.
- `entities` — stores canonical entity labels and types.
- `memory_entities` — joins memories to entities with roles and provenance.
- `memory_relations` — stores explicit and inferred memory-memory links.

No `onUpgrade` migration block is needed. All Phase 3 writes go through the existing schema
using the existing `ContextRepository`.

Note: If the extraction algorithm or vocabulary schema changes significantly in future phases
(e.g. adding a per-entity confidence column), a schema bump will be warranted at that time.
Do not bump prematurely.

---

## 7. Extraction Quality Principles

Context extraction is rule-based and operates on a curated vocabulary. The following principles
ensure the system remains honest about what it knows:

- **Provenance is always `EXTRACTED_BY_RULE`** — never presented to the user as confirmed fact.
- **No extraction can overwrite or contradict `originalText`** (FR-03 invariant must hold after every
  Phase 3 change; verified by a dedicated regression test).
- **Unknown items are silently skipped** — the extractor never fabricates entities.
- **`ItemVocabulary.VOCABULARY_VERSION`** tracks vocabulary evolution so extraction changes are traceable.
- **`generation_status` column** in `context_records` records whether extraction succeeded, failed,
  or is pending — enabling safe recovery after interrupted background jobs (NFR-03).
- **Confidence values** on inferred relations are set conservatively (0.70). Future phases may
  calibrate confidence based on measured extraction recall.

---

## 8. Labeled Evaluation Benchmark Extension

The existing `RetrievalBenchmarkTest` (Phase 2, 19 labeled queries) will be extended with Phase 3
test cases. New test case categories:

### 8.1 Context Keyword Match Queries
Queries whose tokens don't appear verbatim in memory text but match extracted keyword or entity rows.

Example:
- Memory: `"My dust blower is in the bottom drawer beside my bed."`
- Query: `"keyboard cleaning tool"` → should match via synonym keyword `"keyboard cleaner"` extracted
  from the `SynonymDictionary` cluster for `"dust blower"`.

### 8.2 Relationship Traversal Queries
Queries that retrieve a memory primarily because it is linked via `memory_relations` to a
directly-matched memory.

Example:
- Memory A: `"USB-C cable in my desk drawer."` (directly matched by FTS5)
- Memory B: `"My desk is in my home office."` (linked to A via shared `desk` location entity)
- Query: `"home office cable"` → A ranked first via FTS5, B appears as related via `S_relation`.

### 8.3 Multi-Entity Queries
Queries combining item + location phrases that should both match extracted entities.

Example:
- Memory: `"Spare earphones in the left drawer of my bedside table."`
- Query: `"earbuds bedside"` → direct match on `earphone` entity (via synonym) + `bedside table` entity.

### 8.4 Negative Context Queries
Queries where extracted entities do not match any stored memory. Engine must return empty or very
low confidence results, not fabricate an answer.

Example:
- Query: `"swimming goggles bathroom cabinet"` — if no such memory exists, return empty results.

### 8.5 Updated Target Metrics

| Metric | Phase 2 Achieved | Phase 3 Target |
|---|---|---|
| Recall@1 | 84.21% | >= 82% (larger, harder test set with relation cases) |
| Recall@3 | 94.74% | >= 91% |
| MRR | 0.895 | >= 0.83 |
| Latency | 3.46 ms | < 5 ms (including relation expansion step) |
| Context extraction entity coverage | N/A (new metric) | >= 75% of PHYSICAL_ITEM memories yield >= 1 entity |
| Negative query guard | 100% | 100% (no regression allowed) |

---

## 9. Phase 3 Completion Criteria Checklist

- [ ] `ExtractionResult` domain model defined with type, content, provenance, and canonical label.
- [ ] `ContextExtractor` orchestrator implemented and unit-tested.
- [ ] `ItemExtractor` with versioned `ItemVocabulary` implemented and tested.
- [ ] `LocationExtractor` with regex grammar for room/container/surface patterns implemented.
- [ ] `QuantityExtractor` with word-numeral conversion (`one`-`ten`) implemented.
- [ ] `DateTimeExtractor` with relative and absolute date pattern matching implemented.
- [ ] `KeywordExtractor` using Phase 2 `QueryNormalizer` + `SynonymDictionary` implemented.
- [ ] `RelationshipInferrer` with depth-1 shared-entity relation detection implemented and tested.
- [ ] `ContextRepository` interface and `ContextRepositoryImpl` (raw SQLite) implemented.
- [ ] `ContextService` wiring `ContextExtractor` asynchronously after every memory save and update.
- [ ] `MemoryService.createMemory` and `updateMemory` trigger `ContextService` after persistence.
- [ ] On memory update: old context records and inferred relations cleared before re-extraction.
- [ ] `RelationshipExpander` depth-1 graph hop integrated into `SearchService` pipeline.
- [ ] `RelevanceScorer` updated with new 5-component formula (weights verified against benchmark).
- [ ] `SearchResult.explanation` extended with context-match and relation-match provenance tags.
- [ ] `ContextExtractorTest` with >= 15 labeled extraction examples covering all 5 sub-extractors.
- [ ] `RelationshipInferrerTest` covering: shared-entity detection, deduplication, depth-1 boundary.
- [ ] `ContextRepositoryTest` covering: save, retrieve, cascade deletion with memory hard-delete.
- [ ] `RetrievalBenchmarkTest` extended with >= 10 Phase 3 test cases (context + relation categories).
- [ ] All existing Phase 2 benchmark Recall@1, Recall@3, MRR targets maintained or exceeded.
- [ ] No `originalText` modified by context extraction (FR-03 regression check test added).
- [ ] Context extraction failure leaves memory row intact and logs error without crash (NFR-08 check).
