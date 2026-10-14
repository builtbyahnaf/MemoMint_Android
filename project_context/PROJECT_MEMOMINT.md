# Project WhereWasIt?

## An Offline-First, Open-Source Personal Memory Engine for Android

> **Project brief · Concept / pre-development**

| Project details | Value |
|:--|:--|
| **Project status** | Concept / pre-development |
| **Working name** | Project WhereWasIt? |
| **Platform** | Android |
| **Primary language** | Java |
| **Architecture** | Native Android application with a modular, local-first memory engine |
| **Database** | SQLite with full-text indexing |
| **Network dependency** | None required for core functionality |
| **AI/ML dependency** | None required for the initial implementation |
| **License** | To be selected before public release |
| **Development philosophy** | Privacy by design, explainable retrieval, minimal friction, measurable reliability |

---

## Contents

- [1. Executive Summary](#1-executive-summary)
- [2. The Problem](#2-the-problem)
- [3. Product Vision](#3-product-vision)
- [4. Target Users and Use Cases](#4-target-users-and-use-cases)
- [5. Core Use Cases](#5-core-use-cases)
- [6. Product Scope](#6-product-scope)
- [7. Product Pillars](#7-product-pillars)
- [8. Functional Requirements](#8-functional-requirements)
- [9. Non-Functional Requirements](#9-non-functional-requirements)
- [10. Technical Architecture](#10-technical-architecture)
- [11. Data Model](#11-data-model)
- [12. Retrieval Engine](#12-retrieval-engine)
- [13. Context Extraction and Indexing](#13-context-extraction-and-indexing)
- [14. Voice Capture and Intent Interpretation](#14-voice-capture-and-intent-interpretation)
- [15. Trust and Memory Freshness](#15-trust-and-memory-freshness)
- [16. Privacy and Security Model](#16-privacy-and-security-model)
- [17. Background Processing and Battery Management](#17-background-processing-and-battery-management)
- [18. User Experience and Interaction Design](#18-user-experience-and-interaction-design)
- [19. Performance and Quality Targets](#19-performance-and-quality-targets)
- [20. Open-Source Strategy](#20-open-source-strategy)
- [21. Development Roadmap](#21-development-roadmap)
- [22. Testing Strategy](#22-testing-strategy)
- [23. Known Limitations and Risks](#23-known-limitations-and-risks)
- [24. Future Direction](#24-future-direction)
- [25. Coding-Assistant Instructions](#25-coding-assistant-instructions)
- [26. Definition of a Successful Initial Release](#26-definition-of-a-successful-initial-release)
- [27. Final Product Statement](#27-final-product-statement)

---

# 1. Executive Summary

Project WhereWasIt? is an open-source, offline-first personal memory system designed to help people remember information, retrieve forgotten details, locate physical belongings, track files, and act on important memories without relying on cloud infrastructure or online AI services.

People routinely forget where they placed an object, which document contains a particular detail, what they intended to do later, or the context surrounding something they previously learned or recorded. Existing tools generally separate these problems across note-taking applications, file managers, reminders, document search, and personal inventory systems.

WhereWasIt? aims to unify these fragmented workflows into one searchable, context-aware personal memory engine.

Instead of requiring users to remember exact filenames, keywords, folder paths, or the wording of an original note, the application should interpret the intent behind a query and retrieve the most relevant recorded information.

For example, a user might record:

> "My spare USB-C cable is in the second drawer of my desk."

Several days later, the user might search:

> "Where did I put the cable I need for campus?"

The original memory does not need to contain the words "campus" or "need". The retrieval engine should use contextual associations, synonyms, metadata, and relevance ranking to identify the original memory.

WhereWasIt? also aims to maintain the usefulness of memories over time. Files may move or disappear, physical objects may be relocated, and previously accurate information may become outdated. The application should detect what it can, communicate uncertainty honestly, and request confirmation when necessary.

The project is designed around three core pillars:

1. **Capture** — Record information quickly and naturally.

2. **Recall** — Retrieve relevant information even when the user cannot remember its exact wording.

3. **Verify** — Preserve provenance, communicate uncertainty, and help keep memories current.

All essential processing should occur on the device. Open-source development will make the implementation inspectable, while automated tests and transparent documentation will help verify the project's privacy claims.

The initial implementation will use native Android development in Java, SQLite, conventional information-retrieval algorithms, and Android platform APIs. Python and more advanced machine-learning techniques may be introduced later for research, evaluation, model experimentation, or optional on-device inference. They are not prerequisites for the first usable version.

# 2. The Problem

## 2.1 Human memory is fragmented across tools

A person's useful information may be distributed across:

- Personal notes and quick thoughts.

- Documents, PDFs, spreadsheets, and text files.

- Folder paths and downloaded attachments.

- Physical belongings and their storage locations.

- Tasks, reminders, and future intentions.

- Personal observations and contextual details.

- Information shared through other applications.

These records are stored in different formats and accessed through different interfaces.

Even when the information has been recorded, the user may not remember where it was saved or what terminology was used.

## 2.2 Conventional search depends too heavily on remembered wording

A search engine may work well when the user remembers the correct filename or an exact phrase.

However, people frequently remember the *meaning* of information rather than its original wording.

For example:

- Original memory: "The new dust blower is in the bottom drawer beside my bed."

- Search query: "Where did I put the thing for cleaning my keyboard?"

The query and memory have a conceptual relationship, but they do not share many exact keywords.

WhereWasIt? should bridge this gap using a layered retrieval system rather than relying exclusively on exact matches or a single machine-learning model.

## 2.3 Memories become unreliable over time

A stored memory may become inaccurate because:

- A file was moved, renamed, or deleted.

- A physical object was relocated.

- A reminder is no longer relevant.

- A fact has changed since it was recorded.

- A previous interpretation was inferred rather than explicitly confirmed.

A useful memory system must distinguish between information that was once recorded and information that is still believed to be accurate.

## 2.4 Capturing information can be inconvenient

Opening an application, navigating to a form, selecting a category, and manually entering structured details creates unnecessary friction.

If recording a memory takes too much effort, users may postpone it and forget the information before it is captured.

WhereWasIt? should make capture available from Android's home screen, Quick Settings, voice input, and the system share sheet, with ordinary in-app entry as a fallback.

## 2.5 Privacy and dependence on external services

Personal memories may reveal routines, possessions, documents, habits, and sensitive details.

A cloud-dependent memory service introduces questions about data transmission, external processing, account dependence, retention, and future service availability.

WhereWasIt? will pursue a different approach: keep the core product functional offline, make local processing the default, minimize permissions, and make privacy behavior inspectable through its open-source implementation.

# 3. Product Vision

The long-term vision is to build a dependable personal memory engine that lets users externalize useful information without having to organize every detail manually.

Users should be able to record information in ordinary language, forget the organizational details, and later retrieve the information by describing what they remember about it.

The application should behave less like a conventional notes application and more like a searchable, evolving map of a person's recorded knowledge.

This does not mean that the application possesses human-like memory or can reliably infer every unstated intention. Its intelligence must come from explicit records, extracted context, deterministic relationships, measured retrieval algorithms, and optional future local models.

## 3.1 Product principles

### Privacy by design

- No mandatory cloud account.

- No mandatory online AI.

- No required cloud database.

- No telemetry or analytics by default.

- No silent cloud fallback for speech recognition.

- No unexpected network transmission of personal memories.

- Clear controls over which files and folders may be indexed.

### Local-first operation

The core memory store, indexing system, retrieval pipeline, contextual records, and reminder logic must operate locally.

The application must remain useful when the device has no internet connection.

### Explainable retrieval

The application should provide useful matches and, when practical, explain why a result appeared.

A result may match through its original text, a related concept, an attachment, a memory relationship, or a filename. These distinctions should be available to the ranking and presentation layers.

### Low-friction interaction

Recording or retrieving a simple memory should require as few steps as reasonably possible.

### Reliability over artificial intelligence hype

The first version should prioritize correct data handling, high-quality search, predictable behavior, and transparent uncertainty over unnecessary model complexity.

### User control

Users must be able to inspect, edit, delete, export, and correct their memories and their associated context.

# 4. Target Users and Use Cases

WhereWasIt? is intended for people who routinely need to recall details from their everyday lives.

Potential users include:

- Students managing documents, academic materials, equipment, and deadlines.

- Developers and technical professionals managing project references and local files.

- People who frequently misplace belongings.

- Users who collect information across many applications.

- People who want a private, offline personal knowledge system.

- Users who prefer natural-language capture instead of manually categorizing everything.

These are intended audiences, not claims that the product has already been validated with them.

# 5. Core Use Cases

## 5.1 Remembering physical belongings

A user records:

"My spare earphones are in the left drawer of my bedside table."

Later, the user searches:

"Where are my backup earbuds?"

Expected behavior:

- Recognize that "earphones", "earbuds", and "backup" may be related.

- Retrieve the relevant memory.

- Display the recorded location.

- Show when the location was last confirmed.

- Avoid claiming that the item is definitely still there if the memory has become stale.

## 5.2 Remembering document contents

A user indexes a document containing information about guitar prices.

The file may have an unhelpful name such as `notes\\_final\\_3.docx`.

The user searches:

"Find that document where I compared the prices of new guitars."

Expected behavior:

- Search extracted document text, not only filenames.

- Search generated context and relevant keywords.

- Rank likely documents by relevance.

- Show the original filename and accessible location.

- Allow the user to open the document through Android's permitted file-access mechanisms.

## 5.3 Tracking files and their health

A user indexes a document and later moves or deletes it.

WhereWasIt? should periodically verify whether the stored file reference remains valid, where platform permissions permit such checks.

If the file cannot be found:

- Mark the reference as unavailable or unverified.

- Explain the reason when known.

- Ask the user to locate or reconnect the file if appropriate.

- Never invent a new path or silently replace the original reference with an unsupported guess.

File movement detection is platform- and permission-dependent. Android does not guarantee unrestricted filesystem visibility.

## 5.4 Remembering quantities and inventories

A user records:

"I have three boxes of pens in my wardrobe."

The system should preserve:

- Item: pens.

- Quantity: three boxes.

- Location: wardrobe.

- Source: user-recorded memory.

- Timestamp.

- Optional confirmation schedule.

A later query such as "Do I have spare pens?" should retrieve this record through relevant concepts, even when the query does not reproduce the original sentence.

## 5.5 Recording a memory and reminder through voice

A user says:

"Remember that I left my earbuds beside my bed. Remind me to take them tomorrow at eight in the evening."

The system should interpret this as two related but distinct operations:

1. Create a memory about the earbuds' recorded location.

2. Create a reminder to take the earbuds at the requested time.

The application must not silently discard either operation if parsing fails.

If the date or time is ambiguous, the user should be asked to confirm it. The application should display the interpreted memory and reminder before finalizing ambiguous or consequential actions.

## 5.6 Remembering a file location or reference

A user records that a particular project document is inside a specified folder.

The system should save the reference and relevant context without requiring the user to memorize the complete path.

Sensitive directories must be excluded from automatic indexing by default where appropriate. The application must not store passwords or other credentials in plaintext as a substitute for a dedicated credential manager.

## 5.7 Searching through incomplete recollection

A user remembers that they once compared two products but cannot remember their names.

They search:

"That document about choosing a new dust blower."

The engine should search available content, generated context, related memories, and metadata. If it cannot find a convincing match, it should communicate that limitation rather than fabricate an answer.

# 6. Product Scope

## 6.1 Initial scope

The initial Android application will focus on:

- Structured personal memory capture.

- Free-text notes.

- Physical-item and location memories.

- File references and selected-folder indexing.

- Document text extraction where supported.

- Contextual search and relevance ranking.

- A default memory relationship graph.

- Reminders associated with memories.

- File-reference health checks.

- Configurable physical-item confirmation schedules.

- Local speech recognition where supported.

- Home-screen and Quick Settings capture.

- Share-sheet integration.

- Local database management and export.

- Transparent background processing.

- Open-source documentation and automated tests.

## 6.2 Explicitly out of scope for the first version

The first release should not depend on:

- A hosted backend.

- Cloud synchronization.

- Online AI inference.

- A mandatory user account.

- A community or social network.

- An expensive subscription infrastructure.

- A custom-trained neural network.

- A Python runtime embedded inside the Android application.

- Unrestricted filesystem access.

- Automatic claims about the real-time location of physical belongings.

- Autonomous modification of unrelated files or system settings.

These are deliberate exclusions intended to keep the project achievable, testable, and privacy-preserving.

# 7. Product Pillars

## 7.1 Capture

Capture converts an unstructured thought into a useful local memory.

Possible inputs include:

- Typed text.

- Voice transcripts.

- Shared text from other applications.

- Selected documents and file references.

- Structured inventory entries.

- Explicitly entered locations and reminders.

Capture should preserve the original user input. Extracted fields, keywords, summaries, and relationships should be stored separately so that derived information can be corrected or regenerated.

## 7.2 Recall

Recall retrieves relevant memories using more than literal text matching.

The retrieval engine should consider:

- Original memory text.

- Extracted document content.

- Generated contextual descriptions.

- Synonyms and related concepts.

- Filenames and metadata.

- Memory relationships.

- Temporal relevance.

- File availability.

- User confirmation and freshness.

The system must not treat a semantically related result as automatically correct. Retrieval ranking and result confidence are different concepts and should be implemented separately.

## 7.3 Verify

Verification keeps the system honest.

WhereWasIt? should distinguish:

- What the user explicitly recorded.

- What the application extracted from a source.

- What the application inferred.

- What the user later confirmed.

- What is no longer verifiable.

For example, a recorded location may be accurate as a historical statement while no longer being reliable as a current location.

The application must preserve this distinction in storage, ranking, and presentation.

# 8. Functional Requirements

The following identifiers should be retained in implementation issues and acceptance tests.

## FR-01: Local memory creation

The application must allow users to create, view, edit, and delete memories without network access.

Every memory must have a stable identifier and creation timestamp.

## FR-02: Structured memory types

The system should support at least these logical types:

- General note.

- Physical item.

- Location.

- File reference.

- Document context.

- Reminder.

- User-defined category where practical.

These types should share a common memory foundation rather than requiring independent storage systems.

## FR-03: Original-content preservation

The original user-provided text must remain distinguishable from generated summaries, extracted fields, and inferred relationships.

Regenerating context must never silently replace the original record.

## FR-04: Context generation

For supported input types, the system should derive useful searchable context.

Examples include:

- Item names.

- Locations.

- Quantities.

- Dates and times.

- Relevant entities.

- Document topics.

- Keywords.

- Synonyms.

- Relationships to other memories.

Context extraction should be deterministic where practical, and uncertain extraction must not be represented as user-confirmed information.

## FR-05: Document indexing

The application should allow users to select permitted files and folders for indexing.

The indexer should extract supported metadata and document text, generate searchable context, and record the original source reference.

Unsupported or corrupted files must fail gracefully.

## FR-06: Contextual retrieval

The search engine must support ranked results based on multiple signals rather than exact matches alone.

The search interface should allow natural-language queries without requiring a predefined query syntax.

## FR-07: Relationship graph

Relationships between memories must be represented explicitly in the data model.

Examples include:

- Item → stored at → location.

- Memory → references → file.

- Memory → has attachment → document.

- Memory → associated with → reminder.

- Memory → related to → another memory.

The graph is part of the core architecture, not a feature to be postponed indefinitely. Initial relationships may be stored in relational tables; a dedicated graph database is unnecessary.

## FR-08: File health

The system should check the availability of indexed file references when permitted.

Unavailable references should be marked accordingly, with user-facing recovery options.

## FR-09: Physical-item check-ins

Users should be able to configure optional confirmation schedules for physical-item memories.

Supported intervals may include daily, every three days, weekly, or disabled.

A check-in must request confirmation; it must not assume that an item remains at its recorded location.

## FR-10: Reminders

The application must support reminders linked to one or more memories.

Reminder creation, scheduling, completion, cancellation, and rescheduling must be separate operations.

Android may delay background work because of battery management and system policies. The interface must not promise exact delivery when the platform cannot guarantee it.

## FR-11: Voice capture

Where available, the application should use Android's on-device speech-recognition facilities.

The implementation must verify that the chosen recognizer is operating locally before relying on it for privacy-sensitive capture.

If local recognition is unavailable, the application must not silently switch to a cloud recognizer.

Typing remains an available fallback.

## FR-12: Quick capture

The application should provide progressively implemented low-friction entry points:

- In-app capture.

- Home-screen widget.

- Quick Settings tile.

- Android share-sheet target.

- Voice capture.

Capture controls must perform real actions and must not be decorative placeholders.

## FR-13: Background processing

The application should support incremental indexing, context generation, and memory-health checks without unnecessarily interrupting the user.

Background tasks must expose their state where useful and recover safely after interruption.

## FR-14: User control

Users should be able to inspect, correct, delete, and export their stored information.

The system should provide a way to remove indexed content and generated context when a memory or source is deleted.

## FR-15: Offline operation

All essential functions must remain available without internet access, except for explicitly documented platform-dependent capabilities that cannot be provided locally.

The core application must not require a remote API to create or search ordinary memories.

# 9. Non-Functional Requirements

## NFR-01: Privacy

Personal memories and indexed document content must not be transmitted to remote services by default.

No analytics, advertising SDKs, or telemetry should be included in the initial release unless the privacy policy and implementation are deliberately revised.

## NFR-02: Performance

Search should operate over a maintained local index rather than repeatedly crawling the filesystem.

The application should be benchmarked against increasing numbers of memories and indexed documents.

Performance targets must be measured on representative devices rather than assumed.

## NFR-03: Reliability

Database writes, indexing jobs, and reminder operations must recover safely from interruptions, process termination, and device restarts where supported.

Duplicate processing must not create duplicate memories or reminders.

## NFR-04: Maintainability

The codebase must use modular responsibilities, explicit interfaces, and testable domain logic.

The UI must not contain the entire search algorithm, document parser, or persistence implementation.

## NFR-05: Accessibility

The application should support Android accessibility services, readable text, scalable layouts, appropriate contrast, and non-voice alternatives.

## NFR-06: Resource efficiency

Indexing and context generation must be incremental where possible. Unchanged files should not be reprocessed unnecessarily.

Memory, CPU, storage, and battery consumption should be measured.

## NFR-07: Transparency

The project should document its permissions, dependencies, local processing behavior, indexing rules, limitations, and privacy tests.

## NFR-08: Data integrity

The application must preserve stable identifiers, source references, timestamps, and relationships consistently.

A failed context-generation job must not corrupt or delete the original memory.

# 10. Technical Architecture

## 10.1 Platform and language

The initial implementation will use:

- Java.

- Native Android UI components.

- Android SDK APIs.

- SQLite.

- SQLite FTS5 where available in the selected database implementation.

- Android document-access APIs.

- Android scheduling and notification APIs.

- Compatible local document parsers.

Kotlin is not required.

Python is not required for the Android runtime or the initial retrieval engine.

## 10.2 Architectural layers

### Layer A: Presentation

Responsibilities:

- Search interface.

- Memory editor.

- Quick capture.

- Memory detail view.

- Relationship exploration.

- File-health status.

- Reminder management.

- Indexing progress and settings.

This layer should render domain state and send explicit user actions to the application layer.

### Layer B: Application services

Responsibilities:

- Create and update memories.

- Coordinate capture pipelines.

- Manage reminder operations.

- Coordinate file indexing.

- Schedule health checks.

- Request user confirmation when needed.

- Coordinate privacy-sensitive operations.

This layer should orchestrate work rather than implement every algorithm itself.

### Layer C: Domain and memory engine

Responsibilities:

- Memory models.

- Context records.

- Entity and location extraction.

- Relationship management.

- Query normalization.

- Synonym expansion.

- Candidate retrieval.

- Relevance ranking.

- Result explanations.

- Freshness and trust evaluation.

This layer should remain independent of Android UI classes.

### Layer D: Data and indexing

Responsibilities:

- SQLite persistence.

- Full-text indexing.

- Transaction management.

- Incremental index updates.

- Document metadata.

- Source references.

- Search history only if explicitly enabled and justified.

- Database migrations.

- Export and deletion.

### Layer E: Android platform adapters

Responsibilities:

- Document selection and access.

- Native speech-recognition integration.

- Notifications.

- WorkManager integration.

- Widgets and Quick Settings.

- Share-sheet handling.

- Battery and charging state.

- Platform-specific permission handling.

These adapters should expose small interfaces to the rest of the application.

## 10.3 Suggested package organization

The exact package names may evolve, but the separation should remain recognizable.

    app/

      ui/

        search/

        memory/

        capture/

        reminders/

        settings/

      application/

        MemoryService.java

        CaptureService.java

        SearchService.java

        ReminderService.java

        IndexingService.java

      domain/

        model/

        relations/

        retrieval/

        context/

        trust/

      data/

        database/

        repository/

        indexing/

        parsing/

        export/

      platform/

        speech/

        notifications/

        scheduling/

        storage/

        widgets/

        sharing/

      tests/

This is a logical organization, not a requirement to create empty classes or folders prematurely.

## 10.4 Architectural boundaries

The following dependencies should be avoided:

- UI classes directly executing complex SQL queries.

- Search ranking coupled to a particular screen.

- Document parsers writing arbitrary database records.

- Speech-recognition callbacks directly creating reminders without validation.

- Background workers modifying UI state directly.

- Generated summaries replacing original content.

- Platform-specific code leaking into every domain class.

Use interfaces and dependency injection where they provide clear value. Do not introduce a large framework solely for architectural appearance.

# 11. Data Model

The database design should support the current scope while allowing new memory types to be added without a full redesign.

The following entities are conceptual. Exact column types, indexes, and migrations should be defined during implementation.

## 11.1 Memory

The primary user-visible record.

Suggested fields:

- `id`: stable unique identifier.

- `type`: note, physical item, location, file reference, or other supported type.

- `original\\_text`: original user-provided content.

- `title`: optional display title.

- `created\\_at`: creation timestamp.

- `updated\\_at`: last modification timestamp.

- `source\\_type`: typed, voice, shared content, imported file, or another source.

- `source\\_id`: optional reference to the originating source.

- `status`: active, archived, deleted, or another defined state.

- `last\\_confirmed\\_at`: optional confirmation timestamp.

The original text should not be overwritten by generated context.

## 11.2 ContextRecord

Contains derived searchable information.

Suggested fields:

- `id`.

- `memory\\_id`.

- `context\\_type`: summary, keyword set, extracted entities, document content, or similar.

- `content`.

- `generator\\_version`.

- `created\\_at`.

- `updated\\_at`.

- `generation\\_status`.

Context records should be reproducible when possible. A failed regeneration must leave the previous valid record and original memory intact until replacement succeeds.

## 11.3 Entity

Represents a normalized concept or extracted entity.

Examples include:

- USB-C cable.

- Earbuds.

- Guitar.

- Wardrobe.

- Desk drawer.

- A named project.

Suggested fields:

- `id`.

- `canonical\\_label`.

- `entity\\_type`.

- Optional normalization metadata.

Normalization must preserve the original user wording separately.

## 11.4 MemoryEntity

Connects memories to entities.

Suggested fields:

- `memory\\_id`.

- `entity\\_id`.

- `role`: subject, location, object, topic, or another defined role.

- `provenance`.

- `confidence`, where appropriate.

An extracted entity should not automatically be considered a verified fact.

## 11.5 MemoryRelation

Represents relationships between memories or entities.

Suggested fields:

- `id`.

- `source\\_type`.

- `source\\_id`.

- `relation\\_type`.

- `target\\_type`.

- `target\\_id`.

- `provenance`.

- Optional confidence.

- `created\\_at`.

- `last\\_confirmed\\_at`.

Example relations:

- `STORED\\_AT`.

- `REFERENCES\\_FILE`.

- `HAS\\_ATTACHMENT`.

- `ASSOCIATED\\_WITH\\_REMINDER`.

- `RELATED\\_TO`.

- `SUPERSEDES`.

Relationship types should be defined centrally rather than scattered as inconsistent string literals.

## 11.6 FileReference

Tracks an indexed file or document.

Suggested fields:

- `id`.

- `uri\\_or\\_path`.

- `display\\_name`.

- `mime\\_type`.

- `size\\_bytes`.

- `last\\_modified\\_at`, where available.

- `last\\_indexed\\_at`.

- `content\\_fingerprint`, where appropriate.

- `access\\_state`.

- `last\\_verified\\_at`.

- `indexing\\_status`.

- `exclusion\\_status`.

On Android, a content URI and persisted access permission may be more appropriate than a raw filesystem path. The application must not assume unrestricted access to every file.

## 11.7 Reminder

Represents a scheduled action.

Suggested fields:

- `id`.

- `memory\\_id`, where applicable.

- `title`.

- `scheduled\\_at`.

- `time\\_zone\\_id`, where appropriate.

- `status`.

- `created\\_at`.

- `completed\\_at`.

- `last\\_scheduled\\_at`.

Reminder scheduling should be coordinated with Android platform APIs. The database remains the authoritative record of the user's intended reminder.

## 11.8 ConfirmationPolicy

Stores optional freshness-check settings for a memory.

Suggested fields:

- `memory\\_id`.

- `enabled`.

- `interval\\_type`.

- `interval\\_value`.

- `last\\_prompted\\_at`.

- `next\\_check\\_at`.

The scheduling system must tolerate missed execution windows and reschedule work when necessary.

## 11.9 IndexJob

Tracks long-running or resumable indexing operations.

Suggested fields:

- `id`.

- `source\\_id`.

- `status`.

- `progress`.

- `started\\_at`.

- `updated\\_at`.

- `error\\_code`.

- Optional diagnostic details that do not unnecessarily expose private document content.

## 11.10 Database integrity

The implementation must define:

- Primary keys and foreign keys.

- Appropriate indexes.

- Database migrations.

- Cascading-deletion rules.

- Transaction boundaries.

- Duplicate-detection behavior.

- Recovery after interrupted indexing.

- Cleanup of obsolete full-text and contextual records.

Deleting a memory should also remove or appropriately detach its generated context and relationships. Shared sources must not be deleted merely because one memory referencing them is removed.

# 12. Retrieval Engine

Retrieval accuracy is one of the project's most important engineering challenges.

The system must not equate finding a matching word with finding the correct memory.

## 12.1 Retrieval pipeline

A search request should pass through these stages:

1. Receive the user's query.

2. Normalize the text without destroying its meaning.

3. Identify relevant terms, entities, and potential intent.

4. Expand selected terms using a local synonym and related-concept dictionary.

5. Retrieve candidates from the full-text index and relevant metadata.

6. Retrieve additional candidates through appropriate memory relationships.

7. Apply fuzzy matching where useful.

8. Combine the available relevance signals.

9. Evaluate freshness, source reliability, and uncertainty separately.

10. Rank and deduplicate candidates.

11. Return the best results with useful explanations.

The implementation should avoid scanning every indexed file or rebuilding every context record during each search.

## 12.2 Candidate retrieval

The initial engine should use multiple retrieval methods.

### Full-text retrieval

Use SQLite FTS5, or an equivalent maintained local full-text index, to search original text and indexed context.

BM25-style ranking can help prioritize documents with stronger query-term relevance.

### Synonym and concept expansion

Maintain a local, versioned vocabulary that maps related terms to common concepts.

Examples:

- Earbuds ↔ earphones.

- Wardrobe ↔ closet.

- Dust blower ↔ air duster, where appropriate.

- Notebook ↔ laptop, only in contexts where the meanings are genuinely compatible.

Synonyms must be context-sensitive enough to avoid treating every related word as interchangeable.

### Metadata retrieval

Use fields such as:

- Memory type.

- Filename.

- Creation date.

- File location.

- Extracted entity.

- Recorded physical location.

- Confirmation timestamp.

### Relationship retrieval

Search through explicit links between memories and entities.

For example, a query about a reminder may lead to its associated memory, while a query about a file may lead to the document's extracted context.

Relationship traversal must be bounded and must not overwhelm direct matches.

### Fuzzy matching

Use fuzzy matching for misspellings, partial names, and small wording variations.

Fuzzy matching should not be treated as semantic understanding.

## 12.3 Ranking model

The first version should implement a measurable, tunable ranking system.

An illustrative scoring function is:

    final_score =

        0.50 * original_text_score

      + 0.30 * contextual_score

      + 0.15 * metadata_score

      + 0.05 * fuzzy_match_score

These weights are starting hypotheses, not established optimal values.

The implementation must normalize signals to compatible ranges before combining them. BM25 scores, string similarities, and metadata matches cannot be safely combined without considering their different scales.

The weights must be tuned using a manually curated query-and-answer test set.

The ranking model may later incorporate relationship relevance and other features, provided the additional signals are independently evaluated and do not cause existing capabilities to regress.

## 12.4 Relevance versus trust

The most relevant result is not necessarily the most trustworthy or current result.

For example, a memory about a desk drawer may be an excellent semantic match while its location was last confirmed six months ago.

The system should calculate or represent these dimensions separately:

- Retrieval relevance.

- Source provenance.

- User confirmation.

- Temporal freshness.

- File availability.

- Extraction or inference uncertainty.

The UI can use these dimensions to present an appropriate status without arbitrarily hiding the most relevant result.

## 12.5 Search explanations

Where practical, each result should identify useful reasons for its appearance.

Examples:

- Matched the original note.

- Matched a synonym.

- Found a related memory.

- Matched extracted document content.

- Matched a filename.

- Matched a recorded location.

Explanations should describe the actual retrieval signals used. The application must not invent a convincing-sounding explanation after the ranking decision.

## 12.6 Retrieval evaluation

Create a local benchmark containing:

- Representative memories.

- Direct queries.

- Indirect queries.

- Synonym-based queries.

- Queries with spelling mistakes.

- Queries that refer to relationships.

- Ambiguous queries.

- Queries with no correct result.

- Memories with outdated or conflicting information.

Measure:

- Recall@1.

- Recall@3.

- Recall@5.

- Mean reciprocal rank.

- Search latency.

- False-positive rate on defined test cases.

- Performance as the index grows.

For a query with multiple valid answers, the benchmark must allow multiple relevant results.

The goal is not simply to make the result list look plausible. It is to improve measurable retrieval quality without compromising privacy or responsiveness.

# 13. Context Extraction and Indexing

## 13.1 Context generation

Context generation converts raw records into additional searchable representations.

For a physical-item memory, a derived record might contain:

- Item: spare USB-C cable.

- Location: second desk drawer.

- Context: equipment needed for campus.

- Related entities: desk, drawer, campus equipment.

Only the information actually present in the memory should be extracted as fact. A context association such as "needed for campus" must not be invented if the user never supplied that relationship.

Generated context is an aid to retrieval, not a replacement for the original record.

## 13.2 Document processing

The document-processing pipeline should:

1. Obtain permission to access the selected document.

2. Read supported metadata.

3. Extract text using a compatible local parser.

4. Detect extraction failures.

5. Create a searchable document-content record.

6. Generate keywords and contextual descriptions using local logic.

7. Associate the context with the original file reference.

8. Update the index transactionally.

9. Record the indexing outcome.

Supported formats should be selected according to tested Android compatibility. PDF and DOCX are initial candidates; other formats can be added later.

Large files must be processed with memory and battery limits in mind.

Scanned PDFs may require OCR, which is a separate capability and should not be assumed to work through ordinary text extraction.

## 13.3 Incremental indexing

The system should avoid processing unchanged content repeatedly.

It may compare available metadata, timestamps, size, or content fingerprints to determine whether reprocessing is needed.

No single signal is guaranteed to detect every modification. The indexer should periodically reconcile its records against the sources it is permitted to access.

If an indexing job fails, the system must preserve the last valid index where possible and expose an appropriate status.

# 14. Voice Capture and Intent Interpretation

## 14.1 Speech recognition

Prefer Android's on-device speech-recognition capabilities when supported.

The implementation must explicitly verify offline availability and must not assume that the default keyboard's offline voice input implies that every application-level recognizer will behave identically.

If the required on-device recognizer or language model is unavailable, offer text input or a separately installed offline engine.

Do not silently fall back to online speech recognition.

## 14.2 Structured interpretation

After receiving a transcript, a local Java parser should attempt to identify:

- The intended operation.

- The subject or object.

- The recorded location.

- Quantities.

- Relevant dates and times.

- Reminder intent.

- Relationships between the resulting records.

For example:

"Remember that my spare cable is in the second drawer. Remind me to take it tomorrow at 8 PM."

The parser should identify a memory operation and a separate reminder operation.

It should preserve the transcript, display the interpreted fields when confirmation is needed, and provide a recovery path when interpretation fails.

## 14.3 Date and time interpretation

Relative dates such as "tomorrow" must be resolved using the device's local date and time.

The system should handle:

- Local time zones.

- Daylight-saving changes where applicable.

- Missing or ambiguous times.

- Invalid dates.

- Device restart and rescheduling.

- Changes to a reminder after creation.

The UI should make the resolved date and time visible when ambiguity could materially affect the user's intention.

## 14.4 Offline language support

Language support must be tested explicitly.

English, Bangla, and Banglish should not be assumed to have equal recognition quality or availability on every device.

If speech recognition produces an inaccurate transcript, the user should be able to correct it before the application commits important changes.

## 14.5 Future local language processing

More advanced parsing or entity extraction may be added later, including local machine-learning models.

Such models must not be required to operate the initial product, and any model must be evaluated against a test set before replacing a working deterministic component.

# 15. Trust and Memory Freshness

## 15.1 Provenance

Every important memory or derived claim should have a traceable origin.

Possible provenance types include:

- User-entered.

- Voice-transcribed.

- Extracted from a document.

- Imported from another source.

- Inferred by deterministic rules.

- Generated by a future local model.

- Confirmed by the user.

Provenance must be represented in data rather than inferred from UI styling.

## 15.2 Freshness

Freshness should depend on the type of memory.

A physical-item location may need periodic confirmation. A historical note may remain valid indefinitely. A file reference may need verification after access failures or metadata changes.

Do not impose one universal expiration interval on every memory.

## 15.3 File-health status

Useful file states include:

- Accessible.

- Not yet verified.

- Temporarily inaccessible.

- Missing or unavailable.

- Permission revoked.

- Re-indexing required.

The UI should not conflate permission denial with deletion.

## 15.4 Conflicting memories

When a user records a new location for an existing item, the application should not silently erase the historical record.

It should support an update or supersession relationship so that the newest confirmed location can be prioritized while preserving useful history.

## 15.5 Physical-item verification

The application cannot independently know where an ordinary physical object is located unless additional, reliable sensing technology is available.

It must never imply that it has visually or physically verified an item's location.

A confirmation prompt should allow the user to affirm, update, or mark the location as unknown.

# 16. Privacy and Security Model

Privacy is a core product requirement, not a marketing layer added after implementation.

## 16.1 Local data storage

The default implementation should store memories, contextual records, search indexes, and relationships in a local database.

The application must not upload those records to a remote database or AI service as part of normal operation.

## 16.2 Network behavior

The initial release should avoid unnecessary network permissions and remote dependencies.

The project should include tests and documentation examining whether its own code and bundled dependencies attempt network communication.

If a feature requires network access, it must be reviewed separately rather than quietly introducing a new network dependency.

Open-source code alone does not prove that a distributed application has no unexpected network behavior. Reproducible builds, dependency auditing, runtime testing, and clear release documentation would strengthen the claim.

## 16.3 Sensitive content

The application must not automatically index obvious credential stores, password databases, private-key directories, or other excluded sensitive locations.

It must not encourage users to store passwords, authentication tokens, or private keys as ordinary memory text.

Sensitive memories should receive an explicit security design. Local storage is not automatically encrypted, and database encryption should be evaluated separately from the basic storage architecture.

## 16.4 Permissions

Request only the permissions necessary for an enabled feature.

For example:

- Use Android's document picker or Storage Access Framework for user-selected files.

- Request microphone access when voice capture is used.

- Request notification permission where required.

- Use platform-supported scheduling and background mechanisms.

The application must not assume it has unrestricted access to Android storage.

## 16.5 Data deletion and export

Users should be able to delete memories and their derived context.

The application should support a documented export format for portability and debugging, with clear warnings if the export contains sensitive information.

Deleting a record must remove its searchable representations, including full-text index entries and generated context where applicable.

## 16.6 Privacy verification

The repository should include:

- A clear data-flow diagram.

- A permissions inventory.

- A dependency inventory.

- A description of background processing.

- Tests for accidental network access.

- Documented local storage locations.

- A list of excluded directories and file types.

- A reproducible build process where practical.

- A transparent account of known limitations.

The intended privacy promise is:

**"Your data, owned by you, never escapes from you."**

This wording should only be used as an absolute claim once the shipped implementation, dependencies, platform integrations, and release process support it. Until then, describe the actual guarantees and known limitations precisely.

# 17. Background Processing and Battery Management

Background processing should improve the quality of the memory index without making the phone feel slower or draining its battery unnecessarily.

## 17.1 Suitable background work

Examples include:

- Incremental document indexing.

- Context generation.

- File-reference health checks.

- Database maintenance.

- Scheduled physical-item confirmation prompts.

- Recovery of interrupted indexing jobs.

## 17.2 Scheduling strategy

Use Android's supported background scheduling mechanisms, such as WorkManager, for deferrable work.

The initial policy should prefer indexing when the device is idle or otherwise suitable for background work, subject to platform capabilities.

The proposed battery policy is:

- Do not start discretionary indexing while battery charge is at or below 40%.

- Prefer charging or suitable idle periods when practical.

- Recheck battery state when a job starts.

- Defer or reschedule work if the requirements are not met.

- Allow the user to adjust appropriate settings where useful.

A custom 40% threshold requires an explicit runtime check; it is not the same as Android's generic battery-not-low constraint.

WorkManager does not guarantee execution at an exact time. The application must handle deferral, cancellation, process termination, and retries.

## 17.3 Transparency

The UI should provide:

- Whether indexing is active.

- What kind of work is occurring.

- Approximate progress where measurable.

- Whether work is deferred.

- Whether permissions are missing.

- How the user can pause or change indexing.

Notifications should be used when required by the platform or genuinely useful. Do not keep an unnecessary permanent notification solely to create the appearance of transparency.

## 17.4 Background-work correctness

Jobs must be:

- Idempotent where practical.

- Safe to retry.

- Incremental.

- Cancellable.

- Resumable after interruption.

- Careful with database transactions.

- Restricted to permitted source files.

Background tasks must not silently change the meaning of a memory or confirm an unverified physical location.

# 18. User Experience and Interaction Design

The interface should feel like a polished native Android product, with strong visual hierarchy and minimal friction.

Premium styling must not compromise accessibility, clarity, performance, or implementation maintainability.

## 18.1 Primary navigation

A possible initial structure is:

- **Search:** the default entry point.

- **Memories:** browse and manage recorded information.

- **Activity:** recent capture, indexing, and confirmation activity where useful.

- **Settings:** privacy, permissions, indexing, export, and preferences.

This is a starting point, not a requirement to expose every section in the first prototype.

## 18.2 Search experience

The search screen should:

- Allow natural-language queries.

- Present relevant results quickly.

- Display concise context explaining each result.

- Identify stale or inaccessible information.

- Distinguish confirmed facts from inferred context.

- Provide a path to the original source.

- Avoid overwhelming the user with raw diagnostic scores.

## 18.3 Capture experience

Capture should accept an unstructured sentence without forcing users to select a category first.

The application can infer a suggested memory type and present editable structured fields afterward.

If the input is ambiguous, the application should ask a targeted question rather than presenting a long form.

## 18.4 Memory detail

A memory detail view should expose, where relevant:

- Original content.

- Structured fields.

- Related memories.

- Source references.

- Creation and modification timestamps.

- Last confirmation time.

- File availability.

- Associated reminders.

- Derived context.

- Edit, delete, and export actions.

## 18.5 Accessibility and feedback

Voice input must not be the only capture method.

All essential features should remain usable with touch input, accessible text sizes, screen readers, and clear visual feedback.

# 19. Performance and Quality Targets

The project should use measurable goals instead of relying on subjective impressions.

Initial engineering targets can be proposed as follows and revised after baseline measurements:

- Search latency: target a p95 below 200 ms for a representative local-memory benchmark on a defined reference device.

- Capture latency: aim for a simple typed memory to be saved and searchable within one second under normal conditions.

- Indexing: avoid reprocessing unchanged sources unnecessarily.

- Retrieval quality: establish a labeled benchmark before optimizing ranking weights.

- Data integrity: no lost original memories when context generation fails.

- Offline behavior: create and search memories with internet connectivity disabled.

- Privacy: no unexpected outbound transmission of memory content.

- Recovery: interrupted indexing must resume or restart without corrupting the existing index.

These are development targets, not guarantees about all Android devices. The repository should record test-device specifications, dataset size, and measurement methodology alongside performance results.

# 20. Open-Source Strategy

The project will be developed openly so that its architecture, implementation, and privacy claims can be inspected.

## 20.1 Repository contents

The repository should include:

- Application source code.

- Build configuration.

- Architecture documentation.

- Database schema and migration history.

- Retrieval design and evaluation methodology.

- Privacy and security documentation.

- Automated tests.

- Test fixtures that do not contain private user data.

- A clear setup guide.

- Contribution guidelines.

- License information.

- Release notes and known limitations.

## 20.2 Transparency requirements

The project should document:

- What data is stored.

- Where it is stored.

- Which files are indexed.

- How text is extracted.

- How search ranking works.

- Which speech-recognition APIs are used.

- Which dependencies are bundled.

- What background jobs do.

- What permissions are required.

- Which operations may be delayed by Android.

- What information is not accessible to the application.

## 20.3 Licensing

Choose an explicit open-source license before publishing the first public release.

The license should be selected intentionally based on the project's goals for reuse, modification, distribution, and attribution. Do not assume that publishing source code without a license grants unrestricted reuse rights.

## 20.4 Contributions

Contributions should follow the same standards as the core project:

- No mandatory cloud dependencies.

- No unreviewed network calls.

- No unexplained permission expansion.

- Tests for important algorithmic changes.

- Clear migration behavior for database changes.

- No fabricated claims about privacy or retrieval quality.

# 21. Development Roadmap

The roadmap should prioritize a complete, testable core before adding sophisticated optional features.

## Phase 1: Foundation

Implement:

- Android Java project structure.

- Core domain models.

- SQLite persistence.

- Database migrations.

- Basic memory creation, editing, and deletion.

- Original-content preservation.

- Unit tests for data integrity.

**Completion criterion:** users can create and manage ordinary memories entirely offline.

## Phase 2: Retrieval foundation

Implement:

- Full-text indexing.

- Query normalization.

- BM25-based ranking where supported.

- Basic metadata search.

- Initial synonym dictionary.

- Ranked result presentation.

- Search benchmark fixtures.

**Completion criterion:** a test suite demonstrates that relevant memories can be retrieved through direct and selected indirect queries.

## Phase 3: Context and memory relationships

Implement:

- Structured context records.

- Entity extraction using local deterministic logic.

- Memory relationships.

- Related-memory retrieval.

- Result explanations.

- Context regeneration and error handling.

**Completion criterion:** a query can retrieve useful information through supported synonyms and explicit memory relationships without relying solely on exact phrase matches.

## Phase 4: File indexing

Implement:

- User-selected file and folder access.

- Metadata extraction.

- Compatible PDF and DOCX parsing.

- Text-content indexing.

- Incremental updates.

- Exclusion rules.

- File-reference health checks.

- Indexing progress and failure reporting.

**Completion criterion:** supported documents can be indexed and searched locally, with permissions and unavailable sources handled honestly.

## Phase 5: Trust and freshness

Implement:

- Provenance tracking.

- Confirmation timestamps.

- Freshness indicators.

- Conflicting-memory handling.

- Physical-item confirmation policies.

- File-health recovery flows.

**Completion criterion:** the interface distinguishes historical records from currently confirmed information.

## Phase 6: Low-friction capture

Implement:

- Home-screen widget.

- Quick Settings tile.

- Share-sheet integration.

- Voice capture using verified on-device recognition where available.

- Local intent and date parsing.

- Reminder creation and management.

- Confirmation for ambiguous operations.

**Completion criterion:** users can capture and retrieve ordinary memories without navigating through unnecessary forms.

## Phase 7: Background processing and privacy verification

Implement:

- WorkManager-based incremental processing.

- Battery-aware scheduling.

- Resumable jobs.

- Runtime and automated network checks.

- Permission auditing.

- Sensitive-directory exclusions.

- Data export and deletion tests.

**Completion criterion:** the application can maintain its index transparently without depending on cloud services.

## Phase 8: Optimization and optional ML research

After collecting retrieval benchmarks:

- Identify failure cases.

- Improve the vocabulary and ranking strategy.

- Evaluate alternative local retrieval methods.

- Experiment with embeddings or local models only if justified.

- Measure additional latency, storage, battery, and memory costs.

- Preserve the existing conventional retrieval path as a fallback where appropriate.

**Completion criterion:** any advanced algorithm demonstrates a measurable benefit without violating the product's offline-first requirements.

# 22. Testing Strategy

## 22.1 Unit tests

Test:

- Query normalization.

- Synonym expansion.

- Ranking functions.

- Context extraction.

- Date parsing.

- Relationship traversal.

- Freshness rules.

- Duplicate detection.

- Reminder state transitions.

## 22.2 Database tests

Test:

- Creation and editing.

- Transaction rollback.

- Migration between schema versions.

- Foreign-key integrity.

- Deletion of derived context.

- Recovery after interrupted indexing.

- Consistency between the main database and full-text index.

## 22.3 Integration tests

Test:

- Document selection and parsing.

- Speech-recognition availability and failure.

- Memory creation from capture.

- Reminder scheduling.

- File-permission revocation.

- Background indexing.

- Device restart and job rescheduling.

## 22.4 Retrieval tests

Use a fixed, version-controlled dataset of fictional memories and labeled queries.

Include indirect wording, synonyms, spelling errors, conflicting memories, outdated locations, and queries with no valid answer.

Ranking changes must be evaluated against the same benchmark so that regressions are detectable.

## 22.5 Privacy tests

Verify:

- Ordinary memory operations work without internet access.

- No unexpected network requests are made.

- No memory text is sent to remote services.

- Sensitive exclusions work.

- User-selected file permissions are respected.

- Data deletion removes derived searchable content.

- Logs do not expose unnecessary personal information.

## 22.6 Manual testing

Test on real Android devices with different OS versions and manufacturers.

Validate background scheduling, notification permissions, offline speech recognition, file-access behavior, battery constraints, and accessibility.

# 23. Known Limitations and Risks

The project should openly acknowledge the following constraints.

### Contextual search is not perfect semantic understanding

Synonyms, ranking, and relationships can improve retrieval, but they cannot guarantee that every indirect query will be interpreted correctly.

### Android restricts filesystem access

The application can only index and verify files it is permitted to access. File movement and deletion detection may be incomplete.

### Speech recognition varies by device and language

On-device recognition may be unavailable or inaccurate for some languages, devices, or configurations.

### Background execution is controlled by Android

The application cannot guarantee arbitrary execution times or uninterrupted background processing.

### Local storage does not automatically provide encryption

The privacy model must account for device compromise, backups, exports, database files, and sensitive data.

### Open source does not guarantee correct implementation

Privacy and security require code review, testing, careful dependency selection, and accurate release practices.

### More sophisticated ML may increase complexity

Embeddings and local neural models can improve selected retrieval tasks, but they also introduce costs and must justify their inclusion through evaluation.

# 24. Future Direction

The following are possible future extensions, not commitments for the initial release:

- Improved local semantic retrieval.

- Optional local embedding models.

- Better multilingual intent extraction.

- OCR for supported scanned documents.

- Advanced relationship exploration.

- More granular memory-retention policies.

- Encrypted export and import.

- Explicitly designed local-network transfer between a user's devices.

- A future desktop client using a compatible shared domain model.

- Optional Python-based model experimentation and evaluation.

Any future synchronization feature must have a separate threat model and must not silently undermine the original offline-first promise.

# 25. Coding-Assistant Instructions

This section defines the expected behavior of AI coding assistants and contributors working on the repository.

## 25.1 General implementation rules

1. Read the relevant architecture and feature requirements before modifying code.

2. Use Java for the Android application unless a specific, documented reason requires another language.

3. Do not introduce Kotlin merely for convenience.

4. Do not introduce Python as an Android runtime dependency.

5. Do not add cloud services or online AI dependencies to the core application.

6. Prefer native Android APIs and small, well-understood libraries.

7. Keep the UI, application services, domain logic, persistence, and platform adapters separated.

8. Preserve original user content independently from derived context.

9. Implement the memory relationship model as part of the core architecture.

10. Make important algorithms testable independently of the UI.

11. Use explicit database migrations instead of silently changing existing schemas.

12. Avoid implementing large amounts of speculative infrastructure before a working feature needs it.

## 25.2 Algorithm and workflow documentation

Every non-trivial algorithm must include comments or accompanying documentation explaining:

- The problem it solves.

- Its inputs and outputs.

- The steps it performs.

- Why the chosen method is appropriate.

- Its assumptions.

- Its limitations.

- Relevant complexity or resource considerations.

- How it is tested.

Comments should explain reasoning and non-obvious behavior, not merely repeat the syntax of each line.

When implementing a new algorithm, provide a clear workflow explanation so the developer can understand how data moves through the system.

## 25.3 Privacy rules

- Never add an unreviewed network call.

- Never silently switch offline functionality to a cloud service.

- Never log full memory contents unnecessarily.

- Never index excluded sensitive directories without explicit, informed user action.

- Never treat local storage as automatically encrypted.

- Never claim that an item's current physical location has been verified without user confirmation or an actual reliable verification mechanism.

- Never claim that a file is deleted when the application only knows it is inaccessible.

- Never store credentials as ordinary plaintext memories.

## 25.4 Retrieval rules

- Do not implement exact-keyword matching as the only retrieval mechanism.

- Do not claim that fuzzy matching is equivalent to semantic understanding.

- Do not treat retrieval relevance as factual confidence.

- Do not fabricate a result when no convincing match exists.

- Do not use arbitrary scoring weights without measuring their behavior.

- Do not introduce a neural model without identifying a specific retrieval limitation it is expected to address.

- Maintain reproducible benchmark tests for ranking changes.

## 25.5 Background-work rules

- Use supported Android scheduling mechanisms.

- Assume jobs may be deferred, interrupted, or retried.

- Make indexing safe to resume.

- Avoid unnecessary repeated parsing.

- Check the requested battery policy at runtime.

- Keep the UI informed about meaningful background activity.

- Do not promise exact scheduling when the platform cannot guarantee it.

## 25.6 Implementation workflow

For each substantial feature:

1. Explain the proposed design briefly.

2. Identify the files and interfaces involved.

3. Implement the smallest complete version.

4. Explain the important algorithms and data flow.

5. Add tests for expected behavior and relevant failure cases.

6. Run available checks.

7. Report what passed, what failed, and what remains unverified.

8. Update the documentation if architecture or behavior changes.

If requirements conflict, preserve privacy, data integrity, and user intent over convenience or speculative feature expansion.

# 26. Definition of a Successful Initial Release

The initial release will be considered successful when a user can:

1. Create and manage personal memories offline.

2. Record the location of physical belongings.

3. Search using indirect wording and supported synonyms.

4. Retrieve information from supported, user-selected documents.

5. Follow explicit relationships between memories.

6. Distinguish confirmed information from uncertain or outdated information.

7. Create and manage reminders linked to memories.

8. Use low-friction capture mechanisms that have been implemented and tested.

9. Inspect, correct, and delete stored information.

10. Operate the core application without an internet connection.

11. Understand what the application indexes and what its background jobs do.

12. Inspect the source code and supporting documentation to evaluate the privacy claims.

The project should not be considered complete merely because every screen exists. Retrieval quality, data integrity, privacy behavior, and real-device reliability must be demonstrated.

# 27. Final Product Statement

Project WhereWasIt? is an attempt to make personal information easier to remember without requiring people to become better at organizing it.

It combines structured memory capture, local document indexing, contextual retrieval, explicit memory relationships, reminders, and freshness verification in one Android application.

Its defining promise is not that it knows everything or that artificial intelligence can perfectly reconstruct human memory. Its promise is that recorded information should remain private, searchable, understandable, and useful over time.

The project will begin with a Java-based Android implementation and a conventional local retrieval engine. Advanced machine learning will remain a later, evidence-driven extension rather than a prerequisite for the product's existence.

**Core philosophy: Capture effortlessly. Recall contextually. Verify honestly. Keep everything local.**

**Working title:** Project WhereWasIt?
