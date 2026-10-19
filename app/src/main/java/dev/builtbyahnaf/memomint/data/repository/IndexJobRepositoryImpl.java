package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants.IndexJobs;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.IndexJob;

/**
 * SQLite-backed implementation of {@link IndexJobRepository}.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.3)</h2>
 * <p>Persists document indexing jobs and coordinates queue state transitions.</p>
 */
public class IndexJobRepositoryImpl implements IndexJobRepository {

    private final MemoMintDatabase dbHelper;

    public IndexJobRepositoryImpl(MemoMintDatabase dbHelper) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper cannot be null");
    }

    @Override
    public void enqueue(IndexJob job) {
        if (job == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toContentValues(job);

        db.insertWithOnConflict(
                IndexJobs.TABLE,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    @Override
    public IndexJob findNextPendingJob() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + IndexJobs.TABLE
                + " WHERE " + IndexJobs.COL_STATUS + " = '" + IndexJobs.Status.PENDING + "'"
                + " ORDER BY " + IndexJobs.COL_UPDATED_AT + " ASC LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public IndexJob findById(String id) {
        if (id == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + IndexJobs.TABLE
                + " WHERE " + IndexJobs.COL_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{id})) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public IndexJob findBySourceId(String sourceId) {
        if (sourceId == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + IndexJobs.TABLE
                + " WHERE " + IndexJobs.COL_SOURCE_ID + " = ?"
                + " ORDER BY " + IndexJobs.COL_UPDATED_AT + " DESC LIMIT 1";

        try (Cursor cursor = db.rawQuery(query, new String[]{sourceId})) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public void updateStatus(String jobId, String status, int progress, String errorCode) {
        if (jobId == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(IndexJobs.COL_STATUS, status);
        cv.put(IndexJobs.COL_PROGRESS, Math.max(0, Math.min(100, progress)));
        cv.put(IndexJobs.COL_UPDATED_AT, System.currentTimeMillis());
        if (errorCode != null) {
            cv.put(IndexJobs.COL_ERROR_CODE, errorCode);
        }

        if (IndexJobs.Status.RUNNING.equals(status)) {
            // Set started_at if not previously set
            IndexJob existing = findById(jobId);
            if (existing != null && existing.getStartedAt() == null) {
                cv.put(IndexJobs.COL_STARTED_AT, System.currentTimeMillis());
            }
        }

        db.update(
                IndexJobs.TABLE,
                cv,
                IndexJobs.COL_ID + " = ?",
                new String[]{jobId}
        );
    }

    @Override
    public List<IndexJob> findActiveJobs() {
        List<IndexJob> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + IndexJobs.TABLE
                + " WHERE " + IndexJobs.COL_STATUS + " IN ('"
                + IndexJobs.Status.PENDING + "', '" + IndexJobs.Status.RUNNING + "')"
                + " ORDER BY " + IndexJobs.COL_UPDATED_AT + " ASC";

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(fromCursor(cursor));
                }
            }
        }
        return list;
    }

    @Override
    public List<IndexJob> findRecentJobs(int limit) {
        List<IndexJob> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + IndexJobs.TABLE
                + " ORDER BY " + IndexJobs.COL_UPDATED_AT + " DESC"
                + " LIMIT " + Math.max(1, limit);

        try (Cursor cursor = db.rawQuery(query, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(fromCursor(cursor));
                }
            }
        }
        return list;
    }

    private static ContentValues toContentValues(IndexJob job) {
        ContentValues cv = new ContentValues();
        cv.put(IndexJobs.COL_ID, job.getId());
        cv.put(IndexJobs.COL_SOURCE_ID, job.getSourceId());
        cv.put(IndexJobs.COL_STATUS, job.getStatus());
        cv.put(IndexJobs.COL_PROGRESS, job.getProgress());
        cv.put(IndexJobs.COL_STARTED_AT, job.getStartedAt());
        cv.put(IndexJobs.COL_UPDATED_AT, job.getUpdatedAt());
        cv.put(IndexJobs.COL_ERROR_CODE, job.getErrorCode());
        return cv;
    }

    private static IndexJob fromCursor(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(IndexJobs.COL_ID));
        String sourceId = cursor.getString(cursor.getColumnIndexOrThrow(IndexJobs.COL_SOURCE_ID));
        String status = cursor.getString(cursor.getColumnIndexOrThrow(IndexJobs.COL_STATUS));
        int progress = cursor.getInt(cursor.getColumnIndexOrThrow(IndexJobs.COL_PROGRESS));

        int startIdx = cursor.getColumnIndexOrThrow(IndexJobs.COL_STARTED_AT);
        Long startedAt = cursor.isNull(startIdx) ? null : cursor.getLong(startIdx);

        long updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(IndexJobs.COL_UPDATED_AT));

        int errIdx = cursor.getColumnIndexOrThrow(IndexJobs.COL_ERROR_CODE);
        String errorCode = cursor.isNull(errIdx) ? null : cursor.getString(errIdx);

        return new IndexJob(
                id,
                sourceId,
                status,
                progress,
                startedAt,
                updatedAt,
                errorCode
        );
    }
}
