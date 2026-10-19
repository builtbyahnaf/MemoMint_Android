package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.DatabaseConstants.FileReferences;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.AccessState;
import dev.builtbyahnaf.memomint.domain.model.FileReference;
import dev.builtbyahnaf.memomint.domain.model.IndexingStatus;

/**
 * SQLite-backed implementation of {@link FileReferenceRepository}.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.3)</h2>
 * <ul>
 *   <li><b>Raw SQLite Execution:</b> Operates directly against SQLiteOpenHelper using
 *       column constants from {@link DatabaseConstants} without ORM reflection.</li>
 *   <li><b>Clean Unlinking:</b> Deleting a file reference removes associated rows in {@code files_fts}
 *       and {@code index_jobs} within an atomic transaction.</li>
 * </ul>
 */
public class FileReferenceRepositoryImpl implements FileReferenceRepository {

    private final MemoMintDatabase dbHelper;

    public FileReferenceRepositoryImpl(MemoMintDatabase dbHelper) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper cannot be null");
    }

    @Override
    public void save(FileReference fileReference) {
        if (fileReference == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toContentValues(fileReference);

        db.insertWithOnConflict(
                FileReferences.TABLE,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    @Override
    public FileReference findById(String id) {
        if (id == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + FileReferences.TABLE
                + " WHERE " + FileReferences.COL_ID + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{id})) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public FileReference findByUri(String uriOrPath) {
        if (uriOrPath == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + FileReferences.TABLE
                + " WHERE " + FileReferences.COL_URI_OR_PATH + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{uriOrPath})) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public List<FileReference> findAll(int limit) {
        List<FileReference> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + FileReferences.TABLE
                + " ORDER BY " + FileReferences.COL_DISPLAY_NAME + " ASC"
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

    @Override
    public List<FileReference> findByAccessState(AccessState state) {
        List<FileReference> list = new ArrayList<>();
        if (state == null) return list;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT * FROM " + FileReferences.TABLE
                + " WHERE " + FileReferences.COL_ACCESS_STATE + " = ?";

        try (Cursor cursor = db.rawQuery(query, new String[]{state.name()})) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    list.add(fromCursor(cursor));
                }
            }
        }
        return list;
    }

    @Override
    public void updateAccessState(String id, AccessState state, long verifiedAt) {
        if (id == null || state == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FileReferences.COL_ACCESS_STATE, state.name());
        cv.put(FileReferences.COL_LAST_VERIFIED_AT, verifiedAt);

        db.update(
                FileReferences.TABLE,
                cv,
                FileReferences.COL_ID + " = ?",
                new String[]{id}
        );
    }

    @Override
    public void updateIndexingStatus(String id, IndexingStatus status, Long indexedAt, String fingerprint) {
        if (id == null || status == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(FileReferences.COL_INDEXING_STATUS, status.name());
        if (indexedAt != null) {
            cv.put(FileReferences.COL_LAST_INDEXED_AT, indexedAt);
        }
        if (fingerprint != null) {
            cv.put(FileReferences.COL_CONTENT_FINGERPRINT, fingerprint);
        }

        db.update(
                FileReferences.TABLE,
                cv,
                FileReferences.COL_ID + " = ?",
                new String[]{id}
        );
    }

    @Override
    public void delete(String id) {
        if (id == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Delete associated files_fts entries if virtual table exists
            try {
                db.delete(
                        DatabaseConstants.FilesFts.TABLE,
                        DatabaseConstants.FilesFts.COL_FILE_ID + " = ?",
                        new String[]{id}
                );
            } catch (Exception ignored) {}

            // 2. Delete associated index jobs
            db.delete(
                    DatabaseConstants.IndexJobs.TABLE,
                    DatabaseConstants.IndexJobs.COL_SOURCE_ID + " = ?",
                    new String[]{id}
            );

            // 3. Delete file reference record
            db.delete(
                    FileReferences.TABLE,
                    FileReferences.COL_ID + " = ?",
                    new String[]{id}
            );

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static ContentValues toContentValues(FileReference ref) {
        ContentValues cv = new ContentValues();
        cv.put(FileReferences.COL_ID, ref.getId());
        cv.put(FileReferences.COL_URI_OR_PATH, ref.getUriOrPath());
        cv.put(FileReferences.COL_DISPLAY_NAME, ref.getDisplayName());
        cv.put(FileReferences.COL_MIME_TYPE, ref.getMimeType());
        cv.put(FileReferences.COL_SIZE_BYTES, ref.getSizeBytes());
        cv.put(FileReferences.COL_LAST_MODIFIED_AT, ref.getLastModifiedAt());
        cv.put(FileReferences.COL_LAST_INDEXED_AT, ref.getLastIndexedAt());
        cv.put(FileReferences.COL_CONTENT_FINGERPRINT, ref.getContentFingerprint());
        cv.put(FileReferences.COL_ACCESS_STATE, ref.getAccessState() != null ? ref.getAccessState().name() : AccessState.NOT_YET_VERIFIED.name());
        cv.put(FileReferences.COL_LAST_VERIFIED_AT, ref.getLastVerifiedAt());
        cv.put(FileReferences.COL_INDEXING_STATUS, ref.getIndexingStatus() != null ? ref.getIndexingStatus().name() : IndexingStatus.PENDING.name());
        cv.put(FileReferences.COL_EXCLUDED, ref.isExcluded() ? 1 : 0);
        return cv;
    }

    private static FileReference fromCursor(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(FileReferences.COL_ID));
        String uri = cursor.getString(cursor.getColumnIndexOrThrow(FileReferences.COL_URI_OR_PATH));
        String displayName = cursor.getString(cursor.getColumnIndexOrThrow(FileReferences.COL_DISPLAY_NAME));

        int mimeIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_MIME_TYPE);
        String mime = cursor.isNull(mimeIdx) ? null : cursor.getString(mimeIdx);

        int sizeIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_SIZE_BYTES);
        Long size = cursor.isNull(sizeIdx) ? null : cursor.getLong(sizeIdx);

        int modIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_LAST_MODIFIED_AT);
        Long lastModified = cursor.isNull(modIdx) ? null : cursor.getLong(modIdx);

        int idxIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_LAST_INDEXED_AT);
        Long lastIndexed = cursor.isNull(idxIdx) ? null : cursor.getLong(idxIdx);

        int fpIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_CONTENT_FINGERPRINT);
        String fingerprint = cursor.isNull(fpIdx) ? null : cursor.getString(fpIdx);

        String accessStr = cursor.getString(cursor.getColumnIndexOrThrow(FileReferences.COL_ACCESS_STATE));
        AccessState accessState;
        try {
            accessState = AccessState.valueOf(accessStr);
        } catch (IllegalArgumentException e) {
            accessState = AccessState.NOT_YET_VERIFIED;
        }

        int verIdx = cursor.getColumnIndexOrThrow(FileReferences.COL_LAST_VERIFIED_AT);
        Long lastVerified = cursor.isNull(verIdx) ? null : cursor.getLong(verIdx);

        String indexStr = cursor.getString(cursor.getColumnIndexOrThrow(FileReferences.COL_INDEXING_STATUS));
        IndexingStatus indexingStatus;
        try {
            indexingStatus = IndexingStatus.valueOf(indexStr);
        } catch (IllegalArgumentException e) {
            indexingStatus = IndexingStatus.PENDING;
        }

        int excludedInt = cursor.getInt(cursor.getColumnIndexOrThrow(FileReferences.COL_EXCLUDED));
        boolean excluded = (excludedInt == 1);

        return new FileReference(
                id,
                uri,
                displayName,
                mime,
                size,
                lastModified,
                lastIndexed,
                fingerprint,
                accessState,
                lastVerified,
                indexingStatus,
                excluded
        );
    }
}
