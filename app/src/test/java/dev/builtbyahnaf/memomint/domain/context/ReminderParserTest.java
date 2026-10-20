package dev.builtbyahnaf.memomint.domain.context;

import org.junit.Test;

import java.util.Calendar;

import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests verifying deterministic, on-device intelligence parsing for reminders.
 */
public class ReminderParserTest {

    // Fixed baseline epoch: Tuesday, October 20, 2026, 10:00:00 AM UTC
    private static final long BASELINE_EPOCH = 1792490400000L;

    @Test
    public void parse_detectsTomorrowWithTimeAndStripsTitle() {
        String input = "Remind me to buy groceries tomorrow at 5pm";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect reminder intent", result.hasReminderIntent());
        assertEquals("Action title should be stripped of prefix and time", "Buy groceries", result.getReminderTitle());
        assertEquals("Repeat interval should be NONE", RepeatInterval.NONE.name(), result.getRepeatInterval());

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(result.getTargetTimestamp());
        assertEquals("Hour should be 17 (5 PM)", 17, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals("Minute should be 0", 0, cal.get(Calendar.MINUTE));
    }

    @Test
    public void parse_detectsDailyRepetition() {
        String input = "Remember to take vitamin C every day at 8:00 AM";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect reminder intent", result.hasReminderIntent());
        assertEquals("Title should be cleaned", "Take vitamin C", result.getReminderTitle());
        assertEquals("Should detect DAILY recurrence", RepeatInterval.DAILY.name(), result.getRepeatInterval());

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(result.getTargetTimestamp());
        assertEquals("Hour should be 8", 8, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals("Minute should be 0", 0, cal.get(Calendar.MINUTE));
    }

    @Test
    public void parse_detectsWeeklyRepetition() {
        String input = "Water the garden every week at 9am";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect reminder intent", result.hasReminderIntent());
        assertEquals("Title should be cleaned", "Water the garden", result.getReminderTitle());
        assertEquals("Should detect WEEKLY recurrence", RepeatInterval.WEEKLY.name(), result.getRepeatInterval());

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(result.getTargetTimestamp());
        assertEquals("Hour should be 9", 9, cal.get(Calendar.HOUR_OF_DAY));
    }

    @Test
    public void parse_detectsMonthlyRepetition() {
        String input = "Pay electricity bill every month at 10am";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect reminder intent", result.hasReminderIntent());
        assertEquals("Title should be cleaned", "Pay electricity bill", result.getReminderTitle());
        assertEquals("Should detect MONTHLY recurrence", RepeatInterval.MONTHLY.name(), result.getRepeatInterval());
    }

    @Test
    public void parse_detectsRelativeHourOffset() {
        String input = "Check the slow cooker in 2 hours";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect offset", result.hasReminderIntent());
        assertEquals("Title should be cleaned", "Check the slow cooker", result.getReminderTitle());

        long diffHours = (result.getTargetTimestamp() - BASELINE_EPOCH) / (1000 * 60 * 60);
        assertEquals("Should be exactly 2 hours later", 2, diffHours);
    }

    @Test
    public void parse_detectsCalendarMonthAndDay() {
        String input = "Dentist appointment on October 25 at 3:30 pm";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertTrue("Should detect appointment intent", result.hasReminderIntent());
        assertEquals("Title should be cleaned", "Dentist appointment", result.getReminderTitle());

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(result.getTargetTimestamp());
        assertEquals("Month should be October", Calendar.OCTOBER, cal.get(Calendar.MONTH));
        assertEquals("Day should be 25", 25, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals("Hour should be 15 (3 PM)", 15, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals("Minute should be 30", 30, cal.get(Calendar.MINUTE));
    }

    @Test
    public void parse_returnsNoIntentForOrdinaryNotes() {
        String input = "My spare earphones are in the second drawer";
        ParsedReminder result = ReminderParser.parse(input, BASELINE_EPOCH);

        assertFalse("Ordinary note without dates should not trigger reminder", result.hasReminderIntent());
    }
}
