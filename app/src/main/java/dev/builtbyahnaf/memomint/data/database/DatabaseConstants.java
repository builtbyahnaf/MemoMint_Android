package dev.builtbyahnaf.memomint.data.database;

/**
 * Centralizes all database table names, column names, SQL statements, and string constants
 * used by {@link MemoMintDatabase} and the repository implementations.
 *
 * <h2>Why centralize these constants?</h2>
 * <p>Scattering column-name strings across every repository method creates a maintenance
 * problem: a column rename requires hunting down every reference. Centralizing them here
 * means a rename is a single change in one file, and the compiler catches every caller
 * that references the changed constant.</p>
 *
 * <h2>Naming conventions</h2>
 * <ul>
 *   <li>Inner classes group constants by table (e.g., {@link Memories}, {@link ContextRecords}).</li>
 *   <li>Column constants match the exact column name string stored in SQLite.</li>
 *   <li>SQL strings in {@link Sql} are complete DDL statements used during schema creation
 *       and migrations.</li>
 * </ul>
 *
 * <p>This class is not instantiable.</p>
 */
public final class DatabaseConstants {

    private DatabaseConstants() {
        throw new UnsupportedOperationException("DatabaseConstants is a utility class");
    }

    // =========================================================================
    // Database metadata
    // =========================================================================

    /** Filename for the SQLite database on disk. */
    public static final String DATABASE_NAME = "memomint.db";

    /**
     * Current schema version. Increment this integer whenever the schema changes,
     * and add a corresponding migration block in {@link MemoMintDatabase#onUpgrade}.
     *
     * <p>Version history:</p>
     * <ul>
     *   <li>Version 1: Initial schema (Phase 1 — all core tables).</li>
     *   <li>Version 2: Full-text search virtual table (memories_fts) and sync triggers (Phase 2).</li>
     *   <li>Version 3: Document full-text search virtual table (files_fts) for Phase 4 file indexing.</li>
     *   <li>Version 4: Added repeat_interval column to reminders table for recurring notifications.</li>
     * </ul>
     */
    public static final int DATABASE_VERSION = 4;

    // =========================================================================
    // Table: memories
    // =========================================================================

    /** Constants for the {@code memories} table. */
    public static final class Memories {
        private Memories() {}

        public static final String TABLE = "memories";

        public static final String COL_ID               = "id";
        public static final String COL_TYPE             = "type";
        public static final String COL_ORIGINAL_TEXT    = "original_text";
        public static final String COL_TITLE            = "title";
        public static final String COL_CREATED_AT       = "created_at";
        public static final String COL_UPDATED_AT       = "updated_at";
        public static final String COL_SOURCE_TYPE      = "source_type";
        public static final String COL_SOURCE_ID        = "source_id";
        public static final String COL_STATUS           = "status";
        public static final String COL_LAST_CONFIRMED_AT = "last_confirmed_at";
    }

    // =========================================================================
    // Table: memories_fts (FTS5 full-text search index, Schema v2)
    // =========================================================================

    /** Constants for the {@code memories_fts} virtual table (Phase 2). */
    public static final class MemoriesFts {
        private MemoriesFts() {}

        public static final String TABLE = "memories_fts";

        public static final String COL_ID            = "id";
        public static final String COL_TITLE         = "title";
        public static final String COL_ORIGINAL_TEXT = "original_text";
    }

    // =========================================================================
    // Table: files_fts (FTS5 document text search index, Schema v3)
    // =========================================================================

    /** Constants for the {@code files_fts} virtual table (Phase 4). */
    public static final class FilesFts {
        private FilesFts() {}

        public static final String TABLE = "files_fts";

        public static final String COL_ID      = "id";
        public static final String COL_FILE_ID = "file_id";
        public static final String COL_TITLE   = "title";
        public static final String COL_CONTENT = "content";
    }

    // =========================================================================
    // Table: context_records
    // =========================================================================

    /** Constants for the {@code context_records} table. */
    public static final class ContextRecords {
        private ContextRecords() {}

        public static final String TABLE = "context_records";

        public static final String COL_ID                = "id";
        public static final String COL_MEMORY_ID         = "memory_id";
        public static final String COL_CONTEXT_TYPE      = "context_type";
        public static final String COL_CONTENT           = "content";
        public static final String COL_GENERATOR_VERSION = "generator_version";
        public static final String COL_CREATED_AT        = "created_at";
        public static final String COL_UPDATED_AT        = "updated_at";
        public static final String COL_GENERATION_STATUS = "generation_status";
    }

    // =========================================================================
    // Table: entities
    // =========================================================================

    /** Constants for the {@code entities} table. */
    public static final class Entities {
        private Entities() {}

        public static final String TABLE = "entities";

        public static final String COL_ID              = "id";
        public static final String COL_CANONICAL_LABEL = "canonical_label";
        public static final String COL_ENTITY_TYPE     = "entity_type";
        public static final String COL_ORIGINAL_LABEL  = "original_label";
    }

    // =========================================================================
    // Table: memory_entities
    // =========================================================================

    /** Constants for the {@code memory_entities} join table. */
    public static final class MemoryEntities {
        private MemoryEntities() {}

        public static final String TABLE = "memory_entities";

        public static final String COL_MEMORY_ID   = "memory_id";
        public static final String COL_ENTITY_ID   = "entity_id";
        public static final String COL_ROLE        = "role";
        public static final String COL_PROVENANCE  = "provenance";
        public static final String COL_CONFIDENCE  = "confidence";
    }

    // =========================================================================
    // Table: memory_relations
    // =========================================================================

    /** Constants for the {@code memory_relations} table. */
    public static final class MemoryRelations {
        private MemoryRelations() {}

        public static final String TABLE = "memory_relations";

        public static final String COL_ID               = "id";
        public static final String COL_SOURCE_TYPE      = "source_type";
        public static final String COL_SOURCE_ID        = "source_id";
        public static final String COL_RELATION_TYPE    = "relation_type";
        public static final String COL_TARGET_TYPE      = "target_type";
        public static final String COL_TARGET_ID        = "target_id";
        public static final String COL_PROVENANCE       = "provenance";
        public static final String COL_CONFIDENCE       = "confidence";
        public static final String COL_CREATED_AT       = "created_at";
        public static final String COL_LAST_CONFIRMED_AT = "last_confirmed_at";
    }

    // =========================================================================
    // Table: file_references
    // =========================================================================

    /** Constants for the {@code file_references} table. */
    public static final class FileReferences {
        private FileReferences() {}

        public static final String TABLE = "file_references";

        public static final String COL_ID                  = "id";
        public static final String COL_URI_OR_PATH         = "uri_or_path";
        public static final String COL_DISPLAY_NAME        = "display_name";
        public static final String COL_MIME_TYPE           = "mime_type";
        public static final String COL_SIZE_BYTES          = "size_bytes";
        public static final String COL_LAST_MODIFIED_AT    = "last_modified_at";
        public static final String COL_LAST_INDEXED_AT     = "last_indexed_at";
        public static final String COL_CONTENT_FINGERPRINT = "content_fingerprint";
        public static final String COL_ACCESS_STATE        = "access_state";
        public static final String COL_LAST_VERIFIED_AT    = "last_verified_at";
        public static final String COL_INDEXING_STATUS     = "indexing_status";
        public static final String COL_EXCLUDED            = "excluded";
    }

    // =========================================================================
    // Table: reminders
    // =========================================================================

    /** Constants for the {@code reminders} table. */
    public static final class Reminders {
        private Reminders() {}

        public static final String TABLE = "reminders";

        public static final String COL_ID               = "id";
        public static final String COL_MEMORY_ID        = "memory_id";
        public static final String COL_TITLE            = "title";
        public static final String COL_SCHEDULED_AT     = "scheduled_at";
        public static final String COL_TIME_ZONE_ID     = "time_zone_id";
        public static final String COL_STATUS           = "status";
        public static final String COL_CREATED_AT       = "created_at";
        public static final String COL_COMPLETED_AT     = "completed_at";
        public static final String COL_LAST_SCHEDULED_AT = "last_scheduled_at";
        public static final String COL_REPEAT_INTERVAL  = "repeat_interval";
    }

    // =========================================================================
    // Table: confirmation_policies
    // =========================================================================

    /** Constants for the {@code confirmation_policies} table. */
    public static final class ConfirmationPolicies {
        private ConfirmationPolicies() {}

        public static final String TABLE = "confirmation_policies";

        public static final String COL_MEMORY_ID       = "memory_id";
        public static final String COL_ENABLED         = "enabled";
        public static final String COL_INTERVAL_TYPE   = "interval_type";
        public static final String COL_INTERVAL_VALUE  = "interval_value";
        public static final String COL_LAST_PROMPTED_AT = "last_prompted_at";
        public static final String COL_NEXT_CHECK_AT   = "next_check_at";
    }

    // =========================================================================
    // Table: index_jobs
    // =========================================================================

    /** Constants for the {@code index_jobs} table. */
    public static final class IndexJobs {
        private IndexJobs() {}

        public static final String TABLE = "index_jobs";

        public static final String COL_ID         = "id";
        public static final String COL_SOURCE_ID  = "source_id";
        public static final String COL_STATUS     = "status";
        public static final String COL_PROGRESS   = "progress";
        public static final String COL_STARTED_AT = "started_at";
        public static final String COL_UPDATED_AT = "updated_at";
        public static final String COL_ERROR_CODE = "error_code";

        // Valid status values for the index_jobs.status column
        public static final class Status {
            private Status() {}
            public static final String PENDING   = "PENDING";
            public static final String RUNNING   = "RUNNING";
            public static final String DONE      = "DONE";
            public static final String FAILED    = "FAILED";
            public static final String CANCELLED = "CANCELLED";
        }
    }

    // =========================================================================
    // Valid string values for the memory_relations endpoints
    // =========================================================================

    /**
     * Valid values for the source_type and target_type columns in memory_relations.
     * These strings identify which table the corresponding id column refers to.
     */
    public static final class RelationParty {
        private RelationParty() {}
        public static final String MEMORY = "MEMORY";
        public static final String ENTITY = "ENTITY";
    }

    // =========================================================================
    // Valid interval_type strings for confirmation_policies
    // =========================================================================

    /** Valid values for the confirmation_policies.interval_type column. */
    public static final class IntervalType {
        private IntervalType() {}
        public static final String DAILY      = "DAILY";
        public static final String THREE_DAYS = "THREE_DAYS";
        public static final String WEEKLY     = "WEEKLY";
        public static final String DISABLED   = "DISABLED";
    }

    // =========================================================================
    // DDL — SQL creation statements (Version 1 schema)
    // =========================================================================

    /**
     * Contains the CREATE TABLE statements for database version 1.
     * All statements are idempotent with respect to schema version because they are
     * only called from {@code onCreate()} or from a {@code onUpgrade()} block that
     * has already verified the from-version.
     */
    public static final class Sql {
        private Sql() {}

        /**
         * Creates the {@code memories} table.
         *
         * <p>FOREIGN KEY enforcement is enabled per-connection in
         * {@link MemoMintDatabase#onConfigure}. Cascade deletes from this table
         * propagate to context_records, memory_entities, reminders, and
         * confirmation_policies.</p>
         */
        public static final String CREATE_MEMORIES =
                "CREATE TABLE IF NOT EXISTS " + Memories.TABLE + " ("
                        + Memories.COL_ID               + " TEXT PRIMARY KEY NOT NULL, "
                        + Memories.COL_TYPE             + " TEXT NOT NULL, "
                        + Memories.COL_ORIGINAL_TEXT    + " TEXT NOT NULL, "
                        + Memories.COL_TITLE            + " TEXT, "
                        + Memories.COL_CREATED_AT       + " INTEGER NOT NULL, "
                        + Memories.COL_UPDATED_AT       + " INTEGER NOT NULL, "
                        + Memories.COL_SOURCE_TYPE      + " TEXT NOT NULL, "
                        + Memories.COL_SOURCE_ID        + " TEXT, "
                        + Memories.COL_STATUS           + " TEXT NOT NULL DEFAULT 'ACTIVE', "
                        + Memories.COL_LAST_CONFIRMED_AT + " INTEGER"
                        + ")";

        /**
         * Index on memories.status to efficiently filter active vs. archived vs. deleted.
         */
        public static final String CREATE_IDX_MEMORIES_STATUS =
                "CREATE INDEX IF NOT EXISTS idx_memories_status"
                        + " ON " + Memories.TABLE + "(" + Memories.COL_STATUS + ")";

        /**
         * Index on memories.created_at for chronological listing.
         */
        public static final String CREATE_IDX_MEMORIES_CREATED_AT =
                "CREATE INDEX IF NOT EXISTS idx_memories_created_at"
                        + " ON " + Memories.TABLE + "(" + Memories.COL_CREATED_AT + ")";

        /**
         * Creates the {@code context_records} table.
         * Cascade-deleted when the parent memory is deleted.
         */
        public static final String CREATE_CONTEXT_RECORDS =
                "CREATE TABLE IF NOT EXISTS " + ContextRecords.TABLE + " ("
                        + ContextRecords.COL_ID                + " TEXT PRIMARY KEY NOT NULL, "
                        + ContextRecords.COL_MEMORY_ID         + " TEXT NOT NULL, "
                        + ContextRecords.COL_CONTEXT_TYPE      + " TEXT NOT NULL, "
                        + ContextRecords.COL_CONTENT           + " TEXT, "
                        + ContextRecords.COL_GENERATOR_VERSION + " INTEGER NOT NULL DEFAULT 0, "
                        + ContextRecords.COL_CREATED_AT        + " INTEGER NOT NULL, "
                        + ContextRecords.COL_UPDATED_AT        + " INTEGER NOT NULL, "
                        + ContextRecords.COL_GENERATION_STATUS + " TEXT NOT NULL DEFAULT 'PENDING', "
                        + "FOREIGN KEY (" + ContextRecords.COL_MEMORY_ID + ") "
                        + "  REFERENCES " + Memories.TABLE + "(" + Memories.COL_ID + ") "
                        + "  ON DELETE CASCADE"
                        + ")";

        /**
         * Index on context_records.memory_id for fast lookup of all context for a memory.
         */
        public static final String CREATE_IDX_CONTEXT_RECORDS_MEMORY_ID =
                "CREATE INDEX IF NOT EXISTS idx_context_records_memory_id"
                        + " ON " + ContextRecords.TABLE + "(" + ContextRecords.COL_MEMORY_ID + ")";

        /** Creates the {@code entities} table. */
        public static final String CREATE_ENTITIES =
                "CREATE TABLE IF NOT EXISTS " + Entities.TABLE + " ("
                        + Entities.COL_ID              + " TEXT PRIMARY KEY NOT NULL, "
                        + Entities.COL_CANONICAL_LABEL + " TEXT NOT NULL, "
                        + Entities.COL_ENTITY_TYPE     + " TEXT NOT NULL, "
                        + Entities.COL_ORIGINAL_LABEL  + " TEXT NOT NULL"
                        + ")";

        /**
         * Creates the {@code memory_entities} join table.
         * Composite PK on (memory_id, entity_id, role).
         * Cascade-deleted when the parent memory is deleted.
         */
        public static final String CREATE_MEMORY_ENTITIES =
                "CREATE TABLE IF NOT EXISTS " + MemoryEntities.TABLE + " ("
                        + MemoryEntities.COL_MEMORY_ID  + " TEXT NOT NULL, "
                        + MemoryEntities.COL_ENTITY_ID  + " TEXT NOT NULL, "
                        + MemoryEntities.COL_ROLE       + " TEXT NOT NULL, "
                        + MemoryEntities.COL_PROVENANCE + " TEXT NOT NULL, "
                        + MemoryEntities.COL_CONFIDENCE + " REAL, "
                        + "PRIMARY KEY (" + MemoryEntities.COL_MEMORY_ID + ", "
                        +                   MemoryEntities.COL_ENTITY_ID + ", "
                        +                   MemoryEntities.COL_ROLE + "), "
                        + "FOREIGN KEY (" + MemoryEntities.COL_MEMORY_ID + ") "
                        + "  REFERENCES " + Memories.TABLE + "(" + Memories.COL_ID + ") "
                        + "  ON DELETE CASCADE, "
                        + "FOREIGN KEY (" + MemoryEntities.COL_ENTITY_ID + ") "
                        + "  REFERENCES " + Entities.TABLE + "(" + Entities.COL_ID + ")"
                        + ")";

        /** Creates the {@code memory_relations} table. */
        public static final String CREATE_MEMORY_RELATIONS =
                "CREATE TABLE IF NOT EXISTS " + MemoryRelations.TABLE + " ("
                        + MemoryRelations.COL_ID               + " TEXT PRIMARY KEY NOT NULL, "
                        + MemoryRelations.COL_SOURCE_TYPE      + " TEXT NOT NULL, "
                        + MemoryRelations.COL_SOURCE_ID        + " TEXT NOT NULL, "
                        + MemoryRelations.COL_RELATION_TYPE    + " TEXT NOT NULL, "
                        + MemoryRelations.COL_TARGET_TYPE      + " TEXT NOT NULL, "
                        + MemoryRelations.COL_TARGET_ID        + " TEXT NOT NULL, "
                        + MemoryRelations.COL_PROVENANCE       + " TEXT NOT NULL, "
                        + MemoryRelations.COL_CONFIDENCE       + " REAL, "
                        + MemoryRelations.COL_CREATED_AT       + " INTEGER NOT NULL, "
                        + MemoryRelations.COL_LAST_CONFIRMED_AT + " INTEGER"
                        + ")";

        /**
         * Index on memory_relations source for forward traversal of the graph.
         */
        public static final String CREATE_IDX_MEMORY_RELATIONS_SOURCE =
                "CREATE INDEX IF NOT EXISTS idx_memory_relations_source"
                        + " ON " + MemoryRelations.TABLE + "("
                        + MemoryRelations.COL_SOURCE_TYPE + ", "
                        + MemoryRelations.COL_SOURCE_ID + ")";

        /**
         * Index on memory_relations target for reverse traversal of the graph.
         */
        public static final String CREATE_IDX_MEMORY_RELATIONS_TARGET =
                "CREATE INDEX IF NOT EXISTS idx_memory_relations_target"
                        + " ON " + MemoryRelations.TABLE + "("
                        + MemoryRelations.COL_TARGET_TYPE + ", "
                        + MemoryRelations.COL_TARGET_ID + ")";

        /** Creates the {@code file_references} table. */
        public static final String CREATE_FILE_REFERENCES =
                "CREATE TABLE IF NOT EXISTS " + FileReferences.TABLE + " ("
                        + FileReferences.COL_ID                  + " TEXT PRIMARY KEY NOT NULL, "
                        + FileReferences.COL_URI_OR_PATH         + " TEXT NOT NULL, "
                        + FileReferences.COL_DISPLAY_NAME        + " TEXT NOT NULL, "
                        + FileReferences.COL_MIME_TYPE           + " TEXT, "
                        + FileReferences.COL_SIZE_BYTES          + " INTEGER, "
                        + FileReferences.COL_LAST_MODIFIED_AT    + " INTEGER, "
                        + FileReferences.COL_LAST_INDEXED_AT     + " INTEGER, "
                        + FileReferences.COL_CONTENT_FINGERPRINT + " TEXT, "
                        + FileReferences.COL_ACCESS_STATE        + " TEXT NOT NULL DEFAULT 'NOT_YET_VERIFIED', "
                        + FileReferences.COL_LAST_VERIFIED_AT    + " INTEGER, "
                        + FileReferences.COL_INDEXING_STATUS     + " TEXT NOT NULL DEFAULT 'PENDING', "
                        + FileReferences.COL_EXCLUDED            + " INTEGER NOT NULL DEFAULT 0"
                        + ")";

        /**
         * Creates the {@code reminders} table.
         * memory_id is nullable (a reminder may stand alone without a linked memory).
         * Cascade-deleted when the linked memory is deleted.
         */
        public static final String CREATE_REMINDERS =
                "CREATE TABLE IF NOT EXISTS " + Reminders.TABLE + " ("
                        + Reminders.COL_ID               + " TEXT PRIMARY KEY NOT NULL, "
                        + Reminders.COL_MEMORY_ID        + " TEXT, "
                        + Reminders.COL_TITLE            + " TEXT NOT NULL, "
                        + Reminders.COL_SCHEDULED_AT     + " INTEGER NOT NULL, "
                        + Reminders.COL_TIME_ZONE_ID     + " TEXT, "
                        + Reminders.COL_STATUS           + " TEXT NOT NULL DEFAULT 'ACTIVE', "
                        + Reminders.COL_CREATED_AT       + " INTEGER NOT NULL, "
                        + Reminders.COL_COMPLETED_AT     + " INTEGER, "
                        + Reminders.COL_LAST_SCHEDULED_AT + " INTEGER, "
                        + Reminders.COL_REPEAT_INTERVAL  + " TEXT NOT NULL DEFAULT 'NONE', "
                        + "FOREIGN KEY (" + Reminders.COL_MEMORY_ID + ") "
                        + "  REFERENCES " + Memories.TABLE + "(" + Memories.COL_ID + ") "
                        + "  ON DELETE CASCADE"
                        + ")";

        /**
         * Migration DDL: Adds repeat_interval column to reminders (Schema v3 -> v4).
         */
        public static final String ALTER_REMINDERS_ADD_REPEAT_INTERVAL =
                "ALTER TABLE " + Reminders.TABLE
                        + " ADD COLUMN " + Reminders.COL_REPEAT_INTERVAL
                        + " TEXT NOT NULL DEFAULT 'NONE'";

        /**
         * Index on reminders.status + scheduled_at for efficient pending-reminder queries.
         */
        public static final String CREATE_IDX_REMINDERS_STATUS_SCHEDULED =
                "CREATE INDEX IF NOT EXISTS idx_reminders_status_scheduled"
                        + " ON " + Reminders.TABLE + "("
                        + Reminders.COL_STATUS + ", "
                        + Reminders.COL_SCHEDULED_AT + ")";

        /**
         * Creates the {@code confirmation_policies} table.
         * One row per memory; memory_id is both PK and FK.
         * Cascade-deleted when the parent memory is deleted.
         */
        public static final String CREATE_CONFIRMATION_POLICIES =
                "CREATE TABLE IF NOT EXISTS " + ConfirmationPolicies.TABLE + " ("
                        + ConfirmationPolicies.COL_MEMORY_ID        + " TEXT PRIMARY KEY NOT NULL, "
                        + ConfirmationPolicies.COL_ENABLED          + " INTEGER NOT NULL DEFAULT 0, "
                        + ConfirmationPolicies.COL_INTERVAL_TYPE    + " TEXT NOT NULL DEFAULT 'DISABLED', "
                        + ConfirmationPolicies.COL_INTERVAL_VALUE   + " INTEGER NOT NULL DEFAULT 0, "
                        + ConfirmationPolicies.COL_LAST_PROMPTED_AT + " INTEGER, "
                        + ConfirmationPolicies.COL_NEXT_CHECK_AT    + " INTEGER, "
                        + "FOREIGN KEY (" + ConfirmationPolicies.COL_MEMORY_ID + ") "
                        + "  REFERENCES " + Memories.TABLE + "(" + Memories.COL_ID + ") "
                        + "  ON DELETE CASCADE"
                        + ")";

        /**
         * Creates the {@code index_jobs} table.
         * source_id references file_references but is not a foreign key with cascade
         * because index job history may be retained even if the file reference is re-added.
         */
        public static final String CREATE_INDEX_JOBS =
                "CREATE TABLE IF NOT EXISTS " + IndexJobs.TABLE + " ("
                        + IndexJobs.COL_ID         + " TEXT PRIMARY KEY NOT NULL, "
                        + IndexJobs.COL_SOURCE_ID  + " TEXT NOT NULL, "
                        + IndexJobs.COL_STATUS     + " TEXT NOT NULL DEFAULT 'PENDING', "
                        + IndexJobs.COL_PROGRESS   + " INTEGER NOT NULL DEFAULT 0, "
                        + IndexJobs.COL_STARTED_AT + " INTEGER, "
                        + IndexJobs.COL_UPDATED_AT + " INTEGER NOT NULL, "
                        + IndexJobs.COL_ERROR_CODE + " TEXT"
                        + ")";

        /**
         * Index on index_jobs.source_id for looking up jobs by file reference.
         */
        public static final String CREATE_IDX_INDEX_JOBS_SOURCE =
                "CREATE INDEX IF NOT EXISTS idx_index_jobs_source"
                        + " ON " + IndexJobs.TABLE + "(" + IndexJobs.COL_SOURCE_ID + ")";

        // =====================================================================
        // Schema Version 2: Full-Text Search (memories_fts) & Sync Triggers
        // =====================================================================

        /**
         * Creates the {@code memories_fts} virtual table using FTS5 for fast full-text
         * indexing and BM25 ranking (Schema Version 2).
         * Uses unicode61 tokenizer with diacritics stripping.
         */
        public static final String CREATE_MEMORIES_FTS5 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + MemoriesFts.TABLE + " USING fts5("
                        + MemoriesFts.COL_ID + " UNINDEXED, "
                        + MemoriesFts.COL_TITLE + ", "
                        + MemoriesFts.COL_ORIGINAL_TEXT + ", "
                        + "tokenize = 'unicode61 remove_diacritics 2'"
                        + ")";

        /**
         * Creates the {@code memories_fts} virtual table using FTS4 as a safe, guaranteed
         * fallback when FTS5 is not compiled into the Android device's SQLite engine.
         * FTS4 is built into 100% of Android devices (since API 11).
         */
        public static final String CREATE_MEMORIES_FTS4 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + MemoriesFts.TABLE + " USING fts4("
                        + MemoriesFts.COL_ID + ", "
                        + MemoriesFts.COL_TITLE + ", "
                        + MemoriesFts.COL_ORIGINAL_TEXT + ", "
                        + "tokenize=unicode61"
                        + ")";

        /**
         * Creates the {@code memories_fts} virtual table using FTS3 as an ultra-compatible fallback
         * supported on 100% of SQLite engines including JVM test environments.
         */
        public static final String CREATE_MEMORIES_FTS3 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + MemoriesFts.TABLE + " USING fts3("
                        + MemoriesFts.COL_ID + ", "
                        + MemoriesFts.COL_TITLE + ", "
                        + MemoriesFts.COL_ORIGINAL_TEXT
                        + ")";

        /**
         * Alias pointing to the primary FTS definition.
         */
        public static final String CREATE_MEMORIES_FTS = CREATE_MEMORIES_FTS5;

        /**
         * Trigger to automatically insert newly created memories into {@code memories_fts}.
         */
        public static final String CREATE_TRIGGER_MEMORIES_AI =
                "CREATE TRIGGER IF NOT EXISTS trigger_memories_ai AFTER INSERT ON " + Memories.TABLE
                        + " BEGIN "
                        + "INSERT INTO " + MemoriesFts.TABLE + "("
                        + MemoriesFts.COL_ID + ", " + MemoriesFts.COL_TITLE + ", " + MemoriesFts.COL_ORIGINAL_TEXT
                        + ") VALUES (new." + Memories.COL_ID + ", new." + Memories.COL_TITLE + ", new." + Memories.COL_ORIGINAL_TEXT + ");"
                        + " END";

        /**
         * Trigger to automatically delete removed memories from {@code memories_fts}.
         */
        public static final String CREATE_TRIGGER_MEMORIES_AD =
                "CREATE TRIGGER IF NOT EXISTS trigger_memories_ad AFTER DELETE ON " + Memories.TABLE
                        + " BEGIN "
                        + "DELETE FROM " + MemoriesFts.TABLE + " WHERE " + MemoriesFts.COL_ID + " = old." + Memories.COL_ID + ";"
                        + " END";

        /**
         * Trigger to automatically update {@code memories_fts} when a memory's title or text changes.
         */
        public static final String CREATE_TRIGGER_MEMORIES_AU =
                "CREATE TRIGGER IF NOT EXISTS trigger_memories_au AFTER UPDATE ON " + Memories.TABLE
                        + " BEGIN "
                        + "DELETE FROM " + MemoriesFts.TABLE + " WHERE " + MemoriesFts.COL_ID + " = old." + Memories.COL_ID + ";"
                        + "INSERT INTO " + MemoriesFts.TABLE + "("
                        + MemoriesFts.COL_ID + ", " + MemoriesFts.COL_TITLE + ", " + MemoriesFts.COL_ORIGINAL_TEXT
                        + ") VALUES (new." + Memories.COL_ID + ", new." + Memories.COL_TITLE + ", new." + Memories.COL_ORIGINAL_TEXT + ");"
                        + " END";

        /**
         * Populates {@code memories_fts} with all existing memories during v1 -> v2 migration.
         */
        public static final String POPULATE_MEMORIES_FTS =
                "INSERT INTO " + MemoriesFts.TABLE + "("
                        + MemoriesFts.COL_ID + ", " + MemoriesFts.COL_TITLE + ", " + MemoriesFts.COL_ORIGINAL_TEXT + ")"
                        + " SELECT " + Memories.COL_ID + ", " + Memories.COL_TITLE + ", " + Memories.COL_ORIGINAL_TEXT
                        + " FROM " + Memories.TABLE;

        // =====================================================================
        // Schema Version 3: Document Full-Text Search (files_fts)
        // =====================================================================

        /**
         * Creates the {@code files_fts} virtual table using FTS5 for fast full-text
         * indexing and BM25 ranking across document contents (Schema Version 3).
         */
        public static final String CREATE_FILES_FTS5 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + FilesFts.TABLE + " USING fts5("
                        + FilesFts.COL_ID + " UNINDEXED, "
                        + FilesFts.COL_FILE_ID + " UNINDEXED, "
                        + FilesFts.COL_TITLE + ", "
                        + FilesFts.COL_CONTENT + ", "
                        + "tokenize = 'unicode61 remove_diacritics 2'"
                        + ")";

        /**
         * Creates the {@code files_fts} virtual table using FTS4 as fallback.
         */
        public static final String CREATE_FILES_FTS4 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + FilesFts.TABLE + " USING fts4("
                        + FilesFts.COL_ID + ", "
                        + FilesFts.COL_FILE_ID + ", "
                        + FilesFts.COL_TITLE + ", "
                        + FilesFts.COL_CONTENT + ", "
                        + "tokenize=unicode61"
                        + ")";

        /**
         * Creates the {@code files_fts} virtual table using FTS3 as ultra-compatible fallback.
         */
        public static final String CREATE_FILES_FTS3 =
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + FilesFts.TABLE + " USING fts3("
                        + FilesFts.COL_ID + ", "
                        + FilesFts.COL_FILE_ID + ", "
                        + FilesFts.COL_TITLE + ", "
                        + FilesFts.COL_CONTENT
                        + ")";

        /**
         * Alias pointing to preferred FTS definition.
         */
        public static final String CREATE_FILES_FTS = CREATE_FILES_FTS5;
    }
}
