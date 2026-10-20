# Project MemoMint — Phase 5: Trust and Freshness

> **Phase Status:** Complete (164 Tests Passing)  
> **Roadmap Target:** Phase 5 (Section 21 of `PROJECT_MEMOMINT.md`)  
> **Completion Criterion:** The interface clearly distinguishes historical records from currently confirmed information; users can verify physical-item locations, resolve conflicting records via supersession without silent data loss, trace data provenance, and recover broken file references through guided flows.  
> **Guiding Principles:** Section 15 (Trust and Memory Freshness), Section 11.5 (`MemoryRelation`), Section 11.7 (`ConfirmationPolicy`), Section 15.1 (Provenance), Section 15.2 (Freshness), Section 15.3 (File-Health Status), Section 15.4 (Conflicting Memories), Section 15.5 (Physical-Item Verification), FR-08 (File Health), FR-09 (Physical-Item Check-Ins), FR-14 (User Control), NFR-01 (Privacy), NFR-02 (Local-First Operation), NFR-03 (Data Integrity), NFR-08 (Graceful Degradation).

---

## Table of Contents

- [1. Executive Summary & Purpose](#1-executive-summary--purpose)
- [2. Architectural Design & Component Pipelines](#2-architectural-design--component-pipelines)
  - [2.1 Data Provenance Tracking Pipeline](#21-data-provenance-tracking-pipeline)
  - [2.2 Freshness Evaluation Engine & Classification Model](#22-freshness-evaluation-engine--classification-model)
  - [2.3 Physical-Item Confirmation Policy & Scheduling System](#23-physical-item-confirmation-policy--scheduling-system)
  - [2.4 Conflicting-Memory Detection & Supersession Engine](#24-conflicting-memory-detection--supersession-engine)
  - [2.5 File-Health Recovery & Reconnection Pipeline](#25-file-health-recovery--reconnection-pipeline)
  - [2.6 Retrieval Engine Integration (Relevance vs. Confidence)](#26-retrieval-engine-integration-relevance-vs-confidence)
- [3. Data Model & Persistence Architecture](#3-data-model--persistence-architecture)
  - [3.1 Database Schema Extensions](#31-database-schema-extensions)
  - [3.2 Entity Contracts & Relationships](#32-entity-contracts--relationships)
- [4. UI & UX Interaction Workflows](#4-ui--ux-interaction-workflows)
  - [4.1 Memory Card Trust Badges & Freshness Chips](#41-memory-card-trust-badges--freshness-chips)
  - [4.2 Interactive Location Check-In Flow](#42-interactive-location-check-in-flow)
  - [4.3 Conflict Resolution & Supersession Dialogs](#43-conflict-resolution--supersession-dialogs)
  - [4.4 File-Health Diagnostics & Recovery Action Sheets](#44-file-health-diagnostics--recovery-action-sheets)
- [5. Fallback Strategy, Error Containment & Invariants](#5-fallback-strategy-error-containment--invariants)
- [6. Step-by-Step Implementation Roadmap](#6-step-by-step-implementation-roadmap)
  - [Step 5.1: Persistence & Repository Foundation](#step-51-persistence--repository-foundation)
  - [Step 5.2: Freshness Evaluation Engine & Domain Services](#step-52-freshness-evaluation-engine--domain-services)
  - [Step 5.3: Confirmation Policy & Check-In Dispatcher](#step-53-confirmation-policy--check-in-dispatcher)
  - [Step 5.4: Conflicting-Memory & Supersession Engine](#step-54-conflicting-memory--supersession-engine)
  - [Step 5.5: File-Health Recovery Services](#step-55-file-health-recovery-services)
  - [Step 5.6: Presentation Layer & UI Integration](#step-56-presentation-layer--ui-integration)
  - [Step 5.7: Retrieval Integration & Confidence Ranking](#step-57-retrieval-integration--confidence-ranking)
- [7. Testing & Verification Strategy](#7-testing--verification-strategy)
- [8. File Inventory for Phase 5](#8-file-inventory-for-phase-5)
- [9. Phase 5 Completion Criteria Checklist](#9-phase-5-completion-criteria-checklist)

---

## 1. Executive Summary & Purpose

A personal memory system that claims everything is permanently and unconditionally true becomes actively misleading over time:
- A user recorded six months ago that their passport was *"in the top study drawer"*. Has it been moved?
- A document indexed under `notes_final.docx` had its Storage Access Framework permission revoked or was moved to another directory.
- A user records a new memory: *"I moved my spare keys to the hallway coat rack"*, conflicting with an earlier memory stating they were *"on the kitchen counter"*.

Most note-taking applications either silently overwrite the past, erase old context, or present stale information with the same visual authority as something confirmed five minutes ago.

**Phase 5 establishes the Trust & Freshness Pillar of MemoMint (Section 15):**
1. **Never Lie About Verification:** The system cannot visually see physical objects. It must never claim an item is definitely in a location unless explicitly confirmed by the user.
2. **Preserve History Non-Destructively:** When an item's location changes or facts conflict, old records are superseded, not destroyed. Full audit provenance is maintained.
3. **Multi-Speed Freshness:** A historical note may remain valid forever; a physical item's location requires regular confirmation; a file reference requires I/O accessibility checks.
4. **Actionable Recovery:** Inaccessible files provide clear, one-tap recovery flows (permission re-grant or relocation) instead of generic error toasts or silent deletion.

---

## 2. Architectural Design & Component Pipelines

```
+----------------------------------------------------------------------------------------------------+
|                                    PHASE 5 TRUST & FRESHNESS PIPELINE                              |
+----------------------------------------------------------------------------------------------------+
                                                  |
           +--------------------------------------+--------------------------------------+
           |                                      |                                      |
           v                                      v                                      v
  [1. PROVENANCE TRACKER]               [2. FRESHNESS EVALUATOR]            [3. CONFLICT & SUPERSEDING]
  - Source type & source ID             - Type-specific half-life           - Detects item location shifts
  - Derivation tracking                 - Last confirmed timestamp          - Non-destructive SUPERSEDES
  - Extraction rule & version           - Freshness states:                 - Preserves historical timeline
  - User verification log                 FRESH, AGING, STALE, UNCONFIRMED  - Visual lineage in UI
           |                                      |                                      |
           +--------------------------------------+--------------------------------------+
                                                  |
           +--------------------------------------+--------------------------------------+
           |                                                                             |
           v                                                                             v
  [4. CONFIRMATION CHECK-IN ENGINE]                                             [5. FILE-HEALTH RECOVERY]
  - ConfirmationPolicy (DAILY, 3-DAY, WEEKLY)                                   - Evaluates AccessState
  - Next check calculation                                                      - Permission re-grant launcher
  - In-app check-in card & notifications                                        - Document relocation picker
  - Affirm / Relocate / Mark Unknown                                            - Content re-indexer
+----------------------------------------------------------------------------------------------------+
```

---

### 2.1 Data Provenance Tracking Pipeline

Section 15.1 specifies that every memory and derived fact must retain a traceable origin in data rather than presentation heuristics.

#### Provenance Attributes
- **Primary Source Type (`SourceType`):** `TYPED` (direct user input, highest literal confidence), `VOICE` (speech recognition transcript), `SHARED` (system share sheet), `IMPORTED` (external backup/JSON), `FILE` (local document reference).
- **Source Identifier (`source_id`):** Foreign UUID or URI pointing to the upstream source (e.g., `file_references.id` or import batch ID).
- **Derivation Provenance (`ExtractionResult.Provenance`):** `USER_EXPLICIT`, `EXTRACTED_BY_RULE`, `INFERRED_BY_RULE`, `CONFIRMED_BY_USER`.
- **Generator Version:** Schema and rule version used to extract the context record, ensuring outdated heuristics can be re-run cleanly.

---

### 2.2 Freshness Evaluation Engine & Classification Model

Section 15.2 explicitly forbids imposing a single universal expiration interval on all memories. Freshness is a domain-specific evaluation function:

$$\text{FreshnessState} = f(\text{MemoryType}, \text{createdAt}, \text{lastConfirmedAt}, \text{ConfirmationPolicy})$$

#### Freshness State Definitions

| State | Semantic Meaning | Visual Indicator | Default Thresholds |
|---|---|---|---|
| **`CONFIRMED_FRESH`** | Verified recently by the user; high confidence. | Emerald Green Chip ("Confirmed 2h ago") | Confirmed within policy interval or $< 7$ days |
| **`AGING`** | Approaching the confirmation interval boundary; user check-in recommended. | Amber / Ochre Chip ("Check-in suggested") | $75\% - 100\%$ of confirmation interval elapsed |
| **`STALE`** | Passed confirmation window without user verification; potential location drift. | Muted Slate Chip ("Unverified for 14d") | $> 100\%$ of confirmation interval elapsed |
| **`UNCONFIRMED`** | Never confirmed since initial recording; historical assertion only. | Subtle Gray Outline ("Not yet confirmed") | `last_confirmed_at == null` |
| **`EVERGREEN`** | General notes, concepts, and permanent thoughts exempt from decay. | No expiration warning | `MemoryType.NOTE` without check-in policy |

#### Freshness Score Calculation ($S_{\text{freshness}} \in [0.0, 1.0]$)
Used by ranking algorithms to adjust confidence scores without completely suppressing relevant memories:

$$S_{\text{freshness}}(t) = \begin{cases} 
1.0 & \text{if } t \le T_{\text{fresh}} \\
\exp\left(-\lambda \cdot \frac{t - T_{\text{fresh}}}{T_{\text{stale}} - T_{\text{fresh}}}\right) & \text{if } t > T_{\text{fresh}}
\end{cases}$$

Where $\lambda = \ln(2)$ (half-life decay parameter) and $t$ is the elapsed time since `lastConfirmedAt` (or `createdAt` if never confirmed).

---

### 2.3 Physical-Item Confirmation Policy & Scheduling System

In accordance with Section 11.7 and FR-09, physical items can be configured with automated check-in schedules:

```
[Memory created: PHYSICAL_ITEM]
        |
        v
[ConfirmationPolicy Configured]
  |-- Interval: DAILY (1d), THREE_DAYS (3d), WEEKLY (7d), or DISABLED
  |-- Computes: next_check_at = System.currentTimeMillis() + intervalMillis
        |
        v
[Periodic Background Checker (WorkManager)]
  |-- Queries confirmation_policies WHERE enabled=1 AND next_check_at <= now
  |-- Identifies memories needing user confirmation
  |-- Updates last_prompted_at
  |-- Triggers Notification / In-App Check-In Banner
        |
        v
[User Responds to Prompt]
  |---> Option A: [Confirm Location] --> last_confirmed_at = now, next_check_at = now + interval
  |---> Option B: [Moved Item]       --> Launches Relocation & Supersession Workflow
  +---> Option C: [Unknown]          --> Marks state STALE, next check deferred
```

---

### 2.4 Conflicting-Memory Detection & Supersession Engine

When a user creates a new record or changes an item's location (Section 15.4), the system must **never** silently overwrite the past:

```
Existing Memory M1:
"My passport is in the master bedroom nightstand."
(Created: June 1, 2026, Type: PHYSICAL_ITEM)
                         |
                         v
User Records New Memory M2:
"I put my passport in the study desk safe."
                         |
                         v
[Conflict Detector Engine]
  1. Identifies common entity: Entity("passport", ITEM)
  2. Identifies differing locations: Entity("nightstand") vs Entity("desk safe")
  3. Detects temporal sequence: M2.createdAt > M1.createdAt
                         |
                         v
[User Supersession Dialog]
  "You previously noted 'passport' was in 'master bedroom nightstand'.
   Did you relocate this item?"
        |
        +---> [Yes, Relocated (Supersede)]:
        |       1. Creates MemoryRelation(M2, SUPERSEDES, M1)
        |       2. M1 marked with supersession tag (retained in history, de-prioritized in default feed)
        |       3. M2 becomes active primary location memory
        |
        +---> [No, Keep Both Separate]:
                1. Creates MemoryRelation(M1, RELATED_TO, M2)
                2. Both memories remain active independently
```

---

### 2.5 File-Health Recovery & Reconnection Pipeline

Phase 4 introduced `FileHealthService` to detect access states (`PERMISSION_REVOKED`, `MISSING_OR_UNAVAILABLE`, `TEMPORARILY_INACCESSIBLE`). Phase 5 builds the **complete user-facing recovery flows** (Section 15.3, FR-08):

```
                                 [File Health Audit]
                                          |
        +---------------------------------+---------------------------------+
        |                                                                   |
        v                                                                   v
[PERMISSION_REVOKED]                                            [MISSING_OR_UNAVAILABLE]
        |                                                                   |
        v                                                                   v
User sees: "Permission Expired"                                 User sees: "File Moved or Missing"
Action: [Re-grant Permission]                                   Action: [Locate File]
        |                                                                   |
        v                                                                   v
Launches SAF Intent.ACTION_OPEN_DOCUMENT                        Launches SAF Picker with matching MIME/Name
with expected file name / MIME                                              |
        |                                                                   v
User selects file:                                              User selects new location:
takePersistableUriPermission()                                  1. Updates file_references.uri_or_path
        |                                                       2. Recomputes SHA-256 fingerprint
        v                                                       3. If fingerprint matches:
AccessState -> ACCESSIBLE                                          Preserves all context & relations!
Background Re-index triggered                                   4. If content differs:
                                                                   Triggers incremental delta re-index
```

---

### 2.6 Retrieval Engine Integration (Relevance vs. Confidence)

Section 12.3 and Section 25.4 strictly state: **Retrieval relevance is not factual confidence.**

In Phase 5, `SearchService` separates search score from confidence assessment:
1. **Match Relevance Score ($S_{\text{relevance}}$):** Computed via Phase 3's 5-component `RelevanceScorer` (text BM25 + synonyms + metadata + graph relations + fuzzy).
2. **Trust Confidence ($C_{\text{trust}}$):**
   $$C_{\text{trust}} = w_{\text{fresh}} \cdot S_{\text{freshness}} + w_{\text{prov}} \cdot S_{\text{provenance}} + w_{\text{health}} \cdot S_{\text{health}}$$
   - $S_{\text{provenance}}$: `TYPED` = 1.0, `VOICE` = 0.85, `DOCUMENT` = 0.90, `IMPORTED` = 0.70.
   - $S_{\text{health}}$: `ACCESSIBLE` = 1.0, `PERMISSION_REVOKED` = 0.40, `MISSING` = 0.10.
3. **Supersession Filter:** Memories where another active memory points with `SUPERSEDES` are suppressed from primary results unless the user specifically searches for history or past locations.

---

## 3. Data Model & Persistence Architecture

### 3.1 Database Schema Extensions

The core tables (`confirmation_policies`, `memory_relations`, `memories`) were created in Phase 1 with schema hooks for Phase 5. Phase 5 activates and refines these tables.

#### Table: `confirmation_policies` (Active in Phase 5)
```sql
CREATE TABLE IF NOT EXISTS confirmation_policies (
    memory_id TEXT PRIMARY KEY NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 0,
    interval_type TEXT NOT NULL DEFAULT 'DISABLED',
    interval_value INTEGER NOT NULL DEFAULT 0,
    last_prompted_at INTEGER,
    next_check_at INTEGER,
    FOREIGN KEY (memory_id) REFERENCES memories(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_confirmation_policies_next_check
    ON confirmation_policies(enabled, next_check_at);
```

#### Table: `memory_relations` (Supersession Indexing)
```sql
CREATE INDEX IF NOT EXISTS idx_memory_relations_supersedes
    ON memory_relations(relation_type, source_id, target_id);
```

---

### 3.2 Entity Contracts & Relationships

#### Freshness Entity Model (`FreshnessReport.java`)
```java
public class FreshnessReport {
    private final String memoryId;
    private final FreshnessStatus status; // CONFIRMED_FRESH, AGING, STALE, UNCONFIRMED, EVERGREEN
    private final long lastConfirmedAt;
    private final long elapsedMillis;
    private final boolean checkInDue;
    private final String humanReadableStatus;
    // ...
}
```

#### Supersession Record (`SupersessionInfo.java`)
```java
public class SupersessionInfo {
    private final String activeMemoryId;
    private final String supersededMemoryId;
    private final long supersededAt;
    private final String previousValue;
    private final String currentValue;
    // ...
}
```

---

## 4. UI & UX Interaction Workflows

### 4.1 Memory Card Trust Badges & Freshness Chips

The memory card (`item_memory.xml`) is enriched with Phase 5 trust telemetry:
1. **Provenance Chip:**
   - Typed note: Subtle pencil icon (`Typed`)
   - Attached document: File icon (`Linked: notes.docx`)
   - Voice transcript: Mic icon (`Voice Transcript`)
2. **Freshness Pill:**
   - Verified within schedule: Solid mint outline `[✓ Confirmed 3d ago]`
   - Overdue check-in: Ochre badge `[⚠️ Check-in Due]`
   - Unverified: Muted dashed border `[Not confirmed]`
3. **Superseded History Banner:**
   - If memory has historical predecessors: expandable footer `[View 1 previous location]`.

---

### 4.2 Interactive Location Check-In Flow

When a user taps `[Check-in Due]` or `[Confirm Location]`:
- A bottom sheet / dialog appears:
  > **Confirm Location**  
  > *"Is your [Earbuds] still in [Desk Drawer]?"*  
  > Last confirmed: October 10, 2026 (10 days ago)  
  >  
  > `[Yes, Still Here]` — updates timestamp, hides alert  
  > `[Moved to New Location]` — prompts for new location, automatically generates `SUPERSEDES` relation  
  > `[Not Sure / Can't Find]` — marks memory as STALE without deleting historical record  

---

### 4.3 Conflict Resolution & Supersession Dialogs

When saving a memory in `MemoryEditorActivity`:
- `ConflictDetector` checks if an active memory exists with the same physical item entity but a different location entity.
- If detected, an interactive prompt asks the user:
  > **Location Update Detected**  
  > *"You previously recorded that **Spare Keys** is in **Kitchen Drawer**.*  
  > *Are you updating its location to **Hallway Table**?"*  
  >  
  > `[Update Location & Archive History]` *(Recommended)*  
  > `[Keep Both as Separate Memories]`  

---

### 4.4 File-Health Diagnostics & Recovery Action Sheets

When a file-backed memory card is displayed:
- If file health check yields `PERMISSION_REVOKED`:
  - An amber recovery banner appears on the memory card:
    > *Storage access was reset by Android. Reconnect this document to restore search and preview.*  
    > `[Reconnect File]` (one tap launches SAF document picker with matching name).
- If file health check yields `MISSING_OR_UNAVAILABLE`:
  - A red recovery banner appears:
    > *Document could not be located at original path.*  
    > `[Find Moved File]` `[Keep Note Without File]`

---

## 5. Fallback Strategy, Error Containment & Invariants

```
+-------------------------------------------------------------------------------+
|                      PHASE 5 FAIL-SAFE & PRIVACY INVARIANTS                   |
+-------------------------------------------------------------------------------+
|                                                                               |
|  [Zero False Verification Claim]                                              |
|    |--> Invariant: The system NEVER updates lastConfirmedAt automatically     |
|    +--> Rule: Only explicit user clicks/gestures can record verification      |
|                                                                               |
|  [Non-Destructive Supersession]                                               |
|    |--> Invariant: Replacing a location never deletes old memory rows         |
|    +--> Rule: Inserts a SUPERSEDES edge; previous memory retained for history |
|                                                                               |
|  [Storage Permission Revocation Isolation]                                    |
|    |--> Invariant: A revoked SAF permission never deletes the Memory          |
|    +--> Rule: State becomes PERMISSION_REVOKED; text context remains searchable|
|                                                                               |
|  [Battery & Doze Respecting Check-In Jobs]                                    |
|    |--> Invariant: Periodic check-in audits use Android WorkManager           |
|    +--> Constraints: BatteryNotLow, DeviceIdle, no wake-lock abuse             |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 6. Step-by-Step Implementation Roadmap

### Step 5.1: Persistence & Repository Foundation
- [ ] Implement `ConfirmationPolicyRepository` and `ConfirmationPolicyRepositoryImpl` using `DatabaseConstants.ConfirmationPolicies`.
- [ ] Implement `MemoryRelationRepository` and `MemoryRelationRepositoryImpl` for CRUD operations on knowledge graph edges and `SUPERSEDES` relationships.
- [ ] Add query methods to `MemoryRepository`:
  - `findActiveByItemEntity(String canonicalItemLabel)`
  - `findSupersededMemories(String memoryId)`
  - `findPrimaryActiveLocation(String itemEntityId)`

### Step 5.2: Freshness Evaluation Engine & Domain Services
- [ ] Create `FreshnessStatus` enum: `CONFIRMED_FRESH`, `AGING`, `STALE`, `UNCONFIRMED`, `EVERGREEN`.
- [ ] Implement `FreshnessEvaluator` domain class:
  - Evaluates time delta against memory type and confirmation schedule.
  - Returns `FreshnessReport` with status, time elapsed, and action recommendations.
- [ ] Implement `TrustAndFreshnessService`:
  - Coordinates confirmation updates, freshness reports, and audit logging.

### Step 5.3: Confirmation Policy & Check-In Dispatcher
- [ ] Implement `ConfirmationScheduler` using Android `WorkManager` (`PeriodicWorkRequest` running once every 24 hours):
  - Queries `confirmation_policies` where `enabled = 1` and `next_check_at <= now`.
  - Posts user-friendly system notification for items needing verification (Section 15.5).
- [ ] Implement `CheckInNotificationHelper` with high-priority channel and one-tap action intents (`Still Here` / `Moved`).

### Step 5.4: Conflicting-Memory & Supersession Engine
- [ ] Implement `ConflictDetector`:
  - Compares new memory's extracted item/location entities against existing active database records.
  - Detects spatial contradictions (same item, different location).
- [ ] Implement `SupersessionManager`:
  - Links newer memory to older memory with `RelationType.SUPERSEDES`.
  - Maintains provenance timeline and history retrieval.

### Step 5.5: File-Health Recovery Services
- [ ] Extend `FileHealthService` with interactive recovery contracts:
  - `reconnectFileReference(String fileId, Uri newUri)`
  - `reindexFileReference(String fileId)`
- [ ] Implement SAF reconnection helper to verify MIME and file fingerprint compatibility before finalizing recovery.

### Step 5.6: Presentation Layer & UI Integration
- [ ] Update `MemoryAdapter` and `item_memory.xml`:
  - Freshness badge indicators with dynamic color-coding (Mint, Amber, Slate).
  - Provenance icon display (Pencil, File, Mic, Import).
  - Supersession history toggle ("Previous location recorded").
  - File recovery action buttons when file health is compromised.
- [ ] Add confirmation dialogs:
  - `LocationCheckInDialog`: Affirm, Relocate, or Mark Unknown.
  - `ConflictResolutionDialog`: Supersede vs Keep Both.
- [ ] Add check-in schedule configuration to `MemoryEditorActivity` (Daily, 3 Days, Weekly, Disabled).

### Step 5.7: Retrieval Integration & Confidence Ranking
- [ ] Update `SearchService`:
  - De-prioritizes or hides superseded memories from primary search results unless query specifically asks for history (e.g. "where used to be").
  - Calculates composite trust confidence ($C_{\text{trust}}$) alongside search relevance.
  - Displays transparency explanation: *"Confirmed 2 days ago"* or *"Unconfirmed - recorded 4 months ago"*.

---

## 7. Testing & Verification Strategy

Following MemoMint's rigorous offline testing standards (Section 22, Section 25):

1. **`ConfirmationPolicyRepositoryTest.java` (Robolectric / SQLite):**
   - Verifies saving, retrieving, updating `next_check_at`, and cascade deletion when parent memory is deleted.
2. **`FreshnessEvaluatorTest.java` (Pure Unit Test):**
   - Verifies freshness state transitions: `CONFIRMED_FRESH` $\rightarrow$ `AGING` $\rightarrow$ `STALE` across custom timelines.
   - Verifies notes without policy remain `EVERGREEN`.
3. **`ConflictDetectorTest.java` (Unit Test):**
   - Verifies detecting conflicting locations for same physical item.
   - Verifies non-conflicting memories (different items or notes) do not trigger false conflicts.
4. **`SupersessionManagerTest.java` (Integration Test):**
   - Verifies creating `SUPERSEDES` relation.
   - Verifies retrieval suppresses superseded records from default active queries while keeping them intact in audit queries.
5. **`FileHealthRecoveryTest.java` (Robolectric Test):**
   - Simulates `PERMISSION_REVOKED` state and tests successful reconnection with new URI.
   - Tests fingerprint comparison during file relocation.
6. **`CheckInWorkerTest.java` (WorkManager Test):**
   - Verifies background job correctly identifies due check-in records and ignores disabled/future policies.

---

## 8. File Inventory for Phase 5

| Layer | File Path | Purpose |
|---|---|---|
| **Domain Models** | `domain/model/FreshnessStatus.java` | Enum for freshness classification |
| | `domain/model/FreshnessReport.java` | Detailed freshness metrics and check-in metadata |
| | `domain/model/SupersessionInfo.java` | Lineage data linking superseded and active memories |
| | `domain/model/ConflictDetectionResult.java` | Details of detected contradictory facts/locations |
| **Persistence** | `data/repository/ConfirmationPolicyRepository.java` | Interface for `confirmation_policies` CRUD |
| | `data/repository/ConfirmationPolicyRepositoryImpl.java` | SQLite implementation for `confirmation_policies` |
| | `data/repository/MemoryRelationRepository.java` | Interface for graph edges and `SUPERSEDES` |
| | `data/repository/MemoryRelationRepositoryImpl.java` | SQLite implementation for `memory_relations` |
| **Domain Services**| `domain/trust/FreshnessEvaluator.java` | Evaluates freshness score and classification |
| | `domain/trust/ConflictDetector.java` | Analyzes entity overlaps to detect conflicting locations |
| | `domain/trust/SupersessionManager.java` | Manages non-destructive history replacement |
| **Application** | `application/TrustAndFreshnessService.java` | Application orchestrator for confirmation and freshness |
| | `application/ConfirmationScheduler.java` | Schedules periodic background check-in audits |
| | `application/workers/CheckInWorker.java` | Android `WorkManager` task for periodic prompts |
| | `application/FileHealthRecoveryService.java`| Manages SAF file reconnection and relocation |
| **Presentation** | `ui/trust/LocationCheckInBottomSheet.java`| Interactive prompt for confirming item presence |
| | `ui/trust/ConflictResolutionDialog.java` | Interactive prompt for superseding old locations |
| | `ui/memory/MemoryAdapter.java` (Updated) | Renders freshness chips, provenance icons, and recovery |
| | `ui/memory/MemoryEditorActivity.java` (Updated)| Exposes confirmation schedule configuration |
| **Tests** | `app/src/test/.../ConfirmationPolicyRepositoryTest.java` | Database integrity tests for confirmation policies |
| | `app/src/test/.../FreshnessEvaluatorTest.java` | Unit tests for decay curves and freshness states |
| | `app/src/test/.../ConflictDetectorTest.java` | Unit tests for location conflict detection |
| | `app/src/test/.../SupersessionIntegrationTest.java` | End-to-end tests for non-destructive supersession |
| | `app/src/test/.../FileHealthRecoveryTest.java` | Unit tests for file reconnection and re-indexing |

---

## 9. Phase 5 Completion Criteria Checklist

- [x] `ConfirmationPolicyRepository` implemented and tested with full SQLite cascade support.
- [x] `MemoryRelationRepository` implemented supporting `SUPERSEDES` and graph traversal.
- [x] `FreshnessEvaluator` implemented providing deterministic multi-speed freshness evaluation.
- [x] `ConflictDetector` implemented detecting item-location contradictions without cloud inference.
- [x] `SupersessionManager` implemented preserving full historical memory lineage non-destructively.
- [x] `ConfirmationScheduler` and `CheckInReceiver` implemented respecting battery and Doze constraints (via inexact AlarmManager, avoiding external dependencies).
- [x] `FileHealthRecoveryService` implemented supporting one-tap SAF permission re-grant and file relocation.
- [x] Memory card UI updated with dynamic Freshness Chips, Provenance Badges, and Recovery Actions.
- [x] Interactive `LocationCheckInDialog` and `ConflictResolutionDialog` implemented and integrated.
- [x] Search retrieval adjusted to separate match relevance from factual confidence, de-prioritizing superseded history.
- [x] Comprehensive unit and integration test suite passing with 100% offline local execution (164 tests passing, 0 failures).

---

## 10. Phase 5 Verification & Implementation Notes

- **Database Migration:** Database schema updated to Version 5 with explicit `applyMigrationV4toV5(db)` creating composite indexes on `confirmation_policies(enabled, next_check_at)` and `memory_relations(relation_type, source_id, target_id)`.
- **Decoupled Background Scheduler:** Avoided adding external heavyweight WorkManager dependencies to `libs.versions.toml` by utilizing Android's native `AlarmManager.setInexactRepeating` with inexact `ELAPSED_REALTIME` triggers and broadcast intents (`CheckInReceiver`, `CheckInActionReceiver`).
- **Interactive UI Flows:**
  - `LocationCheckInDialog`: Prompting "Still Here", "Moved", and "Not Sure" on card confirmation taps.
  - `ConflictResolutionDialog`: Integrated directly into memory editor save pipeline when a new physical item contradicts an existing record.
- **Search Scoring & Explainability:** `SearchService` incorporates `memoryRelationRepository.isSuperseded(...)` to mark superseded candidates with explainable `"Historical record (superseded by newer memory)"` tags and ranking de-prioritization.
- **Verification Suite:** 6 new dedicated test suites added (`ConfirmationPolicyRepositoryTest`, `ConflictDetectorTest`, `FreshnessEvaluatorTest`, `SupersessionIntegrationTest`, `FileHealthRecoveryTest`, `TrustAndFreshnessServiceTest`), bringing total project tests to 164 with 100% passing offline.
