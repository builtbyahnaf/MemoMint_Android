package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.DatabaseConstants.FilesFts;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.FileSearchResult;

/**
 * SQLite-backed implementation of {@link FileSearchRepository}.
 *
 * <h2>Purpose & Architectural Invariants (Phase 4 Plan §6.3, §7)</h2>
 * <ul>
 *   <li><b>Full-Text Search:</b> Queries {@code files_fts} with sanitized prefix tokens.</li>
 *   <li><b>Resilient Fallback:</b> Automatically falls back to SQL {@code LIKE} queries if
 *       virtual tables or MATCH operators encounter engine-specific errors.</li>
 *   <li><b>Snippet Generation:</b> Generates concise contextual snippets around matching terms.</li>
 * </ul>
 */
public class FileSearchRepositoryImpl implements FileSearchRepository {

    private static final String TAG = "FileSearchRepo";

    private final MemoMintDatabase dbHelper;

    public FileSearchRepositoryImpl(MemoMintDatabase dbHelper) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper cannot be null");
    }

    @Override
    public void indexDocumentContent(String fileId, String title, String textContent) {
        if (fileId == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Remove previous entries for this file
            try {
                db.delete(
                        FilesFts.TABLE,
                        FilesFts.COL_FILE_ID + " = ?",
                        new String[]{fileId}
                );
            } catch (Exception e) {
                Log.w(TAG, "Failed deleting previous files_fts entry: " + e.getMessage());
            }

            // Insert new content
            ContentValues cv = new ContentValues();
            cv.put(FilesFts.COL_ID, UUID.randomUUID().toString());
            cv.put(FilesFts.COL_FILE_ID, fileId);
            cv.put(FilesFts.COL_TITLE, title != null ? title : "");
            cv.put(FilesFts.COL_CONTENT, textContent != null ? textContent : "");

            db.insert(FilesFts.TABLE, null, cv);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error indexing document in files_fts: " + e.getMessage(), e);
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void removeDocumentContent(String fileId) {
        if (fileId == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.delete(
                    FilesFts.TABLE,
                    FilesFts.COL_FILE_ID + " = ?",
                    new String[]{fileId}
            );
        } catch (Exception e) {
            Log.w(TAG, "Error removing document content from files_fts: " + e.getMessage());
        }
    }

    @Override
    public List<FileSearchResult> searchFiles(String query, int limit) {
        List<FileSearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty() || limit <= 0) {
            return results;
        }

        List<String> queryTokens = extractTokens(query);
        if (queryTokens.isEmpty()) {
            return results;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Set<String> seenFileIds = new HashSet<>();

        // 1. Try FTS MATCH query
        String ftsMatchExpr = buildFtsMatchExpression(queryTokens);
        boolean ftsSucceeded = false;

        if (!ftsMatchExpr.isEmpty()) {
            String sqlFts = "SELECT " + FilesFts.COL_FILE_ID + ", "
                    + FilesFts.COL_TITLE + ", "
                    + FilesFts.COL_CONTENT
                    + " FROM " + FilesFts.TABLE
                    + " WHERE " + FilesFts.TABLE + " MATCH ?"
                    + " LIMIT " + limit;

            try (Cursor cursor = db.rawQuery(sqlFts, new String[]{ftsMatchExpr})) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String fileId = cursor.getString(0);
                        if (seenFileIds.add(fileId)) {
                            String title = cursor.getString(1);
                            String content = cursor.getString(2);
                            results.add(buildSearchResult(fileId, title, content, queryTokens));
                        }
                    }
                    ftsSucceeded = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "FTS query failed on files_fts, falling back to LIKE: " + e.getMessage());
            }
        }

        // 2. Relational LIKE fallback if FTS returned nothing or threw
        if (!ftsSucceeded || results.size() < limit) {
            int remaining = limit - results.size();
            try {
                StringBuilder likeSql = new StringBuilder("SELECT "
                        + FilesFts.COL_FILE_ID + ", "
                        + FilesFts.COL_TITLE + ", "
                        + FilesFts.COL_CONTENT
                        + " FROM " + FilesFts.TABLE
                        + " WHERE ");

                List<String> likeArgs = new ArrayList<>();
                for (int i = 0; i < queryTokens.size(); i++) {
                    if (i > 0) likeSql.append(" OR ");
                    likeSql.append("(").append(FilesFts.COL_TITLE).append(" LIKE ? OR ")
                            .append(FilesFts.COL_CONTENT).append(" LIKE ?)");
                    String pattern = "%" + queryTokens.get(i) + "%";
                    likeArgs.add(pattern);
                    likeArgs.add(pattern);
                }
                likeSql.append(" LIMIT ").append(remaining);

                try (Cursor cursor = db.rawQuery(likeSql.toString(), likeArgs.toArray(new String[0]))) {
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String fileId = cursor.getString(0);
                            if (seenFileIds.add(fileId)) {
                                String title = cursor.getString(1);
                                String content = cursor.getString(2);
                                results.add(buildSearchResult(fileId, title, content, queryTokens));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "LIKE query failed on files_fts: " + e.getMessage());
            }
        }

        Collections.sort(results);
        return results;
    }

    private FileSearchResult buildSearchResult(
            String fileId,
            String title,
            String content,
            List<String> queryTokens
    ) {
        String safeTitle = title != null ? title : "";
        String safeContent = content != null ? content : "";
        String lowerTitle = safeTitle.toLowerCase(Locale.ROOT);
        String lowerContent = safeContent.toLowerCase(Locale.ROOT);

        List<String> matchedTerms = new ArrayList<>();
        int titleMatches = 0;
        int contentMatches = 0;
        int firstMatchPos = -1;

        for (String token : queryTokens) {
            String lowerToken = token.toLowerCase(Locale.ROOT);
            boolean matched = false;

            if (lowerTitle.contains(lowerToken)) {
                matched = true;
                titleMatches++;
                matchedTerms.add(token);
            }

            int pos = lowerContent.indexOf(lowerToken);
            if (pos != -1) {
                matched = true;
                contentMatches++;
                if (!matchedTerms.contains(token)) {
                    matchedTerms.add(token);
                }
                if (firstMatchPos == -1 || pos < firstMatchPos) {
                    firstMatchPos = pos;
                }
            }
        }

        // Generate contextual snippet
        String snippet;
        if (safeContent.isEmpty()) {
            snippet = safeTitle;
        } else if (firstMatchPos >= 0) {
            int start = Math.max(0, firstMatchPos - 60);
            int end = Math.min(safeContent.length(), firstMatchPos + 140);
            String prefix = (start > 0) ? "..." : "";
            String suffix = (end < safeContent.length()) ? "..." : "";
            snippet = prefix + safeContent.substring(start, end).replace('\n', ' ').trim() + suffix;
        } else {
            int end = Math.min(safeContent.length(), 160);
            snippet = safeContent.substring(0, end).replace('\n', ' ').trim()
                    + (safeContent.length() > 160 ? "..." : "");
        }

        // Calculate relevance score: [0.3, 1.0]
        double score = 0.35;
        if (titleMatches > 0) {
            score += 0.35 * Math.min(1.0, (double) titleMatches / queryTokens.size());
        }
        if (contentMatches > 0) {
            score += 0.30 * Math.min(1.0, (double) contentMatches / queryTokens.size());
        }
        score = Math.min(1.0, score);

        return new FileSearchResult(fileId, safeTitle, snippet, score, matchedTerms);
    }

    private static List<String> extractTokens(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null) return tokens;
        String[] parts = query.trim().split("[^\\p{L}\\p{N}_-]+");
        for (String part : parts) {
            String t = part.trim();
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static String buildFtsMatchExpression(List<String> tokens) {
        if (tokens.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(tokens.get(i).toLowerCase(Locale.ROOT)).append("*");
        }
        return sb.toString();
    }
}
