package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.MemoryRelation;
import dev.builtbyahnaf.memomint.domain.model.Provenance;
import dev.builtbyahnaf.memomint.domain.model.RelationType;

/**
 * SQLite-backed implementation of {@link MemoryRelationRepository}.
 *
 * <h2>Purpose</h2>
 * <p>Provides all CRUD operations for the {@code memory_relations} table, which stores
 * directed edges of the MemoMint knowledge graph (FR-07, Section 11.5). The implementation
 * is intentionally kept free of Android UI code so it can be called from any background thread
 * or from Robolectric tests.</p>
 *
 * <h2>Null-safety contract</h2>
 * <p>Optional nullable columns ({@code confidence}, {@code last_confirmed_at}) are written as
 * SQL NULL and read back as Java {@code null} using {@link Cursor#isNull}.</p>
 *
 * <h2>Enum serialisation</h2>
 * <p>{@link RelationType} and {@link Provenance} are stored as their {@link Enum#name()} strings
 * and recovered with the corresponding {@code valueOf()} call. Unknown strings fall back to
 * {@link RelationType#RELATED_TO} and {@link Provenance#EXTRACTED} respectively to avoid
 * crashes on schema evolution.</p>
 */
public class MemoryRelationRepositoryImpl implements MemoryRelationRepository {

    private static final String TAG = "MemoryRelationRepo";

    private final MemoMintDatabase dbHelper;

    /**
     * Constructs the repository with a reference to the shared database helper.
     *
     * @param dbHelper the MemoMintDatabase singleton; must not be null
     */
    public MemoryRelationRepositoryImpl(MemoMintDatabase dbHelper) {
        if (dbHelper == null) throw new IllegalArgumentException("dbHelper must not be null");
        this.dbHelper = dbHelper;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@link SQLiteDatabase#insertOrThrow} so callers receive a {@link RuntimeException}
     * on constraint violations rather than a silent {@code -1} return value.</p>
     */
    @Override
    public String save(MemoryRelation relation) {
        if (relation == null || relation.getId() == null) {
            throw new IllegalArgumentException("relation and its id must not be null");
        }

        ContentValues cv = toContentValues(relation);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.insertOrThrow(DatabaseConstants.MemoryRelations.TABLE, null, cv);
        return relation.getId();
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public MemoryRelation findById(String id) {
        if (id == null) return null;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sel = DatabaseConstants.MemoryRelations.COL_ID + " = ?";
        try (Cursor c = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                null, sel, new String[]{id}, null, null, null, "1")) {
            if (c != null && c.moveToFirst()) {
                return cursorToRelation(c);
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public List<MemoryRelation> findBySource(String sourceType, String sourceId) {
        List<MemoryRelation> results = new ArrayList<>();
        if (sourceType == null || sourceId == null) return results;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sel = DatabaseConstants.MemoryRelations.COL_SOURCE_TYPE + " = ? AND "
                + DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ?";
        try (Cursor c = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                null, sel, new String[]{sourceType, sourceId}, null, null,
                DatabaseConstants.MemoryRelations.COL_CREATED_AT + " DESC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    results.add(cursorToRelation(c));
                }
            }
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public List<MemoryRelation> findByTarget(String targetType, String targetId) {
        List<MemoryRelation> results = new ArrayList<>();
        if (targetType == null || targetId == null) return results;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sel = DatabaseConstants.MemoryRelations.COL_TARGET_TYPE + " = ? AND "
                + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?";
        try (Cursor c = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                null, sel, new String[]{targetType, targetId}, null, null,
                DatabaseConstants.MemoryRelations.COL_CREATED_AT + " DESC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    results.add(cursorToRelation(c));
                }
            }
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public List<MemoryRelation> findSupersessionChain(String memoryId) {
        List<MemoryRelation> results = new ArrayList<>();
        if (memoryId == null) return results;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        // Query: relation_type='SUPERSEDES' AND (source_id=? OR target_id=?)
        String sel = DatabaseConstants.MemoryRelations.COL_RELATION_TYPE + " = ? AND ("
                + DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ? OR "
                + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?)";
        try (Cursor c = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                null, sel,
                new String[]{RelationType.SUPERSEDES.name(), memoryId, memoryId},
                null, null,
                DatabaseConstants.MemoryRelations.COL_CREATED_AT + " DESC")) {
            if (c != null) {
                while (c.moveToNext()) {
                    results.add(cursorToRelation(c));
                }
            }
        }
        return results;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isSuperseded(String memoryId) {
        if (memoryId == null) return false;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sel = DatabaseConstants.MemoryRelations.COL_RELATION_TYPE + " = ? AND "
                + DatabaseConstants.MemoryRelations.COL_TARGET_ID + " = ?";
        try (Cursor c = db.query(
                DatabaseConstants.MemoryRelations.TABLE,
                new String[]{"COUNT(*)"},
                sel, new String[]{RelationType.SUPERSEDES.name(), memoryId},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getInt(0) > 0;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public boolean delete(String id) {
        if (id == null) return false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(
                DatabaseConstants.MemoryRelations.TABLE,
                DatabaseConstants.MemoryRelations.COL_ID + " = ?",
                new String[]{id});
        return rows > 0;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteBySource(String sourceType, String sourceId) {
        if (sourceType == null || sourceId == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
                DatabaseConstants.MemoryRelations.TABLE,
                DatabaseConstants.MemoryRelations.COL_SOURCE_TYPE + " = ? AND "
                        + DatabaseConstants.MemoryRelations.COL_SOURCE_ID + " = ?",
                new String[]{sourceType, sourceId});
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link MemoryRelation} domain object into a {@link ContentValues} map
     * ready for SQLite insert or replace.
     *
     * @param r the relation to serialise; must not be null
     * @return populated ContentValues
     */
    private ContentValues toContentValues(MemoryRelation r) {
        ContentValues cv = new ContentValues();
        cv.put(DatabaseConstants.MemoryRelations.COL_ID, r.getId());
        cv.put(DatabaseConstants.MemoryRelations.COL_SOURCE_TYPE, r.getSourceType());
        cv.put(DatabaseConstants.MemoryRelations.COL_SOURCE_ID, r.getSourceId());
        cv.put(DatabaseConstants.MemoryRelations.COL_RELATION_TYPE,
                r.getRelationType() != null ? r.getRelationType().name() : RelationType.RELATED_TO.name());
        cv.put(DatabaseConstants.MemoryRelations.COL_TARGET_TYPE, r.getTargetType());
        cv.put(DatabaseConstants.MemoryRelations.COL_TARGET_ID, r.getTargetId());
        cv.put(DatabaseConstants.MemoryRelations.COL_PROVENANCE,
                r.getProvenance() != null ? r.getProvenance().name() : Provenance.USER_ENTERED.name());
        if (r.getConfidence() != null) {
            cv.put(DatabaseConstants.MemoryRelations.COL_CONFIDENCE, r.getConfidence());
        } else {
            cv.putNull(DatabaseConstants.MemoryRelations.COL_CONFIDENCE);
        }
        cv.put(DatabaseConstants.MemoryRelations.COL_CREATED_AT, r.getCreatedAt());
        if (r.getLastConfirmedAt() != null) {
            cv.put(DatabaseConstants.MemoryRelations.COL_LAST_CONFIRMED_AT, r.getLastConfirmedAt());
        } else {
            cv.putNull(DatabaseConstants.MemoryRelations.COL_LAST_CONFIRMED_AT);
        }
        return cv;
    }

    /**
     * Reads a single {@link MemoryRelation} from the current position of the given cursor.
     *
     * <p>Unknown {@link RelationType} or {@link Provenance} strings are mapped to safe fallbacks
     * to avoid crashes when migrating data from older schema versions.</p>
     *
     * @param c an open, positioned cursor; must not be null
     * @return the reconstructed {@link MemoryRelation}
     */
    private MemoryRelation cursorToRelation(Cursor c) {
        MemoryRelation r = new MemoryRelation();
        r.setId(c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_ID)));
        r.setSourceType(c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_SOURCE_TYPE)));
        r.setSourceId(c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_SOURCE_ID)));

        // Enum: RelationType
        String rtStr = c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_RELATION_TYPE));
        RelationType rt;
        try { rt = RelationType.valueOf(rtStr); } catch (Exception e) { rt = RelationType.RELATED_TO; }
        r.setRelationType(rt);

        r.setTargetType(c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_TARGET_TYPE)));
        r.setTargetId(c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_TARGET_ID)));

        // Enum: Provenance
        String provStr = c.getString(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_PROVENANCE));
        Provenance prov;
        try { prov = Provenance.valueOf(provStr); } catch (Exception e) { prov = Provenance.EXTRACTED; }
        r.setProvenance(prov);

        int confIdx = c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_CONFIDENCE);
        r.setConfidence(c.isNull(confIdx) ? null : c.getDouble(confIdx));

        r.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_CREATED_AT)));

        int lcaIdx = c.getColumnIndexOrThrow(DatabaseConstants.MemoryRelations.COL_LAST_CONFIRMED_AT);
        r.setLastConfirmedAt(c.isNull(lcaIdx) ? null : c.getLong(lcaIdx));

        return r;
    }
}
