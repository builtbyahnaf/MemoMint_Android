package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

/**
 * SQLite-backed implementation of {@link MemoryRepository}.
 *
 * <h2>Implementation approach</h2>
 * <p>Uses raw SQLite via {@link MemoMintDatabase} (a {@link android.database.sqlite.SQLiteOpenHelper}).
 * No ORM or generated code is used, keeping the implementation transparent and minimizing
 * third-party dependencies. All SQL strings use constants from {@link DatabaseConstants} to
 * prevent drift between the schema definition and the query strings.</p>
 *
 * <h2>Original-text protection</h2>
 * <p>The {@link #update} method writes {@code original_text} as provided by the caller.
 * The repository trusts the application layer to only pass user-edited text here, not
 * generated context. The column itself is never generated or overwritten by any
 * background task through this method.</p>
 *
 * <h2>Transactions</h2>
 * <p>Each write operation that needs to be atomic uses an explicit SQLite transaction.
 * Reads do not use transactions (SQLite's per-statement atomicity is sufficient for
 * single-query reads).</p>
 *
 * <h2>Cascade deletion</h2>
 * <p>Foreign key enforcement is enabled by {@link MemoMintDatabase#onConfigure}, so
 * {@link #hardDelete} triggers SQLite's {@code ON DELETE CASCADE} rules automatically —
 * removing context records, memory entities, reminders, and confirmation policies.</p>
 *
 * <h2>Cursor handling</h2>
 * <p>Every Cursor opened by this class is closed in a finally block to prevent leaks,
 * even if an exception occurs during row mapping.</p>
 */
public class MemoryRepositoryImpl implements MemoryRepository {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final MemoMintDatabase dbHelper;

    /**
     * Constructs a new repository backed by the given database helper.
     *
     * @param dbHelper the SQLiteOpenHelper to use; must not be null
     */
    public MemoryRepositoryImpl(MemoMintDatabase dbHelper) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("dbHelper must not be null");
        }
        this.dbHelper = dbHelper;
    }

    // =========================================================================
    // Create
    // =========================================================================

    /**
     * Inserts a new Memory row into the {@code memories} table.
     *
     * <p><b>Algorithm:</b></p>
     * <ol>
     *   <li>Validate inputs (non-null memory and id).</li>
     *   <li>Convert the Memory domain object to a {@link ContentValues} map.</li>
     *   <li>Insert within an explicit transaction.</li>
     *   <li>Return the stable id if the insert succeeds; throw on failure.</li>
     * </ol>
     *
     * @param memory the memory to insert
     * @return the stable id of the inserted memory
     */
    @Override
    public String save(Memory memory) {
        if (memory == null) throw new IllegalArgumentException("memory must not be null");
        if (memory.getId() == null) throw new IllegalArgumentException("memory id must not be null");

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = memoryToContentValues(memory);
            long rowId = db.insertOrThrow(
                    DatabaseConstants.Memories.TABLE,
                    /* nullColumnHack= */ null,
                    values
            );
            if (rowId == -1) {
                throw new RuntimeException("Insert failed for memory id: " + memory.getId());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return memory.getId();
    }

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Looks up a single Memory by its UUID primary key.
     *
     * <p><b>Algorithm:</b></p>
     * <ol>
     *   <li>Query {@code memories} WHERE {@code id = ?}.</li>
     *   <li>Move the cursor to the first (and only expected) row.</li>
     *   <li>Map the cursor row to a {@link Memory} domain object.</li>
     *   <li>Return null if the cursor is empty.</li>
     * </ol>
     *
     * @param id the UUID to look up
     * @return the found Memory, or null
     */
    @Override
    public Memory findById(String id) {
        if (id == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseConstants.Memories.TABLE,
                    /* columns= */ null, // SELECT *
                    DatabaseConstants.Memories.COL_ID + " = ?",
                    new String[]{id},
                    /* groupBy= */ null,
                    /* having= */ null,
                    /* orderBy= */ null,
                    /* limit= */ "1"
            );
            if (cursor.moveToFirst()) {
                return cursorToMemory(cursor);
            }
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /**
     * Returns all memories with the given status, most recently created first.
     *
     * @param status the status filter; must not be null
     * @return list of matching memories, never null
     */
    @Override
    public List<Memory> findByStatus(MemoryStatus status) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        return queryMemories(
                DatabaseConstants.Memories.COL_STATUS + " = ?",
                new String[]{status.name()},
                DatabaseConstants.Memories.COL_CREATED_AT + " DESC"
        );
    }

    /**
     * Returns all memories of the given type and status, most recently created first.
     *
     * @param type   the type filter; must not be null
     * @param status the status filter; must not be null
     * @return list of matching memories, never null
     */
    @Override
    public List<Memory> findByTypeAndStatus(MemoryType type, MemoryStatus status) {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        return queryMemories(
                DatabaseConstants.Memories.COL_TYPE + " = ? AND "
                        + DatabaseConstants.Memories.COL_STATUS + " = ?",
                new String[]{type.name(), status.name()},
                DatabaseConstants.Memories.COL_CREATED_AT + " DESC"
        );
    }

    /**
     * Returns all memories in any status, most recently created first.
     *
     * @return all memories, never null
     */
    @Override
    public List<Memory> findAll() {
        return queryMemories(/* selection= */ null, /* selectionArgs= */ null,
                DatabaseConstants.Memories.COL_CREATED_AT + " DESC");
    }

    /**
     * Counts memories with the given status.
     *
     * @param status the status to count; must not be null
     * @return count of matching rows
     */
    @Override
    public int countByStatus(MemoryStatus status) {
        if (status == null) throw new IllegalArgumentException("status must not be null");

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + DatabaseConstants.Memories.TABLE
                            + " WHERE " + DatabaseConstants.Memories.COL_STATUS + " = ?",
                    new String[]{status.name()}
            );
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Updates all columns of an existing memory row.
     *
     * <p>This replaces all writable fields, including {@code original_text}. Callers must
     * only pass user-edited text in the original_text field — not generated content.</p>
     *
     * @param memory the memory with updated values
     * @return true if a row was updated
     */
    @Override
    public boolean update(Memory memory) {
        if (memory == null) throw new IllegalArgumentException("memory must not be null");
        if (memory.getId() == null) throw new IllegalArgumentException("memory id must not be null");

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        int rowsAffected;
        try {
            ContentValues values = memoryToContentValues(memory);
            rowsAffected = db.update(
                    DatabaseConstants.Memories.TABLE,
                    values,
                    DatabaseConstants.Memories.COL_ID + " = ?",
                    new String[]{memory.getId()}
            );
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return rowsAffected > 0;
    }

    /**
     * Updates only the status column, leaving all other columns untouched.
     *
     * @param id     the memory UUID
     * @param status the new status
     * @return true if a row was updated
     */
    @Override
    public boolean updateStatus(String id, MemoryStatus status) {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");

        ContentValues values = new ContentValues(1);
        values.put(DatabaseConstants.Memories.COL_STATUS, status.name());

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsAffected = db.update(
                DatabaseConstants.Memories.TABLE,
                values,
                DatabaseConstants.Memories.COL_ID + " = ?",
                new String[]{id}
        );
        return rowsAffected > 0;
    }

    /**
     * Sets {@code last_confirmed_at} and {@code updated_at} for a user confirmation.
     *
     * @param id            the memory UUID
     * @param confirmedAtMs confirmation timestamp in epoch ms
     * @return true if a row was updated
     */
    @Override
    public boolean recordConfirmation(String id, long confirmedAtMs) {
        if (id == null) throw new IllegalArgumentException("id must not be null");

        ContentValues values = new ContentValues(2);
        values.put(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT, confirmedAtMs);
        values.put(DatabaseConstants.Memories.COL_UPDATED_AT, confirmedAtMs);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rowsAffected = db.update(
                DatabaseConstants.Memories.TABLE,
                values,
                DatabaseConstants.Memories.COL_ID + " = ?",
                new String[]{id}
        );
        return rowsAffected > 0;
    }

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Marks the memory as DELETED without removing the row.
     *
     * @param id the memory UUID
     * @return true if a row was updated
     */
    @Override
    public boolean softDelete(String id) {
        return updateStatus(id, MemoryStatus.DELETED);
    }

    /**
     * Permanently removes the memory row and all cascade-dependent records.
     *
     * <p>Cascade deletion is handled by SQLite's {@code ON DELETE CASCADE} rules, which
     * are active because foreign key enforcement was enabled in {@link MemoMintDatabase#onConfigure}.
     * Records removed by cascade: context_records, memory_entities, reminders, confirmation_policies.</p>
     *
     * <p>Shared entity records are NOT deleted by this operation, because an entity may be
     * referenced by many memories.</p>
     *
     * @param id the memory UUID
     * @return true if a row was deleted
     */
    @Override
    public boolean hardDelete(String id) {
        if (id == null) throw new IllegalArgumentException("id must not be null");

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        int rowsAffected;
        try {
            rowsAffected = db.delete(
                    DatabaseConstants.Memories.TABLE,
                    DatabaseConstants.Memories.COL_ID + " = ?",
                    new String[]{id}
            );
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return rowsAffected > 0;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Executes a parameterized SELECT on the memories table and returns mapped results.
     *
     * <p>Centralizes the boilerplate of opening, iterating, and closing a Cursor.
     * All read methods delegate to this helper.</p>
     *
     * @param selection     WHERE clause (without the WHERE keyword), or null for all rows
     * @param selectionArgs values to bind to '?' placeholders in selection
     * @param orderBy       ORDER BY clause (without the ORDER BY keyword)
     * @return a list of mapped Memory objects; never null
     */
    private List<Memory> queryMemories(String selection, String[] selectionArgs, String orderBy) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Memory> results = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseConstants.Memories.TABLE,
                    /* columns= */ null, // SELECT *
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    orderBy
            );
            while (cursor.moveToNext()) {
                results.add(cursorToMemory(cursor));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return results;
    }

    /**
     * Maps the current cursor row to a {@link Memory} domain object.
     *
     * <p>Column indices are looked up by name on each call using
     * {@link Cursor#getColumnIndexOrThrow}, which throws a clear
     * {@link IllegalArgumentException} if a column is missing (indicating a schema mismatch).
     * This is preferable to silent wrong-index reads.</p>
     *
     * <p>Nullable columns ({@code title}, {@code source_id}, {@code last_confirmed_at})
     * are handled with {@link Cursor#isNull} before reading.</p>
     *
     * @param cursor a Cursor positioned on a valid row from the memories table
     * @return the mapped Memory object
     */
    private Memory cursorToMemory(Cursor cursor) {
        String id          = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_ID));
        String typeName    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_TYPE));
        String origText    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_ORIGINAL_TEXT));
        String statusName  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_STATUS));
        String srcTypeName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_SOURCE_TYPE));
        long createdAt     = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_CREATED_AT));
        long updatedAt     = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_UPDATED_AT));

        // Nullable columns
        int titleIdx = cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_TITLE);
        String title = cursor.isNull(titleIdx) ? null : cursor.getString(titleIdx);

        int srcIdIdx = cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_SOURCE_ID);
        String sourceId = cursor.isNull(srcIdIdx) ? null : cursor.getString(srcIdIdx);

        int lastConfIdx = cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT);
        Long lastConfirmedAt = cursor.isNull(lastConfIdx) ? null : cursor.getLong(lastConfIdx);

        return new Memory(
                id,
                MemoryType.valueOf(typeName),
                origText,
                title,
                createdAt,
                updatedAt,
                SourceType.valueOf(srcTypeName),
                sourceId,
                MemoryStatus.valueOf(statusName),
                lastConfirmedAt
        );
    }

    /**
     * Converts a {@link Memory} domain object to a {@link ContentValues} map for SQLite writes.
     *
     * <p>All non-null fields are written. Nullable fields use {@link ContentValues#putNull}
     * when the value is null, ensuring that previous non-null values are overwritten on update
     * rather than left unchanged.</p>
     *
     * @param memory the memory to convert; must not be null
     * @return a ContentValues map suitable for INSERT or UPDATE
     */
    private ContentValues memoryToContentValues(Memory memory) {
        ContentValues values = new ContentValues(10);

        values.put(DatabaseConstants.Memories.COL_ID,            memory.getId());
        values.put(DatabaseConstants.Memories.COL_TYPE,          memory.getType().name());
        values.put(DatabaseConstants.Memories.COL_ORIGINAL_TEXT, memory.getOriginalText());
        values.put(DatabaseConstants.Memories.COL_CREATED_AT,    memory.getCreatedAt());
        values.put(DatabaseConstants.Memories.COL_UPDATED_AT,    memory.getUpdatedAt());
        values.put(DatabaseConstants.Memories.COL_SOURCE_TYPE,   memory.getSourceType().name());
        values.put(DatabaseConstants.Memories.COL_STATUS,        memory.getStatus().name());

        if (memory.getTitle() != null) {
            values.put(DatabaseConstants.Memories.COL_TITLE, memory.getTitle());
        } else {
            values.putNull(DatabaseConstants.Memories.COL_TITLE);
        }

        if (memory.getSourceId() != null) {
            values.put(DatabaseConstants.Memories.COL_SOURCE_ID, memory.getSourceId());
        } else {
            values.putNull(DatabaseConstants.Memories.COL_SOURCE_ID);
        }

        if (memory.getLastConfirmedAt() != null) {
            values.put(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT, memory.getLastConfirmedAt());
        } else {
            values.putNull(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT);
        }

        return values;
    }
}
