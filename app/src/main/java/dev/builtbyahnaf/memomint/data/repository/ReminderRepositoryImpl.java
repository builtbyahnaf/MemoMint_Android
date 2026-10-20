package dev.builtbyahnaf.memomint.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.builtbyahnaf.memomint.data.database.DatabaseConstants;
import dev.builtbyahnaf.memomint.data.database.DatabaseConstants.Reminders;
import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.ReminderStatus;

/**
 * SQLite-backed implementation of {@link ReminderRepository}.
 *
 * <h2>Purpose & Architectural Invariants</h2>
 * <ul>
 *   <li>Direct SQLite interaction using column constants from {@link DatabaseConstants.Reminders}.</li>
 *   <li>Zero ORM overhead, deterministic SQL statements, and safe resource closure.</li>
 *   <li>Ensures foreign-key cascade awareness when reminders are linked to memories.</li>
 * </ul>
 */
public class ReminderRepositoryImpl implements ReminderRepository {

    private final MemoMintDatabase dbHelper;

    public ReminderRepositoryImpl(MemoMintDatabase dbHelper) {
        this.dbHelper = Objects.requireNonNull(dbHelper, "dbHelper cannot be null");
    }

    @Override
    public void save(Reminder reminder) {
        if (reminder == null || reminder.getId() == null) {
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toContentValues(reminder);

        db.insertWithOnConflict(
                Reminders.TABLE,
                null,
                cv,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    @Override
    public Reminder findById(String id) {
        if (id == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = Reminders.COL_ID + " = ?";
        String[] selectionArgs = new String[]{id};

        try (Cursor cursor = db.query(Reminders.TABLE, null, selection, selectionArgs, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public Reminder findByMemoryId(String memoryId) {
        if (memoryId == null) return null;

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = Reminders.COL_MEMORY_ID + " = ?";
        String[] selectionArgs = new String[]{memoryId};
        String orderBy = Reminders.COL_SCHEDULED_AT + " ASC";

        try (Cursor cursor = db.query(Reminders.TABLE, null, selection, selectionArgs, null, null, orderBy, "1")) {
            if (cursor != null && cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
        }
        return null;
    }

    @Override
    public List<Reminder> findAllActive() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = Reminders.COL_STATUS + " IN (?, ?)";
        String[] selectionArgs = new String[]{ReminderStatus.ACTIVE.name(), ReminderStatus.SNOOZED.name()};
        String orderBy = Reminders.COL_SCHEDULED_AT + " ASC";

        List<Reminder> results = new ArrayList<>();
        try (Cursor cursor = db.query(Reminders.TABLE, null, selection, selectionArgs, null, null, orderBy)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    results.add(fromCursor(cursor));
                }
            }
        }
        return results;
    }

    @Override
    public List<Reminder> findPendingDue(long maxScheduledAt) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String selection = Reminders.COL_STATUS + " IN (?, ?) AND " + Reminders.COL_SCHEDULED_AT + " <= ?";
        String[] selectionArgs = new String[]{
                ReminderStatus.ACTIVE.name(),
                ReminderStatus.SNOOZED.name(),
                String.valueOf(maxScheduledAt)
        };
        String orderBy = Reminders.COL_SCHEDULED_AT + " ASC";

        List<Reminder> results = new ArrayList<>();
        try (Cursor cursor = db.query(Reminders.TABLE, null, selection, selectionArgs, null, null, orderBy)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    results.add(fromCursor(cursor));
                }
            }
        }
        return results;
    }

    @Override
    public void updateStatus(String id, ReminderStatus status, Long completedAt) {
        if (id == null || status == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Reminders.COL_STATUS, status.name());
        if (completedAt != null) {
            cv.put(Reminders.COL_COMPLETED_AT, completedAt);
        } else {
            cv.putNull(Reminders.COL_COMPLETED_AT);
        }

        db.update(Reminders.TABLE, cv, Reminders.COL_ID + " = ?", new String[]{id});
    }

    @Override
    public void updateScheduledAt(String id, long scheduledAt, Long lastScheduledAt) {
        if (id == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Reminders.COL_SCHEDULED_AT, scheduledAt);
        if (lastScheduledAt != null) {
            cv.put(Reminders.COL_LAST_SCHEDULED_AT, lastScheduledAt);
        } else {
            cv.putNull(Reminders.COL_LAST_SCHEDULED_AT);
        }

        db.update(Reminders.TABLE, cv, Reminders.COL_ID + " = ?", new String[]{id});
    }

    @Override
    public void delete(String id) {
        if (id == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Reminders.TABLE, Reminders.COL_ID + " = ?", new String[]{id});
    }

    @Override
    public void deleteByMemoryId(String memoryId) {
        if (memoryId == null) return;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Reminders.TABLE, Reminders.COL_MEMORY_ID + " = ?", new String[]{memoryId});
    }

    // -------------------------------------------------------------------------
    // Mapping Helpers
    // -------------------------------------------------------------------------

    private ContentValues toContentValues(Reminder reminder) {
        ContentValues cv = new ContentValues();
        cv.put(Reminders.COL_ID, reminder.getId());
        cv.put(Reminders.COL_MEMORY_ID, reminder.getMemoryId());
        cv.put(Reminders.COL_TITLE, reminder.getTitle());
        cv.put(Reminders.COL_SCHEDULED_AT, reminder.getScheduledAt());
        cv.put(Reminders.COL_TIME_ZONE_ID, reminder.getTimeZoneId());
        cv.put(Reminders.COL_STATUS, reminder.getStatus() != null ? reminder.getStatus().name() : ReminderStatus.ACTIVE.name());
        cv.put(Reminders.COL_CREATED_AT, reminder.getCreatedAt());

        if (reminder.getCompletedAt() != null) {
            cv.put(Reminders.COL_COMPLETED_AT, reminder.getCompletedAt());
        } else {
            cv.putNull(Reminders.COL_COMPLETED_AT);
        }

        if (reminder.getLastScheduledAt() != null) {
            cv.put(Reminders.COL_LAST_SCHEDULED_AT, reminder.getLastScheduledAt());
        } else {
            cv.putNull(Reminders.COL_LAST_SCHEDULED_AT);
        }

        cv.put(Reminders.COL_REPEAT_INTERVAL, reminder.getRepeatInterval());

        return cv;
    }

    private Reminder fromCursor(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(Reminders.COL_ID));
        String memoryId = cursor.getString(cursor.getColumnIndexOrThrow(Reminders.COL_MEMORY_ID));
        String title = cursor.getString(cursor.getColumnIndexOrThrow(Reminders.COL_TITLE));
        long scheduledAt = cursor.getLong(cursor.getColumnIndexOrThrow(Reminders.COL_SCHEDULED_AT));
        String timeZoneId = cursor.getString(cursor.getColumnIndexOrThrow(Reminders.COL_TIME_ZONE_ID));
        String statusStr = cursor.getString(cursor.getColumnIndexOrThrow(Reminders.COL_STATUS));
        long createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(Reminders.COL_CREATED_AT));

        Long completedAt = null;
        int completedIdx = cursor.getColumnIndexOrThrow(Reminders.COL_COMPLETED_AT);
        if (!cursor.isNull(completedIdx)) {
            completedAt = cursor.getLong(completedIdx);
        }

        Long lastScheduledAt = null;
        int lastSchedIdx = cursor.getColumnIndexOrThrow(Reminders.COL_LAST_SCHEDULED_AT);
        if (!cursor.isNull(lastSchedIdx)) {
            lastScheduledAt = cursor.getLong(lastSchedIdx);
        }

        String repeatInterval = "NONE";
        int repeatIdx = cursor.getColumnIndex(Reminders.COL_REPEAT_INTERVAL);
        if (repeatIdx != -1 && !cursor.isNull(repeatIdx)) {
            repeatInterval = cursor.getString(repeatIdx);
        }

        ReminderStatus status = ReminderStatus.ACTIVE;
        if (statusStr != null) {
            try {
                status = ReminderStatus.valueOf(statusStr);
            } catch (IllegalArgumentException ignored) {}
        }

        return new Reminder(
                id,
                memoryId,
                title,
                scheduledAt,
                timeZoneId,
                status,
                createdAt,
                completedAt,
                lastScheduledAt,
                repeatInterval
        );
    }
}
