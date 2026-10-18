package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.context.ExtractionResult;
import dev.builtbyahnaf.memomint.domain.context.ItemVocabulary;
import dev.builtbyahnaf.memomint.domain.context.MemoryRelation;

/**
 * SQLite implementation of {@link ContextRepository} using raw Android SQLite APIs.
 *
 * <h2>Architectural Principles (Section 11, Section 22.1 &amp; Phase 3 Plan)</h2>
 * <ul>
 *   <li><b>Raw SQLite Execution:</b> Operates directly against {@link SQLiteDatabase} without Room or third-party ORMs,
 *       enforcing explicit column constant references from {@link DatabaseConstants}.</li>
 *   <li><b>Transactional Integrity:</b> All batch operations (such as saving context records or relation batches)
 *       are wrapped inside explicit database transactions with try-finally blocks to preserve ACID consistency.</li>
 *   <li><b>Idempotent Upserts:</b> Uses {@code INSERT OR REPLACE} for context records and {@code INSERT OR IGNORE}
 *       for entities and relation edges to guarantee idempotent re-indexing.</li>
 * </ul>
 */
public class ContextRepositoryImpl implements ContextRepository {

    private static final String TAG = "ContextRepositoryImpl";

    private final MemoMintDatabase dbHelper;

    /**
     * Constructs a ContextRepositoryImpl backed by the MemoMint database helper.
     *
     * @param dbHelper database helper singleton; must not be null
     */
    public ContextRepositoryImpl(MemoMintDatabase dbHelper) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper cannot be null");
    }

    @Override
    public void saveContextRecords(List<ExtractionResult> results, String memoryId) {
        if (results == null || results.isEmpty() || memoryId == null) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (ExtractionResult result : results) {
                ContentValues cv = new ContentValues();
                cv.put(DatabaseConstants.ContextRecords.COL_ID, UUID.randomUUID().toString());
                cv.put(DatabaseConstants.ContextRecords.COL_MEMORY_ID, memoryId);
                cv.put(DatabaseConstants.ContextRecords.COL_CONTEXT_TYPE, result.getType().name());
                cv.put(DatabaseConstants.ContextRecords.COL_CONTENT, result.getContent());
                cv.put(DatabaseConstants.ContextRecords.COL_GENERATOR_VERSION, ItemVocabulary.VOCABULARY_VERSION);
                cv.put(DatabaseConstants.ContextRecords.COL_CREATED_AT, now);
                cv.put(DatabaseConstants.ContextRecords.COL_UPDATED_AT, now);
                cv.put(DatabaseConstants.ContextRecords.COL_GENERATION_STATUS, "DONE");

                db.insertWithOnConflict(
                        DatabaseConstants.ContextRecords.TABLE,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_REPLACE
                );
            }
            db.setTransactionSuccessful();
        } catch (Throwable t) {
            Log.e(TAG, "Error saving context records for memory: " + memoryId, t);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public List<ExtractionResult> getContextForMemory(String memoryId) {
        if (memoryId == null) {
            return Collections.emptyList();
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<ExtractionResult> list = new ArrayList<>();

        String[] columns = {
                DatabaseConstants.ContextRecords.COL_ID,
                DatabaseConstants.ContextRecords.COL_MEMORY_ID,
                DatabaseConstants.ContextRecords.COL_CONTEXT_TYPE,
                DatabaseConstants.ContextRecords.COL_CONTENT,
                DatabaseConstants.ContextRecords.COL_GENERATOR_VERSION
        };

        String selection = DatabaseConstants.ContextRecords.COL_MEMORY_ID + " = ?";
        String[] selectionArgs = {memoryId};

        try (Cursor cursor = db.query(
                DatabaseConstants.ContextRecords.TABLE,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                DatabaseConstants.ContextRecords.COL_CREATED_AT + " ASC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int colMemoryId = cursor.getColumnIndexOrThrow(DatabaseConstants.ContextRecords.COL_MEMORY_ID);
                int colType = cursor.getColumnIndexOrThrow(DatabaseConstants.ContextRecords.COL_CONTEXT_TYPE);
                int colContent = cursor.getColumnIndexOrThrow(DatabaseConstants.ContextRecords.COL_CONTENT);
                int colGenVer = cursor.getColumnIndexOrThrow(DatabaseConstants.ContextRecords.COL_GENERATOR_VERSION);

                do {
                    String memId = cursor.getString(colMemoryId);
                    String typeStr = cursor.getString(colType);
                    String content = cursor.getString(colContent);
                    int genVersion = cursor.getInt(colGenVer);

                    ExtractionResult.Type type;
                    try {
                        type = ExtractionResult.Type.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        type = ExtractionResult.Type.KEYWORD;
                    }

                    list.add(new ExtractionResult(
                            memId,
                            type,
                            content != null ? content : "",
                            content != null ? content : "",
                            ExtractionResult.Provenance.EXTRACTED_BY_RULE,
                            genVersion
                    ));
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error querying context for memory: " + memoryId, t);
        }

        return list;
    }

    @Override
    public void deleteContextForMemory(String memoryId) {
        if (memoryId == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.delete(
                    DatabaseConstants.ContextRecords.TABLE,
                    DatabaseConstants.ContextRecords.COL_MEMORY_ID + " = ?",
                    new String[]{memoryId}
            );
        } catch (Throwable t) {
            Log.e(TAG, "Error deleting context for memory: " + memoryId, t);
        }
    }

    @Override
    public void upsertEntity(String canonicalLabel, String entityType, String originalLabel) {
        getOrCreateEntityId(canonicalLabel, entityType, originalLabel);
    }

    @Override
    public String getOrCreateEntityId(String canonicalLabel, String entityType, String originalLabel) {
        if (canonicalLabel == null || entityType == null) {
            return UUID.randomUUID().toString();
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String selection = DatabaseConstants.Entities.COL_CANONICAL_LABEL + " = ? AND "
                + DatabaseConstants.Entities.COL_ENTITY_TYPE + " = ?";
        String[] selectionArgs = {canonicalLabel, entityType};

        try (Cursor cursor = db.query(
                DatabaseConstants.Entities.TABLE,
                new String[]{DatabaseConstants.Entities.COL_ID},
                selection,
                selectionArgs,
                null,
                null,
                null,
                "1"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error looking up entity: " + canonicalLabel, t);
        }

        // Entity does not exist; insert new entity row
        String newId = UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseConstants.Entities.COL_ID, newId);
        cv.put(DatabaseConstants.Entities.COL_CANONICAL_LABEL, canonicalLabel);
        cv.put(DatabaseConstants.Entities.COL_ENTITY_TYPE, entityType);
        cv.put(DatabaseConstants.Entities.COL_ORIGINAL_LABEL, originalLabel != null ? originalLabel : canonicalLabel);

        try {
            db.insertWithOnConflict(
                    DatabaseConstants.Entities.TABLE,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        } catch (Throwable t) {
            Log.e(TAG, "Error inserting entity: " + canonicalLabel, t);
        }

        return newId;
    }

    @Override
    public void saveMemoryEntityLink(String memoryId, String entityId, String role, String canonicalLabel, String entityType) {
        if (memoryId == null || entityId == null || role == null) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseConstants.MemoryEntities.COL_MEMORY_ID, memoryId);
        cv.put(DatabaseConstants.MemoryEntities.COL_ENTITY_ID, entityId);
        cv.put(DatabaseConstants.MemoryEntities.COL_ROLE, role);
        cv.put(DatabaseConstants.MemoryEntities.COL_PROVENANCE, "EXTRACTED");
        cv.put(DatabaseConstants.MemoryEntities.COL_CONFIDENCE, 0.80);

        try {
            db.insertWithOnConflict(
                    DatabaseConstants.MemoryEntities.TABLE,
                    null,
                    cv,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        } catch (Throwable t) {
            Log.e(TAG, "Error saving memory-entity link: " + memoryId + " -> " + entityId, t);
        }
    }

    @Override
    public List<String> findMemoriesByEntityLabel(String canonicalLabel) {
        if (canonicalLabel == null || canonicalLabel.trim().isEmpty()) {
            return Collections.emptyList();
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<String> memoryIds = new ArrayList<>();

        String sql = "SELECT DISTINCT me." + DatabaseConstants.MemoryEntities.COL_MEMORY_ID
                + " FROM " + DatabaseConstants.MemoryEntities.TABLE + " me"
                + " JOIN " + DatabaseConstants.Entities.TABLE + " e"
                + " ON me." + DatabaseConstants.MemoryEntities.COL_ENTITY_ID + " = e." + DatabaseConstants.Entities.COL_ID
                + " WHERE e." + DatabaseConstants.Entities.COL_CANONICAL_LABEL + " = ?";

        try (Cursor cursor = db.rawQuery(sql, new String[]{canonicalLabel.trim()})) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String memId = cursor.getString(0);
                    if (memId != null && !memoryIds.contains(memId)) {
                        memoryIds.add(memId);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error finding memories by entity label: " + canonicalLabel, t);
        }

        return memoryIds;
    }

    @Override
    public void saveMemoryRelations(List<MemoryRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (MemoryRelation rel : relations) {
                ContentValues cv = new ContentValues();
                cv.put(DatabaseConstants.MemoryRelations.COL_ID, rel.getId());
                cv.put(DatabaseConstants.MemoryRelations.COL_SOURCE_TYPE, DatabaseConstants.RelationParty.MEMORY);
                cv.put(DatabaseConstants.MemoryRelations.COL_SOURCE_ID, rel.getSourceMemoryId());
                cv.put(DatabaseConstants.MemoryRelations.COL_RELATION_TYPE, rel.getRelationType());
                cv.put(DatabaseConstants.MemoryRelations.COL_TARGET_TYPE, DatabaseConstants.RelationParty.MEMORY);
                cv.put(DatabaseConstants.MemoryRelations.COL_TARGET_ID, rel.getTargetMemoryId());
                cv.put(DatabaseConstants.MemoryRelations.COL_PROVENANCE, rel.getProvenance());
                cv.put(DatabaseConstants.MemoryRelations.COL_CONFIDENCE, rel.getConfidence());
                cv.put(DatabaseConstants.MemoryRelations.COL_CREATED_AT, rel.getCreatedAt());

                db.insertWithOnConflict(
                        DatabaseConstants.MemoryRelations.TABLE,
                        null,
                        cv,
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }
            db.setTransactionSuccessful();
        } catch (Throwable t) {
            Log.e(TAG, "Error saving memory relations batch", t);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public boolean relationExists(String sourceMemoryId, String targetMemoryId, String relationType) {
        if (sourceMemoryId == null || targetMemoryId == null || relationType == null) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT COUNT(*) FROM " + DatabaseConstants.MemoryRelations.TABLE
                + " WHERE " + DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ?"
                + " AND " + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?"
                + " AND " + DatabaseConstants.MemoryRelations.COL_RELATION_TYPE + " = ?";

        try (Cursor cursor = db.rawQuery(sql, new String[]{sourceMemoryId, targetMemoryId, relationType})) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error checking relation existence", t);
        }

        return false;
    }

    @Override
    public List<MemoryRelation> getRelationsForMemory(String memoryId) {
        if (memoryId == null) {
            return Collections.emptyList();
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<MemoryRelation> relations = new ArrayList<>();

        String[] columns = {
                DatabaseConstants.MemoryRelations.COL_ID,
                DatabaseConstants.MemoryRelations.COL_SOURCE_ID,
                DatabaseConstants.MemoryRelations.COL_RELATION_TYPE,
                DatabaseConstants.MemoryRelations.COL_TARGET_ID,
                DatabaseConstants.MemoryRelations.COL_CONFIDENCE,
                DatabaseConstants.MemoryRelations.COL_PROVENANCE,
                DatabaseConstants.MemoryRelations.COL_CREATED_AT
        };

        String selection = DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ? OR "
                + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?";
        String[] selectionArgs = {memoryId, memoryId};

        try (Cursor cursor = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                columns,
                selection,
                selectionArgs,
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                int colId = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_ID);
                int colSource = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_SOURCE_ID);
                int colRelType = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_RELATION_TYPE);
                int colTarget = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_TARGET_ID);
                int colConf = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_CONFIDENCE);
                int colProv = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_PROVENANCE);
                int colCreated = cursor.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_CREATED_AT);

                do {
                    String id = cursor.getString(colId);
                    String src = cursor.getString(colSource);
                    String type = cursor.getString(colRelType);
                    String tgt = cursor.getString(colTarget);
                    double conf = cursor.isNull(colConf) ? 0.70 : cursor.getDouble(colConf);
                    String prov = cursor.getString(colProv);
                    long created = cursor.getLong(colCreated);

                    relations.add(new MemoryRelation(id, src, type, tgt, conf, prov, created));
                } while (cursor.moveToNext());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error fetching relations for memory: " + memoryId, t);
        }

        return relations;
    }

    @Override
    public void deleteRelationsForMemory(String memoryId) {
        if (memoryId == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            String where = DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ? OR "
                    + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?";
            db.delete(DatabaseConstants.MemoryRelations.TABLE, where, new String[]{memoryId, memoryId});
        } catch (Throwable t) {
            Log.e(TAG, "Error deleting relations for memory: " + memoryId, t);
        }
    }

    @Override
    public void deleteRelationsAndContextForMemory(String memoryId) {
        if (memoryId == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Delete context records
            db.delete(
                    DatabaseConstants.ContextRecords.TABLE,
                    DatabaseConstants.ContextRecords.COL_MEMORY_ID + " = ?",
                    new String[]{memoryId}
            );

            // Delete memory-entity links
            db.delete(
                    DatabaseConstants.MemoryEntities.TABLE,
                    DatabaseConstants.MemoryEntities.COL_MEMORY_ID + " = ?",
                    new String[]{memoryId}
            );

            // Delete relations
            String whereRel = DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ? OR "
                    + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?";
            db.delete(DatabaseConstants.MemoryRelations.TABLE, whereRel, new String[]{memoryId, memoryId});

            db.setTransactionSuccessful();
        } catch (Throwable t) {
            Log.e(TAG, "Error in deleteRelationsAndContextForMemory: " + memoryId, t);
        } finally {
            db.endTransaction();
        }
    }
}
