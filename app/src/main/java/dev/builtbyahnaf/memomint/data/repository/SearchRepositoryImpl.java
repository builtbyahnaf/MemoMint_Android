package dev.builtbyahnaf.memomint.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

/**
 * SQLite FTS (FTS5/FTS4) and SQL-backed implementation of {@link SearchRepository}.
 *
 * <h2>Purpose & Architectural Invariants</h2>
 * <p>Implements Layer D (Data and Indexing) candidate retrieval queries.
 * Queries {@code memories_fts} to fetch relevant candidates ranked by SQLite's native BM25
 * scoring function where supported (FTS5), with graceful fallback to standard FTS4 matching
 * and case-insensitive substring (LIKE) scanning to guarantee full recall across all Android versions.</p>
 */
public class SearchRepositoryImpl implements SearchRepository {

    private static final String TAG = "SearchRepoImpl";

    private final MemoMintDatabase dbHelper;

    /**
     * Constructs a SearchRepositoryImpl backed by the given database helper.
     *
     * @param dbHelper the MemoMintDatabase instance; must not be null
     */
    public SearchRepositoryImpl(MemoMintDatabase dbHelper) {
        if (dbHelper == null) {
            throw new IllegalArgumentException("dbHelper must not be null");
        }
        this.dbHelper = dbHelper;
    }

    /**
     * Sanitizes a raw keyword string into a safe SQLite FTS MATCH expression.
     * Generates prefix tokens (e.g., "air*") to support partial/prefix matching (e.g. "air" -> "Airpods").
     *
     * @param query raw query or expanded term string
     * @return safe FTS MATCH expression
     */
    public static String sanitizeFtsQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        // Split into alphanumeric words
        String[] parts = query.trim().split("[^\\p{L}\\p{N}_-]+");
        List<String> validTokens = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty()) {
                // In SQLite FTS (both FTS4 and FTS5), prefix matching is bareword* (e.g. air*).
                // Do NOT wrap in double quotes like "air"*, which causes SQLite FTS syntax errors.
                validTokens.add(token + "*");
            }
        }

        if (validTokens.isEmpty()) {
            return "";
        }

        // Join tokens with OR so that documents matching any token are retrieved as candidates
        return String.join(" OR ", validTokens);
    }

    @Override
    public List<FtsCandidate> searchFts(String ftsQuery, int limit) {
        List<FtsCandidate> candidates = new ArrayList<>();
        if (ftsQuery == null || ftsQuery.trim().isEmpty() || limit <= 0) {
            return candidates;
        }

        String safeQuery = sanitizeFtsQuery(ftsQuery);
        if (safeQuery.isEmpty()) {
            return candidates;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Set<String> seenIds = new HashSet<>();

        // 1. Try primary FTS5 query with BM25 ranking
        boolean ftsQuerySucceeded = false;
        String sqlFts5 = "SELECT m." + DatabaseConstants.Memories.COL_ID + ", "
                + "m." + DatabaseConstants.Memories.COL_TYPE + ", "
                + "m." + DatabaseConstants.Memories.COL_ORIGINAL_TEXT + ", "
                + "m." + DatabaseConstants.Memories.COL_TITLE + ", "
                + "m." + DatabaseConstants.Memories.COL_CREATED_AT + ", "
                + "m." + DatabaseConstants.Memories.COL_UPDATED_AT + ", "
                + "m." + DatabaseConstants.Memories.COL_SOURCE_TYPE + ", "
                + "m." + DatabaseConstants.Memories.COL_SOURCE_ID + ", "
                + "m." + DatabaseConstants.Memories.COL_STATUS + ", "
                + "m." + DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT + ", "
                + "bm25(" + DatabaseConstants.MemoriesFts.TABLE + ") AS bm25_score, "
                + "snippet(" + DatabaseConstants.MemoriesFts.TABLE + ", -1, '<b>', '</b>', '...', 15) AS match_snippet "
                + "FROM " + DatabaseConstants.MemoriesFts.TABLE + " fts "
                + "JOIN " + DatabaseConstants.Memories.TABLE + " m ON fts." + DatabaseConstants.MemoriesFts.COL_ID
                + " = m." + DatabaseConstants.Memories.COL_ID + " "
                + "WHERE " + DatabaseConstants.MemoriesFts.TABLE + " MATCH ? "
                + "  AND m." + DatabaseConstants.Memories.COL_STATUS + " != '" + MemoryStatus.DELETED.name() + "' "
                + "ORDER BY bm25_score ASC "
                + "LIMIT ?";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sqlFts5, new String[]{safeQuery, String.valueOf(limit)});
            while (cursor.moveToNext()) {
                Memory memory = cursorToMemory(cursor);
                double rawBm25 = cursor.getDouble(10);
                String snippet = cursor.getString(11);
                candidates.add(new FtsCandidate(memory, rawBm25, snippet));
                seenIds.add(memory.getId());
            }
            ftsQuerySucceeded = true;
        } catch (Exception e) {
            Log.w(TAG, "searchFts FTS5 query failed (falling back to FTS4 without bm25): " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        // 2. Fallback to FTS4 query if FTS5 (or bm25 function) is unavailable on this device
        if (!ftsQuerySucceeded) {
            String sqlFts4 = "SELECT m." + DatabaseConstants.Memories.COL_ID + ", "
                    + "m." + DatabaseConstants.Memories.COL_TYPE + ", "
                    + "m." + DatabaseConstants.Memories.COL_ORIGINAL_TEXT + ", "
                    + "m." + DatabaseConstants.Memories.COL_TITLE + ", "
                    + "m." + DatabaseConstants.Memories.COL_CREATED_AT + ", "
                    + "m." + DatabaseConstants.Memories.COL_UPDATED_AT + ", "
                    + "m." + DatabaseConstants.Memories.COL_SOURCE_TYPE + ", "
                    + "m." + DatabaseConstants.Memories.COL_SOURCE_ID + ", "
                    + "m." + DatabaseConstants.Memories.COL_STATUS + ", "
                    + "m." + DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT + " "
                    + "FROM " + DatabaseConstants.MemoriesFts.TABLE + " fts "
                    + "JOIN " + DatabaseConstants.Memories.TABLE + " m ON fts." + DatabaseConstants.MemoriesFts.COL_ID
                    + " = m." + DatabaseConstants.Memories.COL_ID + " "
                    + "WHERE " + DatabaseConstants.MemoriesFts.TABLE + " MATCH ? "
                    + "  AND m." + DatabaseConstants.Memories.COL_STATUS + " != '" + MemoryStatus.DELETED.name() + "' "
                    + "LIMIT ?";

            try {
                cursor = db.rawQuery(sqlFts4, new String[]{safeQuery, String.valueOf(limit)});
                while (cursor.moveToNext()) {
                    Memory memory = cursorToMemory(cursor);
                    if (!seenIds.contains(memory.getId())) {
                        candidates.add(new FtsCandidate(memory, -1.0, memory.getOriginalText()));
                        seenIds.add(memory.getId());
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "searchFts FTS4 query also failed: " + e.getMessage());
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        // 3. Robust substring LIKE query on memories table to guarantee case-insensitive prefix/substring
        // matching (e.g. searching "air" matches "Airpods" even if FTS token boundaries differ)
        if (candidates.size() < limit) {
            String[] rawWords = ftsQuery.trim().split("[^\\p{L}\\p{N}_-]+");
            for (String word : rawWords) {
                String token = word.trim();
                if (token.length() >= 2 && candidates.size() < limit) {
                    String likePattern = "%" + token + "%";
                    String sqlLike = "SELECT " + DatabaseConstants.Memories.COL_ID + ", "
                            + DatabaseConstants.Memories.COL_TYPE + ", "
                            + DatabaseConstants.Memories.COL_ORIGINAL_TEXT + ", "
                            + DatabaseConstants.Memories.COL_TITLE + ", "
                            + DatabaseConstants.Memories.COL_CREATED_AT + ", "
                            + DatabaseConstants.Memories.COL_UPDATED_AT + ", "
                            + DatabaseConstants.Memories.COL_SOURCE_TYPE + ", "
                            + DatabaseConstants.Memories.COL_SOURCE_ID + ", "
                            + DatabaseConstants.Memories.COL_STATUS + ", "
                            + DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT + " "
                            + "FROM " + DatabaseConstants.Memories.TABLE + " "
                            + "WHERE " + DatabaseConstants.Memories.COL_STATUS + " != '" + MemoryStatus.DELETED.name() + "' "
                            + "  AND (" + DatabaseConstants.Memories.COL_TITLE + " LIKE ? OR "
                            + DatabaseConstants.Memories.COL_ORIGINAL_TEXT + " LIKE ?) "
                            + "LIMIT ?";
                    try {
                        cursor = db.rawQuery(sqlLike, new String[]{likePattern, likePattern, String.valueOf(limit - candidates.size())});
                        while (cursor.moveToNext()) {
                            Memory memory = cursorToMemory(cursor);
                            if (!seenIds.contains(memory.getId())) {
                                candidates.add(new FtsCandidate(memory, -1.0, memory.getOriginalText()));
                                seenIds.add(memory.getId());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "searchBySubstring LIKE query failed: " + token, e);
                    } finally {
                        if (cursor != null) cursor.close();
                    }
                }
            }
        }

        return candidates;
    }

    @Override
    public List<Memory> searchByMetadata(MemoryType type, int limit) {
        List<Memory> results = new ArrayList<>();
        if (limit <= 0) return results;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection;
        String[] selectionArgs;

        if (type != null) {
            selection = DatabaseConstants.Memories.COL_STATUS + " != ? AND "
                    + DatabaseConstants.Memories.COL_TYPE + " = ?";
            selectionArgs = new String[]{MemoryStatus.DELETED.name(), type.name()};
        } else {
            selection = DatabaseConstants.Memories.COL_STATUS + " != ?";
            selectionArgs = new String[]{MemoryStatus.DELETED.name()};
        }

        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseConstants.Memories.TABLE,
                    /* columns= */ null,
                    selection,
                    selectionArgs,
                    /* groupBy= */ null,
                    /* having= */ null,
                    DatabaseConstants.Memories.COL_CREATED_AT + " DESC",
                    String.valueOf(limit)
            );

            while (cursor.moveToNext()) {
                results.add(cursorToMemory(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "searchByMetadata failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    @Override
    public List<Memory> getAllActiveMemories(int limit) {
        List<Memory> results = new ArrayList<>();
        if (limit <= 0) return results;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseConstants.Memories.TABLE,
                    /* columns= */ null,
                    DatabaseConstants.Memories.COL_STATUS + " = ?",
                    new String[]{MemoryStatus.ACTIVE.name()},
                    /* groupBy= */ null,
                    /* having= */ null,
                    DatabaseConstants.Memories.COL_UPDATED_AT + " DESC",
                    String.valueOf(limit)
            );

            while (cursor.moveToNext()) {
                results.add(cursorToMemory(cursor));
            }
        } catch (Exception e) {
            Log.e(TAG, "getAllActiveMemories failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return results;
    }

    private Memory cursorToMemory(Cursor cursor) {
        String id           = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_ID));
        String typeName     = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_TYPE));
        String origText     = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_ORIGINAL_TEXT));
        String title        = cursor.isNull(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_TITLE))
                ? null : cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_TITLE));
        long createdAt      = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_CREATED_AT));
        long updatedAt      = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_UPDATED_AT));
        String srcTypeName  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_SOURCE_TYPE));
        String sourceId     = cursor.isNull(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_SOURCE_ID))
                ? null : cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_SOURCE_ID));
        String statusName   = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_STATUS));
        Long lastConfirmedAt = cursor.isNull(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT))
                ? null : cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseConstants.Memories.COL_LAST_CONFIRMED_AT));

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
}
