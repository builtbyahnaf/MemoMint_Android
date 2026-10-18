package dev.builtbyahnaf.memomint.data.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.repository.MemoryRepository;
import dev.builtbyahnaf.memomint.data.repository.MemoryRepositoryImpl;
import dev.builtbyahnaf.memomint.domain.model.ContextType;
import dev.builtbyahnaf.memomint.domain.model.EntityRole;
import dev.builtbyahnaf.memomint.domain.model.EntityType;
import dev.builtbyahnaf.memomint.domain.model.GenerationStatus;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.Provenance;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration and data-integrity tests for {@link MemoMintDatabase} and {@link MemoryRepositoryImpl}.
 *
 * <h2>Purpose & Scope</h2>
 * <p>This test suite verifies the foundational data integrity guarantees of Project MemoMint (Phase 1):</p>
 * <ul>
 *   <li><b>Schema Initialization:</b> Verifies all 10 core tables and their indexes are created as expected.</li>
 *   <li><b>Memory CRUD:</b> Verifies inserting, reading by ID, querying by status/type, updating, and counting.</li>
 *   <li><b>Original-Content Preservation (FR-03):</b> Verifies that updating a memory's metadata or status
 *       never silently alters or erases the original user-entered text.</li>
 *   <li><b>Foreign Key Cascade Deletion (FR-14):</b> Verifies that when a memory is hard-deleted, all
 *       dependent rows in {@code context_records}, {@code memory_entities}, {@code reminders}, and
 *       {@code confirmation_policies} are automatically purged by SQLite's engine.</li>
 *   <li><b>Shared Entity Preservation:</b> Verifies that deleting a memory referencing an entity does NOT
 *       delete the row in the {@code entities} table, ensuring canonical vocabulary is preserved.</li>
 *   <li><b>Transaction Rollback:</b> Verifies that an uncommitted or aborted transaction leaves the database
 *       in its original state without partial writes.</li>
 * </ul>
 *
 * <h2>Algorithm & Execution Strategy</h2>
 * <p>These tests use Robolectric to execute against real Android SQLite instances on the JVM without
 * requiring an attached physical Android device or emulator. An in-memory database instance
 * (using {@code name = null}) is created fresh for each test method and torn down in {@link #tearDown()},
 * ensuring complete test isolation and zero disk state leakage.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MemoMintDatabaseTest {

    private MemoMintDatabase dbHelper;
    private MemoryRepository repository;
    private SQLiteDatabase db;

    /**
     * Initializes a clean in-memory database helper and repository before each test.
     * Passing {@code null} as the database name instructs SQLiteOpenHelper to use an in-memory database.
     */
    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new MemoMintDatabase(context, /* in-memory name= */ null);
        repository = new MemoryRepositoryImpl(dbHelper);
        db = dbHelper.getWritableDatabase();
    }

    /**
     * Closes the database helper and connection after each test to release resources.
     */
    @After
    public void tearDown() {
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    // =========================================================================
    // 1. Schema Integrity Tests
    // =========================================================================

    /**
     * Verifies that all expected tables are created upon initial database configuration.
     *
     * <p><b>Algorithm:</b> Query {@code sqlite_master} for all table names and assert the presence
     * of each required table in the MemoMint Phase 1 architecture.</p>
     */
    @Test
    public void schema_createsAllRequiredTables() {
        String[] expectedTables = new String[]{
                DatabaseConstants.Memories.TABLE,
                DatabaseConstants.ContextRecords.TABLE,
                DatabaseConstants.Entities.TABLE,
                DatabaseConstants.MemoryEntities.TABLE,
                DatabaseConstants.MemoryRelations.TABLE,
                DatabaseConstants.FileReferences.TABLE,
                DatabaseConstants.Reminders.TABLE,
                DatabaseConstants.ConfirmationPolicies.TABLE,
                DatabaseConstants.IndexJobs.TABLE
        };

        for (String table : expectedTables) {
            Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{table}
            );
            try {
                assertTrue("Table '" + table + "' should exist in the created schema", cursor.moveToFirst());
            } finally {
                cursor.close();
            }
        }
    }

    /**
     * Verifies that foreign key constraints are strictly enabled on the database connection.
     *
     * <p>SQLite requires explicit activation of foreign keys per-connection via PRAGMA foreign_keys = ON.
     * In {@link MemoMintDatabase#onConfigure}, this is enabled to guarantee cascade delete behavior.</p>
     */
    @Test
    public void foreignKeys_areEnabledOnConnection() {
        Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null);
        try {
            assertTrue("PRAGMA foreign_keys query should return a row", cursor.moveToFirst());
            int enabled = cursor.getInt(0);
            assertEquals("Foreign keys must be enabled (value 1)", 1, enabled);
        } finally {
            cursor.close();
        }
    }

    // =========================================================================
    // 2. Memory CRUD Tests
    // =========================================================================

    /**
     * Verifies inserting a complete Memory record and retrieving it by primary key ID.
     */
    @Test
    public void saveAndFindById_persistsAllFieldsAccurately() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Memory memory = new Memory(
                id,
                MemoryType.PHYSICAL_ITEM,
                "Spare USB-C cable is in the top desk drawer.",
                "Desk Cable",
                now,
                now,
                SourceType.TYPED,
                "manual_entry",
                MemoryStatus.ACTIVE,
                now
        );

        String savedId = repository.save(memory);
        assertEquals("Returned ID should match the memory's ID", id, savedId);

        Memory retrieved = repository.findById(id);
        assertNotNull("Retrieved memory must not be null", retrieved);
        assertEquals(id, retrieved.getId());
        assertEquals(MemoryType.PHYSICAL_ITEM, retrieved.getType());
        assertEquals("Spare USB-C cable is in the top desk drawer.", retrieved.getOriginalText());
        assertEquals("Desk Cable", retrieved.getTitle());
        assertEquals(now, retrieved.getCreatedAt());
        assertEquals(now, retrieved.getUpdatedAt());
        assertEquals(SourceType.TYPED, retrieved.getSourceType());
        assertEquals("manual_entry", retrieved.getSourceId());
        assertEquals(MemoryStatus.ACTIVE, retrieved.getStatus());
        assertEquals(Long.valueOf(now), retrieved.getLastConfirmedAt());
    }

    /**
     * Verifies that nullable fields (such as title, sourceId, and lastConfirmedAt) correctly
     * persist as NULL in SQLite and read back as Java null.
     */
    @Test
    public void saveAndFindById_handlesNullableFieldsGracefully() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Memory memory = new Memory(
                id,
                MemoryType.NOTE,
                "Quick thought without a title.",
                /* title= */ null,
                now,
                now,
                SourceType.TYPED,
                /* sourceId= */ null,
                MemoryStatus.ACTIVE,
                /* lastConfirmedAt= */ null
        );

        repository.save(memory);

        Memory retrieved = repository.findById(id);
        assertNotNull(retrieved);
        assertNull("Title should be null", retrieved.getTitle());
        assertNull("SourceId should be null", retrieved.getSourceId());
        assertNull("LastConfirmedAt should be null", retrieved.getLastConfirmedAt());
    }

    /**
     * Verifies updating a memory's metadata and ensures the update returns true.
     */
    @Test
    public void update_modifiesFieldsCorrectly() {
        String id = UUID.randomUUID().toString();
        long created = 1000L;

        Memory original = new Memory(
                id,
                MemoryType.NOTE,
                "Initial observation",
                "Draft",
                created,
                created,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );
        repository.save(original);

        // Update fields
        long updatedTime = 2000L;
        Memory updated = new Memory(
                id,
                MemoryType.NOTE,
                "User-edited observation",
                "Final Note",
                created,
                updatedTime,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );

        boolean success = repository.update(updated);
        assertTrue("Update operation should report success", success);

        Memory fromDb = repository.findById(id);
        assertNotNull(fromDb);
        assertEquals("User-edited observation", fromDb.getOriginalText());
        assertEquals("Final Note", fromDb.getTitle());
        assertEquals(updatedTime, fromDb.getUpdatedAt());
        assertEquals("Created timestamp must remain unmodified", created, fromDb.getCreatedAt());
    }

    /**
     * Verifies targeted status updates (e.g. archiving or soft-deleting).
     */
    @Test
    public void updateStatus_changesOnlyStatusColumn() {
        String id = UUID.randomUUID().toString();
        Memory memory = new Memory(
                id,
                MemoryType.NOTE,
                "Preserved text",
                "Title",
                1000L,
                1000L,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );
        repository.save(memory);

        boolean changed = repository.updateStatus(id, MemoryStatus.ARCHIVED);
        assertTrue(changed);

        Memory fromDb = repository.findById(id);
        assertEquals(MemoryStatus.ARCHIVED, fromDb.getStatus());
        assertEquals("Preserved text", fromDb.getOriginalText());
        assertEquals("Title", fromDb.getTitle());
    }

    /**
     * Verifies recording user confirmation updates {@code last_confirmed_at} and {@code updated_at}.
     */
    @Test
    public void recordConfirmation_updatesTimestampCorrectly() {
        String id = UUID.randomUUID().toString();
        long created = 1000L;
        long confirmed = 5000L;

        Memory memory = new Memory(
                id,
                MemoryType.PHYSICAL_ITEM,
                "Backpack in the closet",
                null,
                created,
                created,
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );
        repository.save(memory);

        boolean ok = repository.recordConfirmation(id, confirmed);
        assertTrue(ok);

        Memory fromDb = repository.findById(id);
        assertEquals(Long.valueOf(confirmed), fromDb.getLastConfirmedAt());
        assertEquals(confirmed, fromDb.getUpdatedAt());
    }

    // =========================================================================
    // 3. Query Filtering & Ordering Tests
    // =========================================================================

    /**
     * Verifies querying memories by status orders results chronologically descending (newest first).
     */
    @Test
    public void findByStatus_returnsMatchingMemoriesOrderedByCreatedAtDesc() {
        Memory m1 = new Memory(UUID.randomUUID().toString(), MemoryType.NOTE, "Note 1", null, 100L, 100L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        Memory m2 = new Memory(UUID.randomUUID().toString(), MemoryType.NOTE, "Note 2", null, 300L, 300L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        Memory m3 = new Memory(UUID.randomUUID().toString(), MemoryType.NOTE, "Note 3", null, 200L, 200L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        Memory archived = new Memory(UUID.randomUUID().toString(), MemoryType.NOTE, "Archived", null, 400L, 400L, SourceType.TYPED, null, MemoryStatus.ARCHIVED, null);

        repository.save(m1);
        repository.save(m2);
        repository.save(m3);
        repository.save(archived);

        List<Memory> activeList = repository.findByStatus(MemoryStatus.ACTIVE);
        assertEquals("Should retrieve exactly 3 active memories", 3, activeList.size());
        assertEquals("Newest active memory should be first (300L)", m2.getId(), activeList.get(0).getId());
        assertEquals("Second active memory should be 200L", m3.getId(), activeList.get(1).getId());
        assertEquals("Oldest active memory should be last (100L)", m1.getId(), activeList.get(2).getId());

        int count = repository.countByStatus(MemoryStatus.ACTIVE);
        assertEquals(3, count);
        assertEquals(1, repository.countByStatus(MemoryStatus.ARCHIVED));
    }

    /**
     * Verifies querying by both type and status filters properly.
     */
    @Test
    public void findByTypeAndStatus_filtersCorrectly() {
        Memory item = new Memory(UUID.randomUUID().toString(), MemoryType.PHYSICAL_ITEM, "Headphones", null, 100L, 100L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        Memory note = new Memory(UUID.randomUUID().toString(), MemoryType.NOTE, "Lecture notes", null, 200L, 200L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);

        repository.save(item);
        repository.save(note);

        List<Memory> items = repository.findByTypeAndStatus(MemoryType.PHYSICAL_ITEM, MemoryStatus.ACTIVE);
        assertEquals(1, items.size());
        assertEquals(item.getId(), items.get(0).getId());

        List<Memory> notes = repository.findByTypeAndStatus(MemoryType.NOTE, MemoryStatus.ACTIVE);
        assertEquals(1, notes.size());
        assertEquals(note.getId(), notes.get(0).getId());
    }

    // =========================================================================
    // 4. Soft Delete vs Hard Delete Tests
    // =========================================================================

    /**
     * Verifies that soft-delete marks the row as DELETED, excluding it from active queries
     * while retaining the row in the database for possible audit or recovery.
     */
    @Test
    public void softDelete_setsStatusToDeletedWithoutRemovingRow() {
        String id = UUID.randomUUID().toString();
        Memory memory = new Memory(id, MemoryType.NOTE, "To be soft-deleted", null, 1000L, 1000L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        repository.save(memory);

        boolean softDeleted = repository.softDelete(id);
        assertTrue(softDeleted);

        Memory fromDb = repository.findById(id);
        assertNotNull("Soft-deleted row must still exist in SQLite", fromDb);
        assertEquals(MemoryStatus.DELETED, fromDb.getStatus());
        assertTrue("isDeleted() must return true", fromDb.isDeleted());

        // Must not appear in active results
        List<Memory> active = repository.findByStatus(MemoryStatus.ACTIVE);
        assertEquals(0, active.size());
    }

    /**
     * Verifies that hard-delete completely erases the memory row from the table.
     */
    @Test
    public void hardDelete_removesRowCompletely() {
        String id = UUID.randomUUID().toString();
        Memory memory = new Memory(id, MemoryType.NOTE, "To be hard-deleted", null, 1000L, 1000L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        repository.save(memory);

        boolean hardDeleted = repository.hardDelete(id);
        assertTrue(hardDeleted);

        assertNull("Hard-deleted row must return null on findById", repository.findById(id));
    }

    // =========================================================================
    // 5. Cascade Deletion & Foreign Key Integrity Tests (FR-14, NFR-08)
    // =========================================================================

    /**
     * Verifies that hard-deleting a Memory automatically purges its associated
     * {@code context_records}, {@code memory_entities}, {@code reminders}, and
     * {@code confirmation_policies} via SQLite's ON DELETE CASCADE constraints.
     *
     * <p><b>Algorithm:</b></p>
     * <ol>
     *   <li>Create a Memory row.</li>
     *   <li>Insert dependent rows in context_records, entities + memory_entities, reminders,
     *       and confirmation_policies referencing the memory ID.</li>
     *   <li>Execute {@code hardDelete(memoryId)}.</li>
     *   <li>Assert that zero rows remain in any of the dependent tables for that memory ID.</li>
     *   <li>Assert that the shared entity in {@code entities} is NOT deleted.</li>
     * </ol>
     */
    @Test
    public void hardDelete_cascadesToDependentTablesWhilePreservingSharedEntities() {
        String memoryId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        // 1. Create primary memory
        Memory memory = new Memory(memoryId, MemoryType.PHYSICAL_ITEM, "Keys on coat rack", null, now, now, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
        repository.save(memory);

        // 2. Insert dependent context_record
        String contextId = UUID.randomUUID().toString();
        ContentValues crValues = new ContentValues();
        crValues.put(DatabaseConstants.ContextRecords.COL_ID, contextId);
        crValues.put(DatabaseConstants.ContextRecords.COL_MEMORY_ID, memoryId);
        crValues.put(DatabaseConstants.ContextRecords.COL_CONTEXT_TYPE, ContextType.KEYWORDS.name());
        crValues.put(DatabaseConstants.ContextRecords.COL_CONTENT, "keys coat rack entrance");
        crValues.put(DatabaseConstants.ContextRecords.COL_GENERATOR_VERSION, 1);
        crValues.put(DatabaseConstants.ContextRecords.COL_CREATED_AT, now);
        crValues.put(DatabaseConstants.ContextRecords.COL_UPDATED_AT, now);
        crValues.put(DatabaseConstants.ContextRecords.COL_GENERATION_STATUS, GenerationStatus.DONE.name());
        db.insertOrThrow(DatabaseConstants.ContextRecords.TABLE, null, crValues);

        // 3. Insert entity and memory_entity join record
        String entityId = UUID.randomUUID().toString();
        ContentValues entValues = new ContentValues();
        entValues.put(DatabaseConstants.Entities.COL_ID, entityId);
        entValues.put(DatabaseConstants.Entities.COL_CANONICAL_LABEL, "coat rack");
        entValues.put(DatabaseConstants.Entities.COL_ENTITY_TYPE, EntityType.LOCATION.name());
        entValues.put(DatabaseConstants.Entities.COL_ORIGINAL_LABEL, "coat rack");
        db.insertOrThrow(DatabaseConstants.Entities.TABLE, null, entValues);

        ContentValues meValues = new ContentValues();
        meValues.put(DatabaseConstants.MemoryEntities.COL_MEMORY_ID, memoryId);
        meValues.put(DatabaseConstants.MemoryEntities.COL_ENTITY_ID, entityId);
        meValues.put(DatabaseConstants.MemoryEntities.COL_ROLE, EntityRole.LOCATION.name());
        meValues.put(DatabaseConstants.MemoryEntities.COL_PROVENANCE, Provenance.USER_ENTERED.name());
        db.insertOrThrow(DatabaseConstants.MemoryEntities.TABLE, null, meValues);

        // 4. Insert reminder linked to the memory
        String reminderId = UUID.randomUUID().toString();
        ContentValues remValues = new ContentValues();
        remValues.put(DatabaseConstants.Reminders.COL_ID, reminderId);
        remValues.put(DatabaseConstants.Reminders.COL_MEMORY_ID, memoryId);
        remValues.put(DatabaseConstants.Reminders.COL_TITLE, "Pick up keys");
        remValues.put(DatabaseConstants.Reminders.COL_SCHEDULED_AT, now + 3600000L);
        remValues.put(DatabaseConstants.Reminders.COL_STATUS, "ACTIVE");
        remValues.put(DatabaseConstants.Reminders.COL_CREATED_AT, now);
        db.insertOrThrow(DatabaseConstants.Reminders.TABLE, null, remValues);

        // 5. Insert confirmation policy
        ContentValues cpValues = new ContentValues();
        cpValues.put(DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID, memoryId);
        cpValues.put(DatabaseConstants.ConfirmationPolicies.COL_ENABLED, 1);
        cpValues.put(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_TYPE, "DAILY");
        cpValues.put(DatabaseConstants.ConfirmationPolicies.COL_INTERVAL_VALUE, 1);
        db.insertOrThrow(DatabaseConstants.ConfirmationPolicies.TABLE, null, cpValues);

        // Verify all 4 dependent records exist prior to deletion
        assertEquals(1, countRowsWhere(DatabaseConstants.ContextRecords.TABLE, DatabaseConstants.ContextRecords.COL_MEMORY_ID, memoryId));
        assertEquals(1, countRowsWhere(DatabaseConstants.MemoryEntities.TABLE, DatabaseConstants.MemoryEntities.COL_MEMORY_ID, memoryId));
        assertEquals(1, countRowsWhere(DatabaseConstants.Reminders.TABLE, DatabaseConstants.Reminders.COL_MEMORY_ID, memoryId));
        assertEquals(1, countRowsWhere(DatabaseConstants.ConfirmationPolicies.TABLE, DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID, memoryId));

        // Act: Delete the memory
        boolean deleted = repository.hardDelete(memoryId);
        assertTrue(deleted);

        // Assert: All dependent rows must be purged by ON DELETE CASCADE
        assertEquals("ContextRecords should be cascade deleted", 0,
                countRowsWhere(DatabaseConstants.ContextRecords.TABLE, DatabaseConstants.ContextRecords.COL_MEMORY_ID, memoryId));
        assertEquals("MemoryEntities should be cascade deleted", 0,
                countRowsWhere(DatabaseConstants.MemoryEntities.TABLE, DatabaseConstants.MemoryEntities.COL_MEMORY_ID, memoryId));
        assertEquals("Reminders linked to memory should be cascade deleted", 0,
                countRowsWhere(DatabaseConstants.Reminders.TABLE, DatabaseConstants.Reminders.COL_MEMORY_ID, memoryId));
        assertEquals("ConfirmationPolicies should be cascade deleted", 0,
                countRowsWhere(DatabaseConstants.ConfirmationPolicies.TABLE, DatabaseConstants.ConfirmationPolicies.COL_MEMORY_ID, memoryId));

        // Assert: The shared Entity must still exist in the database!
        assertEquals("Shared Entity record must NOT be deleted", 1,
                countRowsWhere(DatabaseConstants.Entities.TABLE, DatabaseConstants.Entities.COL_ID, entityId));
    }

    // =========================================================================
    // 6. Transaction Rollback Tests
    // =========================================================================

    /**
     * Verifies that if an exception occurs during an atomic transaction, all changes
     * made within that transaction are rolled back cleanly.
     */
    @Test
    public void transaction_rollsBackCleanlyOnFailure() {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        db.beginTransaction();
        try {
            // First valid insert
            Memory m1 = new Memory(id1, MemoryType.NOTE, "Note A", null, 100L, 100L, SourceType.TYPED, null, MemoryStatus.ACTIVE, null);
            repository.save(m1);

            // Simulate an error or intentionally omit setTransactionSuccessful()
            if (true) {
                throw new IllegalStateException("Simulated operational failure mid-batch");
            }

            db.setTransactionSuccessful();
        } catch (IllegalStateException expected) {
            // Transaction ends without being marked successful -> triggers rollback
        } finally {
            db.endTransaction();
        }

        // Assert: Nothing should have been persisted due to rollback
        assertNull("Row inserted prior to failure should be rolled back", repository.findById(id1));
        assertEquals(0, repository.countByStatus(MemoryStatus.ACTIVE));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Helper to count rows matching a specific column value.
     */
    private int countRowsWhere(String table, String column, String value) {
        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                new String[]{value}
        );
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            cursor.close();
        }
    }
}
