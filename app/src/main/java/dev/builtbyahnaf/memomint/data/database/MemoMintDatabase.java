package dev.builtbyahnaf.memomint.data.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

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
        // Currently, there is only version 1, so no upgrade paths exist yet.
        switch (oldVersion) {
            // Version 1 is the initial schema; no upgrade from a prior version exists yet.
            // Future migrations will be added here as:
            //   case 1:
            //       applyMigrationV1toV2(db);
            //       // fall through
            //   case 2:
            //       applyMigrationV2toV3(db);
            //       // fall through
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
}
