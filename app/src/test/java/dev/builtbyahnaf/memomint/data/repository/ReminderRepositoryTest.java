package dev.builtbyahnaf.memomint.data.repository;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.UUID;

import dev.builtbyahnaf.memomint.data.database.MemoMintDatabase;
import dev.builtbyahnaf.memomint.domain.model.Memory;
import dev.builtbyahnaf.memomint.domain.model.MemoryStatus;
import dev.builtbyahnaf.memomint.domain.model.MemoryType;
import dev.builtbyahnaf.memomint.domain.model.Reminder;
import dev.builtbyahnaf.memomint.domain.model.ReminderStatus;
import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;
import dev.builtbyahnaf.memomint.domain.model.SourceType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit and integration tests for {@link ReminderRepositoryImpl}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ReminderRepositoryTest {

    private MemoMintDatabase dbHelper;
    private ReminderRepository reminderRepo;
    private MemoryRepository memoryRepo;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        dbHelper = new MemoMintDatabase(context, null); // in-memory
        reminderRepo = new ReminderRepositoryImpl(dbHelper);
        memoryRepo = new MemoryRepositoryImpl(dbHelper);
    }

    @After
    public void tearDown() {
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    @Test
    public void saveAndFindById_persistsReminderAndRepeatInterval() {
        String reminderId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long scheduledTime = now + 86400000L;

        Reminder reminder = new Reminder(
                reminderId,
                null,
                "Buy groceries",
                scheduledTime,
                "UTC",
                ReminderStatus.ACTIVE,
                now,
                null,
                null,
                RepeatInterval.DAILY.name()
        );

        reminderRepo.save(reminder);

        Reminder retrieved = reminderRepo.findById(reminderId);
        assertNotNull(retrieved);
        assertEquals(reminderId, retrieved.getId());
        assertEquals("Buy groceries", retrieved.getTitle());
        assertEquals(scheduledTime, retrieved.getScheduledAt());
        assertEquals(ReminderStatus.ACTIVE, retrieved.getStatus());
        assertEquals(RepeatInterval.DAILY.name(), retrieved.getRepeatInterval());
        assertTrue(retrieved.isRepeating());
    }

    @Test
    public void findByMemoryId_returnsLinkedReminder() {
        String memoryId = UUID.randomUUID().toString();
        Memory memory = new Memory(
                memoryId,
                MemoryType.NOTE,
                "Meeting notes",
                "Meeting",
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                SourceType.TYPED,
                null,
                MemoryStatus.ACTIVE,
                null
        );
        memoryRepo.save(memory);

        String reminderId = UUID.randomUUID().toString();
        Reminder reminder = new Reminder(
                reminderId,
                memoryId,
                "Follow up meeting",
                System.currentTimeMillis() + 3600000L,
                "UTC",
                ReminderStatus.ACTIVE,
                System.currentTimeMillis(),
                null,
                null,
                RepeatInterval.WEEKLY.name()
        );
        reminderRepo.save(reminder);

        Reminder found = reminderRepo.findByMemoryId(memoryId);
        assertNotNull(found);
        assertEquals(reminderId, found.getId());
        assertEquals(memoryId, found.getMemoryId());
        assertEquals(RepeatInterval.WEEKLY.name(), found.getRepeatInterval());
    }

    @Test
    public void findPendingDue_returnsOnlyActiveDueReminders() {
        long now = System.currentTimeMillis();

        Reminder pastDue = new Reminder(
                UUID.randomUUID().toString(), null, "Past due", now - 1000L, "UTC",
                ReminderStatus.ACTIVE, now - 10000L, null, null, "NONE"
        );
        Reminder future = new Reminder(
                UUID.randomUUID().toString(), null, "Future", now + 100000L, "UTC",
                ReminderStatus.ACTIVE, now, null, null, "NONE"
        );
        Reminder completed = new Reminder(
                UUID.randomUUID().toString(), null, "Completed", now - 1000L, "UTC",
                ReminderStatus.COMPLETED, now - 10000L, now, null, "NONE"
        );

        reminderRepo.save(pastDue);
        reminderRepo.save(future);
        reminderRepo.save(completed);

        List<Reminder> due = reminderRepo.findPendingDue(now);
        assertEquals(1, due.size());
        assertEquals(pastDue.getId(), due.get(0).getId());
    }

    @Test
    public void updateStatus_marksCompletedCorrectly() {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Reminder reminder = new Reminder(
                id, null, "Water plants", now + 3600000L, "UTC",
                ReminderStatus.ACTIVE, now, null, null, "NONE"
        );
        reminderRepo.save(reminder);

        reminderRepo.updateStatus(id, ReminderStatus.COMPLETED, now + 1000L);

        Reminder updated = reminderRepo.findById(id);
        assertNotNull(updated);
        assertEquals(ReminderStatus.COMPLETED, updated.getStatus());
        assertEquals(Long.valueOf(now + 1000L), updated.getCompletedAt());
    }

    @Test
    public void delete_removesRow() {
        String id = UUID.randomUUID().toString();
        Reminder reminder = new Reminder(
                id, null, "Temporary task", System.currentTimeMillis() + 10000L, "UTC",
                ReminderStatus.ACTIVE, System.currentTimeMillis(), null, null, "NONE"
        );
        reminderRepo.save(reminder);
        assertNotNull(reminderRepo.findById(id));

        reminderRepo.delete(id);
        assertNull(reminderRepo.findById(id));
    }
}
