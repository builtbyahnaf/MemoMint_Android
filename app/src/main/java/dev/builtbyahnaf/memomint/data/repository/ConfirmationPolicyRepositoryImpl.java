package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.ConfirmationPolicy;

/**
 * SQLite-backed implementation of {@link ConfirmationPolicyRepository}.
 *
 * <h2>Purpose</h2>
 * <p>Manages persistence and retrieval of {@link ConfirmationPolicy} schedules for physical-item
 * and other relevant memories using the {@code confirmation_policies} table defined in
 * {@link DatabaseConstants.ConfirmationPolicies}.</p>
 *
 * <h2>Design &amp; Invariants</h2>
 * <ul>
 *   <li>One-to-one relationship with memories table keyed by {@code memory_id}.</li>
 *   <li>Uses {@link SQLiteDatabase#CONFLICT_REPLACE} for atomic upsert operations.</li>
 *   <li>Thread-safe when accessed through background threads.</li>
 * </ul>
 */
public class ConfirmationPolicyRepositoryImpl implements ConfirmationPolicyRepository {

    private final MemoMintDatabase dbHelper;

    /**
     * Constructs a new {@link ConfirmationPolicyRepositoryImpl} instance.
     *
     * @param dbHelper the database helper used to acquire readable and writable database connections;
     *                 must not be null
     * @throws IllegalArgumentException if dbHelper is null
     */
    public ConfirmationPolicyRepositoryImpl(MemoMintDatabase dbHelper) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("dbHelper must not be null");
        }
        this.dbHelper = dbHelper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Inserts or replaces the confirmation policy row using {@link SQLiteDatabase#CONFLICT_REPLACE}.</p>
     *
     * @param policy the policy to persist; must not be null and its memoryId must not be null
     * @throws IllegalArgumentException if {@code policy} or {@code policy.getMemoryId()} is null
     */
    @Override
    public void saveOrUpdate(ConfirmationPolicy policy) {
        if (policy == null || policy.getMemoryId() == null) {
            throw new IllegalArgumentException("policy and policy.getMemoryId() must not be null");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toContentValues(policy);

        db.insertWithOnConflict(
                DatabaseConstants.ConfirmationPolicies.TABLE,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    /**
     * {@inheritDoc}
     *
     * @param memoryId the stable UUID of the memory whose policy should be retrieved
     * @return the {@link ConfirmationPolicy}, or {@code null} if no policy was found
     */
    @Override
    public ConfirmationPolicy findByMemoryId(String memoryId) {
        if (memoryId == null) {
            return null;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID + " = ?";
        String[] selectionArgs = new String[]{memoryId};

        try (Cursor cursor = db.query(
                DatabaseConstants.ConfirmationPolicies.TABLE,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null,
                "1"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToPolicy(cursor);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Finds all rows where {@code enabled=1} and {@code next_check_at <= nowMillis},
     * ordered chronologically by {@code next_check_at}.</p>
     *
     * @param nowMillis UTC epoch milliseconds reference time
     * @return list of due policies; never null; may be empty
     */
    @Override
    public List<ConfirmationPolicy> findDueForCheckIn(long nowMillis) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = DatabaseConstants.ConfirmationPolicies.COL_ENABLED + " = 1 AND "
                + DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT + " <= ?";
        String[] selectionArgs = new String[]{String.valueOf(nowMillis)};
        String orderBy = DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT + " ASC";

        List<ConfirmationPolicy> results = new ArrayList<>();
        try (Cursor cursor = db.query(
                DatabaseConstants.ConfirmationPolicies.TABLE,
                null,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    results.add(cursorToPolicy(cursor));
                }
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Targeted update of {@code next_check_at} and {@code last_prompted_at} columns only.</p>
     *
     * @param memoryId        the stable UUID of the memory whose policy should be updated
     * @param nextCheckAtMs   the new scheduled time for the next check-in prompt, in UTC epoch ms
     * @param lastPromptedAt  the timestamp when the prompt was most recently issued; may be null
     * @return {@code true} if a row was updated; {@code false} if no matching memoryId was found
     */
    @Override
    public boolean updateNextCheckAt(String memoryId, long nextCheckAtMs, Long lastPromptedAt) {
        if (memoryId == null) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT, nextCheckAtMs);
        if (lastPromptedAt != null) {
            cv.put(DatabaseConstants.ConfirmationPolicies.COL_LAST_PROMPTED_AT, lastPromptedAt);
        } else {
            cv.putNull(DatabaseConstants.ConfirmationPolicies.COL_LAST_PROMPTED_AT);
        }

        String whereClause = DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID + " = ?";
        String[] whereArgs = new String[]{memoryId};

        int rows = db.update(DatabaseConstants.ConfirmationPolicies.TABLE, cv, whereClause, whereArgs);
        return rows > 0;
    }

    /**
     * {@inheritDoc}
     *
     * @param memoryId the stable UUID of the memory whose policy should be removed
     * @return {@code true} if a row was deleted; {@code false} if no matching memoryId was found
     */
    @Override
    public boolean delete(String memoryId) {
        if (memoryId == null) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String whereClause = DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID + " = ?";
        String[] whereArgs = new String[]{memoryId};

        int rows = db.delete(DatabaseConstants.ConfirmationPolicies.TABLE, whereClause, whereArgs);
        return rows > 0;
    }

    /**
     * Reads a single {@link ConfirmationPolicy} from the current position of the cursor.
     *
     * @param c an open, positioned cursor; must not be null
     * @return hydrated {@link ConfirmationPolicy}
     */
    private ConfirmationPolicy cursorToPolicy(Cursor c) {
        String memoryId = c.getString(c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID));
        boolean enabled = c.getInt(c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_ENABLED)) == 1;
        String intervalType = c.getString(c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_TYPE));
        int intervalValue = c.getInt(c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_VALUE));

        int lastPromptedIdx = c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_LAST_PROMPTED_AT);
        Long lastPromptedAt = c.isNull(lastPromptedIdx) ? null : c.getLong(lastPromptedIdx);

        int nextCheckIdx = c.getColumnIndexOrThrow(DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT);
        Long nextCheckAt = c.isNull(nextCheckIdx) ? null : c.getLong(nextCheckIdx);

        return new ConfirmationPolicy(memoryId, enabled, intervalType, intervalValue, lastPromptedAt, nextCheckAt);
    }

    /**
     * Serialises a {@link ConfirmationPolicy} into a {@link ContentValues} instance.
     *
     * @param p the policy to convert; must not be null
     * @return populated {@link ContentValues}
     */
    private ContentValues toContentValues(ConfirmationPolicy p) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID, p.getMemoryId());
        cv.put(DatabaseConstants.ConfirmationPolicies.COL_ENABLED, p.isEnabled() ? 1 : 0);
        cv.put(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_TYPE, p.getIntervalType());
        cv.put(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_VALUE, p.getIntervalValue());

        if (p.getLastPromptedAt() != null) {
            cv.put(DatabaseConstants.ConfirmationPolicies.COL_LAST_PROMPTED_AT, p.getLastPromptedAt());
        } else {
            cv.putNull(DatabaseConstants.ConfirmationPolicies.COL_LAST_PROMPTED_AT);
        }

        if (p.getNextCheckAt() != null) {
            cv.put(DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT, p.getNextCheckAt());
        } else {
            cv.putNull(DatabaseConstants.ConfirmationPolicies.COL_NEXT_CHECK_AT);
        }

        return cv;
    }
}
