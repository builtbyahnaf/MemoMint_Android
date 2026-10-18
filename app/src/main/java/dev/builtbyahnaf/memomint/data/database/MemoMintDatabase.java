package dev.builtbyahnaf.memomint.data.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.Locale;

/**
 * SQLite database helper for MemoMint.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Creates all tables on first install ({@link #onCreate}).</li>
 *   <li>Applies explicit, version-ordered migrations on upgrade ({@link #onUpgrade}).</li>
 *   <li>Enables foreign-key enforcement per-connection ({@link #onConfigure}).</li>
 *   <li>Enforces WAL journal mode for better concurrent read performance ({@link #onOpen}).</li>
 * </ul>
 *
 * <h2>Schema versioning rules</h2>
 * <ol>
 *   <li>Increment {@link DatabaseConstants#DATABASE_VERSION} in DatabaseConstants.</li>
 *   <li>Add a {@code case} block in {@link #onUpgrade} that applies only the delta SQL
 *       needed to move from the previous version to the new one.</li>
 *   <li>Do NOT drop tables or columns without a documented migration path.</li>
 *   <li>Do NOT recreate tables in {@code onCreate} outside the {@link DatabaseConstants.Sql}
 *       constants — keep the DDL there to avoid drift between create and upgrade paths.</li>
 * </ol>
 *
 * <h2>Foreign key enforcement</h2>
 * <p>SQLite's foreign key support is OFF by default per connection. It is enabled here in
 * {@code onConfigure}, which is called before any other database operation. This ensures
 * that all cascade-delete rules defined in the schema are actually enforced.</p>
 *
 * <h2>Thread safety</h2>
 * <p>This helper is intended to be used as a singleton (one instance per process) obtained
 * via {@link #getInstance}. SQLiteOpenHelper is thread-safe for its own internal locking;
 * callers obtain a {@link SQLiteDatabase} via {@link #getWritableDatabase()} or
 * {@link #getReadableDatabase()} and should not retain database references beyond the
 * scope of a single operation or transaction.</p>
 */
public class MemoMintDatabase extends SQLiteOpenHelper {

    private static final String TAG = "MemoMintDatabase";

    // -------------------------------------------------------------------------
    // Singleton management
    // -------------------------------------------------------------------------

    private static volatile MemoMintDatabase sInstance;

    /**
     * Returns the singleton database helper, creating it if necessary.
     *
     * <p>Uses double-checked locking with a volatile field to ensure that only one
     * instance is created, even under concurrent access.</p>
     *
     * @param context application context; must not be null
     * @return the singleton {@link MemoMintDatabase} instance
     */
    public static MemoMintDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (MemoMintDatabase.class) {
                if (sInstance == null) {
                    sInstance = new MemoMintDatabase(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    /**
     * Clears the singleton instance.
     *
     * <p><b>For testing only.</b> This allows a fresh in-memory database to be created
     * between tests without carrying state across test cases. Must not be called in
     * production code.</p>
     */
    static void resetInstanceForTesting() {
        synchronized (MemoMintDatabase.class) {
            if (sInstance != null) {
                sInstance.close();
                sInstance = null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Private constructor. Use {@link #getInstance} in production,
     * or create directly with a null name for in-memory testing.
     *
     * @param context application context (or test context)
     */
    private MemoMintDatabase(Context context) {
        super(
                context,
                DatabaseConstants.DATABASE_NAME,
                /* factory= */ null,
                DatabaseConstants.DATABASE_VERSION
        );
    }

    /**
     * Package-private constructor for tests that need an in-memory database.
     * Pass {@code null} as the database name to create an in-memory database.
     *
     * @param context application or test context
     * @param name    database file name, or {@code null} for an in-memory database
     */
    MemoMintDatabase(Context context, String name) {
        super(context, name, /* factory= */ null, DatabaseConstants.DATABASE_VERSION);
    }

    // -------------------------------------------------------------------------
    // SQLiteOpenHelper callbacks
    // -------------------------------------------------------------------------

    /**
     * Called when foreign key support needs to be enabled.
     *
     * <p>SQLite's foreign key enforcement is off by default. Enabling it here in
     * {@code onConfigure} — which runs before any DDL or DML — ensures that all
     * {@code ON DELETE CASCADE} rules in the schema are enforced from the very first
     * operation on this connection.</p>
     *
     * @param db the database connection being configured
     */
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    /**
     * Called when the database file is first opened (after onCreate or after onUpgrade).
     *
     * <p>WAL (Write-Ahead Logging) mode is enabled here because it allows concurrent
     * readers while a write is in progress, which is better than the default rollback
     * journal for an application that performs background indexing while the UI reads.</p>
     *
     * @param db the opened database connection
     */
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable WAL mode for better read/write concurrency.
            // This is a no-op if WAL is already enabled or if the DB is read-only.
            db.enableWriteAheadLogging();

            // Automatically detect and repair any corrupted or unsupported FTS schema
            // (e.g. if the DB was previously created with FTS5 on an engine lacking FTS5).
            repairFtsSchemaIfUnsupported(db);
        }
    }

    /**
     * Creates the database schema from scratch.
     *
     * <p>Called once, the first time the database is opened on a new installation.
     * All statements are wrapped in a single transaction so that a failure leaves no
     * partial schema.</p>
     *
     * @param db the newly created database
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            createVersion1Schema(db);
            createVersion2Schema(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Applies schema migrations when the database version has increased.
     *
     * <p>Migrations are applied sequentially: the {@code switch} falls through from
     * {@code oldVersion} to {@code newVersion - 1}, applying only the changes needed
     * at each step. Each case is wrapped in its own transaction block so that a failure
     * in one migration does not corrupt work done by earlier migrations.</p>
     *
     * <p><b>When adding a new migration:</b></p>
     * <ol>
     *   <li>Increment {@link DatabaseConstants#DATABASE_VERSION}.</li>
     *   <li>Add {@code case N:} (where N is the old version) with the delta SQL.</li>
     *   <li>Do not add a {@code break} — let it fall through to the next case.</li>
     *   <li>End the chain with a {@code break} before the closing brace.</li>
     * </ol>
     *
     * @param db         the database to migrate
     * @param oldVersion the schema version currently on disk
     * @param newVersion the target schema version
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Each case migrates from 'case N' to version N+1.
        // Fall-through is intentional to chain multiple migrations.
        switch (oldVersion) {
            case 1:
                applyMigrationV1toV2(db);
                // fall through
            default:
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Executes all DDL statements needed to create the Version 1 schema.
     *
     * <p>This method is called from {@link #onCreate} and is the single source of truth
     * for the initial schema. It must be kept in sync with {@link DatabaseConstants.Sql}.</p>
     *
     * <p>Tables are created in dependency order: tables with no foreign keys first,
     * then tables that reference them.</p>
     *
     * @param db the database in which to create the schema
     */
    private void createVersion1Schema(SQLiteDatabase db) {
        // ---- Core memory table (no FK dependencies) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_MEMORIES);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_MEMORIES_STATUS);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_MEMORIES_CREATED_AT);

        // ---- Derived context (FK → memories) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_CONTEXT_RECORDS);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_CONTEXT_RECORDS_MEMORY_ID);

        // ---- Entity vocabulary (no FK dependencies) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_ENTITIES);

        // ---- Memory ↔ Entity join (FK → memories, entities) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_MEMORY_ENTITIES);

        // ---- Knowledge graph edges (no FK enforcement on polymorphic columns) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_MEMORY_RELATIONS);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_MEMORY_RELATIONS_SOURCE);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_MEMORY_RELATIONS_TARGET);

        // ---- File references (no FK dependencies; used by index_jobs) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_FILE_REFERENCES);

        // ---- Reminders (FK → memories) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_REMINDERS);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_REMINDERS_STATUS_SCHEDULED);

        // ---- Confirmation policies (FK → memories) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_CONFIRMATION_POLICIES);

        // ---- Index jobs (references file_references by convention, no FK cascade) ----
        db.execSQL(DatabaseConstants.Sql.CREATE_INDEX_JOBS);
        db.execSQL(DatabaseConstants.Sql.CREATE_IDX_INDEX_JOBS_SOURCE);
    }

    /**
     * Executes all DDL statements needed to create the Version 2 schema extensions:
     * the FTS full-text search table (FTS5 -> FTS4 -> FTS3 fallback cascade) and automatic
     * synchronization triggers.
     *
     * <p>Algorithm:
     * 1. Inspects SQLite engine capabilities via {@link #isModuleSupported}.
     * 2. Tries FTS5 first (best BM25 ranking and unicode61 tokenizer).
     * 3. Falls back to FTS4 (built into Android since API 11).
     * 4. Falls back to FTS3 (supported on 100% of SQLite engines including JVM/Robolectric).
     * 5. Creates synchronization triggers IF AND ONLY IF a virtual table was successfully created.
     *    Never creates triggers referencing a non-existent table, which would cause all
     *    memories DML operations to fail with SQLITE_ERROR.
     * </p>
     *
     * @param db the database in which to create the schema
     */
    private void createVersion2Schema(SQLiteDatabase db) {
        boolean ftsCreated = false;

        // 1. Preferred engine: FTS5 (BM25 ranking, unicode61 tokenizer with diacritics removal)
        if (isModuleSupported(db, "fts5")) {
            try {
                db.execSQL(DatabaseConstants.Sql.CREATE_MEMORIES_FTS5);
                ftsCreated = true;
                Log.i(TAG, "Initialized memories_fts using SQLite FTS5");
            } catch (Exception e) {
                Log.w(TAG, "Failed creating FTS5 table: " + e.getMessage());
            }
        }

        // 2. Secondary fallback: FTS4 (built into Android since API 11)
        if (!ftsCreated && isModuleSupported(db, "fts4")) {
            try {
                db.execSQL(DatabaseConstants.Sql.CREATE_MEMORIES_FTS4);
                ftsCreated = true;
                Log.i(TAG, "Initialized memories_fts using SQLite FTS4 fallback");
            } catch (Exception e) {
                Log.w(TAG, "Failed creating FTS4 table: " + e.getMessage());
            }
        }

        // 3. Ultra-compatible fallback: FTS3 (guaranteed on all SQLite builds including JVM/Robolectric)
        if (!ftsCreated && isModuleSupported(db, "fts3")) {
            try {
                db.execSQL(DatabaseConstants.Sql.CREATE_MEMORIES_FTS3);
                ftsCreated = true;
                Log.i(TAG, "Initialized memories_fts using SQLite FTS3 fallback");
            } catch (Exception e) {
                Log.w(TAG, "Failed creating FTS3 table: " + e.getMessage());
            }
        }

        // 4. Synchronization triggers: ONLY created when a virtual table exists!
        if (ftsCreated) {
            db.execSQL(DatabaseConstants.Sql.CREATE_TRIGGER_MEMORIES_AI);
            db.execSQL(DatabaseConstants.Sql.CREATE_TRIGGER_MEMORIES_AD);
            db.execSQL(DatabaseConstants.Sql.CREATE_TRIGGER_MEMORIES_AU);
        } else {
            Log.w(TAG, "No SQLite FTS module available on this device; running in pure relational mode.");
        }
    }

    /**
     * Checks whether an SQLite compilation option is enabled in this SQLite engine.
     * Uses the standard scalar function sqlite_compileoption_used('OPTION_NAME').
     *
     * @param db         the database connection
     * @param optionName the option name, e.g. "ENABLE_FTS5"
     * @return 1 if enabled, 0 if explicitly disabled, -1 if compile options cannot be determined
     */
    private static int checkCompileOption(SQLiteDatabase db, String optionName) {
        try (Cursor cursor = db.rawQuery("SELECT sqlite_compileoption_used(?)", new String[]{optionName})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) == 1 ? 1 : 0;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Checks whether a virtual table module (e.g. "fts5", "fts4", "fts3") is available
     * on this SQLite database connection.
     *
     * <p>Algorithm:
     * 1. Checks standard scalar function {@code sqlite_compileoption_used('ENABLE_FTSx')}.
     *    If it returns 1, module is supported. If it returns 0, module is definitively NOT supported.
     * 2. If compile options cannot be queried (-1), checks {@code PRAGMA module_list} (SQLite 3.16.0+).
     * 3. As a last resort, probes via a temporary virtual table with a unique probe name in the
     *    {@code temp} schema to avoid corrupting main schema.
     * </p>
     *
     * @param db         the database connection
     * @param moduleName the name of the module ("fts5", "fts4", "fts3")
     * @return true if the module can be used to construct virtual tables
     */
    private static boolean isModuleSupported(SQLiteDatabase db, String moduleName) {
        if (moduleName == null) return false;
        String lowerName = moduleName.toLowerCase(Locale.US);

        // 1. Check sqlite_compileoption_used
        int compileStatus = checkCompileOption(db, "ENABLE_" + lowerName.toUpperCase(Locale.US));
        if (compileStatus == 1) {
            return true;
        } else if (compileStatus == 0) {
            return false;
        }

        // 2. Check PRAGMA module_list
        try (Cursor cursor = db.rawQuery("PRAGMA module_list", null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (lowerName.equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Safe probe with temporary table
        String probeTable = "_probe_" + lowerName;
        try {
            db.execSQL("CREATE VIRTUAL TABLE temp." + probeTable + " USING " + lowerName + "(x)");
            try {
                db.execSQL("DROP TABLE IF EXISTS temp." + probeTable);
            } catch (Exception ignored) {
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if the database on disk has a corrupted or unsupported FTS virtual table
     * (for example, if created with FTS5 on an Android device or emulator lacking FTS5).
     * If so, cleanly repairs the schema by dropping the broken virtual table/triggers
     * and recreating with a supported FTS module or relational fallback.
     *
     * @param db the database to inspect and repair
     */
    private void repairFtsSchemaIfUnsupported(SQLiteDatabase db) {
        String existingTableSql = null;
        try (Cursor cursor = db.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{DatabaseConstants.MemoriesFts.TABLE}
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                existingTableSql = cursor.getString(0);
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to inspect sqlite_master for FTS table: " + e.getMessage());
            return;
        }

        if (existingTableSql == null) {
            // Table doesn't exist yet; if version >= 2, create it
            if (db.getVersion() >= 2) {
                createVersion2Schema(db);
            }
            return;
        }

        boolean isFts5 = existingTableSql.toLowerCase(Locale.US).contains("using fts5");
        boolean isFts4 = existingTableSql.toLowerCase(Locale.US).contains("using fts4");
        boolean needsRepair = false;

        if (isFts5 && !isModuleSupported(db, "fts5")) {
            Log.w(TAG, "Existing memories_fts uses FTS5, but FTS5 is not supported on this engine. Repairing...");
            needsRepair = true;
        } else if (isFts4 && !isModuleSupported(db, "fts4")) {
            Log.w(TAG, "Existing memories_fts uses FTS4, but FTS4 is not supported on this engine. Repairing...");
            needsRepair = true;
        }

        if (needsRepair) {
            try { db.execSQL("DROP TRIGGER IF EXISTS trigger_memories_ai"); } catch (Exception ignored) {}
            try { db.execSQL("DROP TRIGGER IF EXISTS trigger_memories_ad"); } catch (Exception ignored) {}
            try { db.execSQL("DROP TRIGGER IF EXISTS trigger_memories_au"); } catch (Exception ignored) {}

            try {
                db.execSQL("DROP TABLE IF EXISTS " + DatabaseConstants.MemoriesFts.TABLE);
            } catch (Exception e) {
                try {
                    db.execSQL("PRAGMA writable_schema = ON");
                    db.execSQL("DELETE FROM sqlite_master WHERE tbl_name = '" + DatabaseConstants.MemoriesFts.TABLE + "'");
                    db.execSQL("PRAGMA writable_schema = OFF");
                } catch (Exception ignored) {}
            }

            createVersion2Schema(db);
            try {
                db.execSQL(DatabaseConstants.Sql.POPULATE_MEMORIES_FTS);
            } catch (Exception e) {
                Log.w(TAG, "Could not repopulate FTS index after repair: " + e.getMessage());
            }
        }
    }

    /**
     * Applies the migration from Version 1 to Version 2.
     *
     * <p>Creates the FTS virtual table and synchronization triggers, then populates
     * the index with all existing rows from {@code memories}.</p>
     *
     * @param db the database being upgraded
     */
    private void applyMigrationV1toV2(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            createVersion2Schema(db);
            try {
                db.execSQL(DatabaseConstants.Sql.POPULATE_MEMORIES_FTS);
            } catch (Exception e) {
                Log.w(TAG, "Population of FTS during migration skipped or unsupported: " + e.getMessage());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
