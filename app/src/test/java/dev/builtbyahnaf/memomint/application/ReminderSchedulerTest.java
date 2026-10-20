package dev.builtbyahnaf.memomint.application;

import org.junit.Test;

import java.util.Calendar;

import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying recurrence calculation in {@link ReminderScheduler}.
 */
public class ReminderSchedulerTest {

    @Test
    public void computeNextOccurrence_none_returnsOriginalTimestamp() {
        long timestamp = 1792490400000L;
        long next = ReminderScheduler.computeNextOccurrence(timestamp, RepeatInterval.NONE.name());
        assertEquals("NONE should return identical timestamp", timestamp, next);
    }

    @Test
    public void computeNextOccurrence_daily_advancesToFuture() {
        long now = System.currentTimeMillis();
        // A timestamp in the past (e.g. yesterday)
        long yesterday = now - (24 * 60 * 60 * 1000L);

        long next = ReminderScheduler.computeNextOccurrence(yesterday, RepeatInterval.DAILY.name());
        assertTrue("Next occurrence must be in the future", next > now);

        // Verify wall clock hour and minute are preserved
        Calendar calOld = Calendar.getInstance();
        calOld.setTimeInMillis(yesterday);

        Calendar calNew = Calendar.getInstance();
        calNew.setTimeInMillis(next);

        assertEquals("Hour should be preserved", calOld.get(Calendar.HOUR_OF_DAY), calNew.get(Calendar.HOUR_OF_DAY));
        assertEquals("Minute should be preserved", calOld.get(Calendar.MINUTE), calNew.get(Calendar.MINUTE));
    }

    @Test
    public void computeNextOccurrence_weekly_advancesToFuture() {
        long now = System.currentTimeMillis();
        long twoWeeksAgo = now - (14 * 24 * 60 * 60 * 1000L);

        long next = ReminderScheduler.computeNextOccurrence(twoWeeksAgo, RepeatInterval.WEEKLY.name());
        assertTrue("Next weekly occurrence must be in the future", next > now);

        Calendar calOld = Calendar.getInstance();
        calOld.setTimeInMillis(twoWeeksAgo);

        Calendar calNew = Calendar.getInstance();
        calNew.setTimeInMillis(next);

        assertEquals("Day of week must match", calOld.get(Calendar.DAY_OF_WEEK), calNew.get(Calendar.DAY_OF_WEEK));
        assertEquals("Hour must match", calOld.get(Calendar.HOUR_OF_DAY), calNew.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void computeNextOccurrence_monthly_advancesToFuture() {
        long now = System.currentTimeMillis();
        long twoMonthsAgo = now - (60L * 24 * 60 * 60 * 1000L);

        long next = ReminderScheduler.computeNextOccurrence(twoMonthsAgo, RepeatInterval.MONTHLY.name());
        assertTrue("Next monthly occurrence must be in the future", next > now);

        Calendar calOld = Calendar.getInstance();
        calOld.setTimeInMillis(twoMonthsAgo);

        Calendar calNew = Calendar.getInstance();
        calNew.setTimeInMillis(next);

        assertEquals("Hour must match", calOld.get(Calendar.HOUR_OF_DAY), calNew.get(Calendar.HOUR_OF_DAY));
        assertEquals("Minute must match", calOld.get(Calendar.MINUTE), calNew.get(Calendar.MINUTE));
    }
}
