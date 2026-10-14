# Project MemoMint — Phase 1: Foundation Plan & Summary

> **Phase Status:** Completed  
> **Completion Criterion:** Users can create and manage ordinary memories entirely offline.  
> **Primary Technology:** Java 11, SQLite (via `SQLiteOpenHelper` with WAL and foreign key cascade), Material 3.

---

## 1. Overview & Objectives

Phase 1 established the local-first architectural skeleton of Project MemoMint. It delivered:
1. Strict modular layer separation (Domain, Persistence, Application, Presentation).
2. Domain entities and immutable contracts (20 models and enums).
3. SQLite relational schema with 10 tables, explicit indexes, foreign key cascade deletion, and version-tracked migration infrastructure.
4. Application service layer enforcing input validation, timestamp consistency, and thread-safe background execution.
5. Material 3 Day/Night adaptive user interface enabling offline memory creation, editing, location confirmation, archiving, and deletion.
6. Three test suites covering model invariants, SQLite persistence & cascade rules, and application business logic.

---

## 2. Package Architecture

```
dev.builtbyahnaf.memomint/
├── domain/model/
│   ├── Memory.java              — Core entity; strictly protects originalText from automated overwrite
│   ├── MemoryType.java          — Enum: NOTE, PHYSICAL_ITEM, LOCATION, FILE_REFERENCE, DOCUMENT_CONTEXT, REMINDER
│   ├── MemoryStatus.java        — Enum: ACTIVE, ARCHIVED, DELETED
│   ├── SourceType.java          — Enum: TYPED, VOICE, SHARED, IMPORTED
│   ├── ContextRecord.java       — Derived searchable context record
│   ├── ContextType.java         — Enum: SUMMARY, KEYWORDS, ENTITIES, DOCUMENT_CONTENT
│   ├── GenerationStatus.java    — Enum: PENDING, DONE, FAILED
│   ├── Entity.java              — Normalized concept entity
│   ├── EntityType.java          — Enum: ITEM, LOCATION, PERSON, TOPIC, OTHER
│   ├── MemoryEntity.java        — Join record (Memory ↔ Entity) with role & confidence
│   ├── EntityRole.java          — Enum: SUBJECT, LOCATION, OBJECT, TOPIC
│   ├── MemoryRelation.java      — Directed relation edge (STORED_AT, REFERENCES_FILE, SUPERSEDES, etc.)
│   ├── RelationType.java        — Central relationship type definitions
│   ├── Provenance.java          — Enum: USER_ENTERED, VOICE_TRANSCRIBED, EXTRACTED, INFERRED, CONFIRMED
│   ├── FileReference.java       — Tracked document/file record
│   ├── AccessState.java         — Enum: ACCESSIBLE, NOT_YET_VERIFIED, MISSING_OR_UNAVAILABLE, PERMISSION_REVOKED
│   ├── IndexingStatus.java      — Enum: PENDING, INDEXED, FAILED, EXCLUDED
│   ├── Reminder.java            — Scheduled reminder linked to memory with timezone awareness
│   ├── ReminderStatus.java      — Enum: ACTIVE, COMPLETED, CANCELLED, SNOOZED
│   ├── ConfirmationPolicy.java  — Freshness check schedule for physical items
│   └── IndexJob.java            — Resumable document indexing task record
│
├── data/
│   ├── database/
│   │   ├── DatabaseConstants.java   — Central table & column definitions, indexes, DDL SQL
│   │   └── MemoMintDatabase.java    — SQLiteOpenHelper, WAL mode, foreign keys, migrations
│   └── repository/
│       ├── MemoryRepository.java     — Repository interface with explicit integrity contracts
│       └── MemoryRepositoryImpl.java — SQLite implementation with transactions and safe cursor lifecycles
│
├── application/
│   └── MemoryService.java       — Coordinates CRUD, validation, threading, and UI callbacks
│
└── ui/
    ├── memory/
    │   └── MemoryAdapter.java   — RecyclerView adapter with DiffUtil, day/night badges, and action listeners
    └── MainActivity.java        — Main screen supporting creation, editing, confirmation, and deletion
```

---

## 3. Database Schema (Version 1)

All tables use UUID string primary keys and UTC epoch millisecond timestamps. Foreign key constraints are enforced on connection opening (`PRAGMA foreign_keys = ON`).

| Table | Purpose | Cascade Behavior |
|---|---|---|
| `memories` | Authoritative memory records | Root parent table |
| `context_records` | Derived summaries and keyword sets | Cascade-deleted with parent memory |
| `entities` | Canonical vocabulary of concepts | Preserved across memory deletions |
| `memory_entities` | Memory ↔ Entity associations with roles | Cascade-deleted with parent memory |
| `memory_relations` | Directed graph edges between memories/entities | Polled by relation traversal queries |
| `file_references` | Indexed document paths & content fingerprints | Retained for health audits |
| `reminders` | Scheduled notifications | Cascade-deleted with linked memory |
| `confirmation_policies` | Periodic freshness-check intervals | Cascade-deleted with parent memory |
| `index_jobs` | Resumable background indexing operations | Independent audit log |

---

## 4. Key Design Invariants (FR-03, FR-14, NFR-08)

1. **Original-Content Preservation (FR-03):** `original_text` represents the user's unadulterated words. Derived context generation (Phase 3/4) must never overwrite this column.
2. **Deterministic Cascade Deletion (FR-14):** Hard-deleting a memory automatically purges dependent context records, reminders, and confirmation policies without orphan rows.
3. **Shared Entity Vocabulary:** Deleting a memory never deletes rows from `entities`, preserving canonical terms for other memories.
4. **Day/Night Theme Conformance:** Zero hardcoded colors in layouts. Color roles use Material 3 semantic attributes and DayNight resource qualifiers (`res/values/colors.xml` and `res/values-night/colors.xml`).
5. **Edge-to-Edge Padding:** Status bar insets are consumed by the AppBarLayout and navigation bar insets by the list padding/FAB margin, preventing double-padding or content clipping.

---

## 5. Verification Checklist

- [x] Core domain model classes created with strict invariants and detailed Javadoc.
- [x] Database constants centralized in `DatabaseConstants.java`.
- [x] Database helper configured with WAL, foreign keys, and migration switch framework.
- [x] Repository implementation using parameterized statements and transaction rollback.
- [x] Application service providing validation, default assignments, and async background execution.
- [x] Unit test suites implemented:
  - `MemoryTest.java`: Model rules and mutation invariants.
  - `MemoMintDatabaseTest.java`: SQLite schema, foreign keys, cascade delete, rollback.
  - `MemoryServiceTest.java`: Service validation and state machine testing with fake repository.
- [x] Material 3 UI supporting offline create, view, edit, confirm, archive, and delete flows.
- [x] Adaptive Day/Night theme implemented and verified.
