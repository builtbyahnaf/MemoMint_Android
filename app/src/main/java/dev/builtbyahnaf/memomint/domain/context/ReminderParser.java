package dev.builtbyahnaf.memomint.domain.context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;

/**
 * Intelligent on-device natural language parser extracting reminders, target timestamps,
 * repetition rules, and action titles from free-form text.
 *
 * <h2>Design & Token Efficiency</h2>
 * <ul>
 *   <li><b>100% On-Device & Zero Token Cost:</b> Executes deterministically via precompiled regular
 *       expressions, temporal heuristics, and calendar arithmetic without network calls or cloud AI tokens.</li>
 *   <li><b>Clean Action Extraction:</b> Strips out intention prefixes ("remind me to", "remember to")
 *       and temporal expressions, leaving a concise, human-readable notification title.</li>
 *   <li><b>Recurrence Intelligence:</b> Detects repeating intervals (daily, weekly, monthly) and associates
 *       them with the reminder schedule.</li>
 * </ul>
 */
public class ReminderParser {

    // Trigger phrases indicating reminder intent
    private static final Pattern INTENT_PATTERN = Pattern.compile(
            "\\b(?:remind\\s+me(?:\\s+to)?|remember\\s+to|don't\\s+forget\\s+to|need\\s+to|have\\s+to|schedule|appointment|meeting)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Repetition patterns
    private static final Pattern REPEAT_DAILY_PATTERN = Pattern.compile(
            "\\b(?:every\\s+day|daily|each\\s+day|everyday)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REPEAT_WEEKLY_PATTERN = Pattern.compile(
            "\\b(?:every\\s+week|weekly|each\\s+week|every\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday))\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REPEAT_MONTHLY_PATTERN = Pattern.compile(
            "\\b(?:every\\s+month|monthly|each\\s+month)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Relative offset patterns (e.g. "in 30 minutes", "in 2 hours", "in 3 days")
    private static final Pattern OFFSET_PATTERN = Pattern.compile(
            "\\bin\\s+(\\d+)\\s+(minute|min|hour|hr|day)s?\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Relative day patterns
    private static final Pattern DAY_AFTER_TOMORROW_PATTERN = Pattern.compile(
            "\\bday\\s+after\\s+tomorrow\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOMORROW_PATTERN = Pattern.compile(
            "\\btomorrow\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TODAY_PATTERN = Pattern.compile(
            "\\b(?:today|tonight)\\b", Pattern.CASE_INSENSITIVE
    );

    // Day of the week pattern (e.g. "next Friday", "on Monday")
    private static final Pattern DAY_OF_WEEK_PATTERN = Pattern.compile(
            "\\b(?:next\\s+|on\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Specific calendar date patterns (e.g. "Oct 25", "25th October", "10/25/2026")
    private static final Pattern MONTH_NAME_DATE_PATTERN = Pattern.compile(
            "\\b(?:on\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:\\s*,?\\s*(\\d{4}))?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DAY_MONTH_NAME_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)(?:\\s*,?\\s*(\\d{4}))?\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?\\b"
    );

    // Time of day patterns (e.g. "at 5:30 pm", "at 9am", "18:00", "at 5")
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_24H_PATTERN = Pattern.compile(
            "\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b"
    );
    private static final Pattern TIME_AT_SIMPLE_PATTERN = Pattern.compile(
            "\\bat\\s+(\\d{1,2})\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Qualitative time indicators
    private static final Pattern TIME_MORNING = Pattern.compile("\\b(?:in\\s+the\\s+morning|morning)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_AFTERNOON = Pattern.compile("\\b(?:in\\s+the\\s+afternoon|afternoon)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_EVENING = Pattern.compile("\\b(?:in\\s+the\\s+evening|evening)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_NIGHT = Pattern.compile("\\b(?:at\\s+night|tonight|night)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_NOON = Pattern.compile("\\b(?:at\\s+noon|noon)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Parses the memory text and extracts reminder parameters relative to {@code System.currentTimeMillis()}.
     *
     * @param rawText the unstructured user input
     * @return structured {@link ParsedReminder}
     */
    public static ParsedReminder parse(String rawText) {
        return parse(rawText, System.currentTimeMillis());
    }

    /**
     * Deterministic parse method accepting a baseline epoch timestamp.
     *
     * @param rawText        the unstructured user input
     * @param nowEpochMillis baseline time for relative offsets
     * @return structured {@link ParsedReminder}
     */
    public static ParsedReminder parse(String rawText, long nowEpochMillis) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ParsedReminder(false, "", nowEpochMillis, RepeatInterval.NONE.name(), "");
        }

        String text = rawText.trim();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowEpochMillis);

        boolean hasExplicitIntent = INTENT_PATTERN.matcher(text).find();
        boolean hasTemporalPattern = false;

        // 1. Detect Repeat Interval
        String repeatInterval = RepeatInterval.NONE.name();
        Matcher dailyMatcher = REPEAT_DAILY_PATTERN.matcher(text);
        if (dailyMatcher.find()) {
            repeatInterval = RepeatInterval.DAILY.name();
            hasTemporalPattern = true;
        } else {
            Matcher weeklyMatcher = REPEAT_WEEKLY_PATTERN.matcher(text);
            if (weeklyMatcher.find()) {
                repeatInterval = RepeatInterval.WEEKLY.name();
                hasTemporalPattern = true;
            } else {
                Matcher monthlyMatcher = REPEAT_MONTHLY_PATTERN.matcher(text);
                if (monthlyMatcher.find()) {
                    repeatInterval = RepeatInterval.MONTHLY.name();
                    hasTemporalPattern = true;
                }
            }
        }

        // 2. Detect Relative Offsets (e.g., "in 2 hours", "in 15 minutes")
        Matcher offsetMatcher = OFFSET_PATTERN.matcher(text);
        if (offsetMatcher.find()) {
            hasTemporalPattern = true;
            int amount = Integer.parseInt(offsetMatcher.group(1));
            String unit = offsetMatcher.group(2).toLowerCase(Locale.ROOT);
            if (unit.startsWith("min")) {
                cal.add(Calendar.MINUTE, amount);
            } else if (unit.startsWith("hour") || unit.startsWith("hr")) {
                cal.add(Calendar.HOUR_OF_DAY, amount);
            } else if (unit.startsWith("day")) {
                cal.add(Calendar.DAY_OF_YEAR, amount);
            }
        } else {
            // 3. Detect Calendar Date
            boolean dateSet = false;

            if (DAY_AFTER_TOMORROW_PATTERN.matcher(text).find()) {
                cal.add(Calendar.DAY_OF_YEAR, 2);
                dateSet = true;
                hasTemporalPattern = true;
            } else if (TOMORROW_PATTERN.matcher(text).find()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
                dateSet = true;
                hasTemporalPattern = true;
            } else if (TODAY_PATTERN.matcher(text).find()) {
                dateSet = true;
                hasTemporalPattern = true;
            }

            if (!dateSet) {
                // Day of week check (e.g. "on Friday", "next Tuesday")
                Matcher dowMatcher = DAY_OF_WEEK_PATTERN.matcher(text);
                if (dowMatcher.find()) {
                    int targetDow = parseDayOfWeek(dowMatcher.group(1));
                    if (targetDow != -1) {
                        int currentDow = cal.get(Calendar.DAY_OF_WEEK);
                        int daysToAdd = (targetDow - currentDow + 7) % 7;
                        if (daysToAdd == 0) {
                            daysToAdd = 7; // Next week if same day mentioned
                        }
                        cal.add(Calendar.DAY_OF_YEAR, daysToAdd);
                        dateSet = true;
                        hasTemporalPattern = true;
                    }
                }
            }

            if (!dateSet) {
                // Calendar month and day check (e.g. "Oct 25" or "25th October")
                Matcher m1 = MONTH_NAME_DATE_PATTERN.matcher(text);
                if (m1.find()) {
                    int month = parseMonth(m1.group(1));
                    int day = Integer.parseInt(m1.group(2));
                    String yearStr = m1.group(3);
                    if (month != -1) {
                        cal.set(Calendar.MONTH, month);
                        cal.set(Calendar.DAY_OF_MONTH, day);
                        if (yearStr != null && !yearStr.isEmpty()) {
                            cal.set(Calendar.YEAR, Integer.parseInt(yearStr));
                        } else if (cal.getTimeInMillis() < nowEpochMillis) {
                            cal.add(Calendar.YEAR, 1);
                        }
                        dateSet = true;
                        hasTemporalPattern = true;
                    }
                } else {
                    Matcher m2 = DAY_MONTH_NAME_PATTERN.matcher(text);
                    if (m2.find()) {
                        int day = Integer.parseInt(m2.group(1));
                        int month = parseMonth(m2.group(2));
                        String yearStr = m2.group(3);
                        if (month != -1) {
                            cal.set(Calendar.MONTH, month);
                            cal.set(Calendar.DAY_OF_MONTH, day);
                            if (yearStr != null && !yearStr.isEmpty()) {
                                cal.set(Calendar.YEAR, Integer.parseInt(yearStr));
                            } else if (cal.getTimeInMillis() < nowEpochMillis) {
                                cal.add(Calendar.YEAR, 1);
                            }
                            dateSet = true;
                            hasTemporalPattern = true;
                        }
                    }
                }
            }

            // 4. Detect Time of Day
            boolean timeSet = false;
            Matcher timeMatcher = TIME_PATTERN.matcher(text);
            if (timeMatcher.find()) {
                int hour = Integer.parseInt(timeMatcher.group(1));
                int minute = (timeMatcher.group(2) != null) ? Integer.parseInt(timeMatcher.group(2)) : 0;
                String ampm = timeMatcher.group(3).toLowerCase(Locale.ROOT);

                if ("pm".equals(ampm) && hour < 12) {
                    hour += 12;
                } else if ("am".equals(ampm) && hour == 12) {
                    hour = 0;
                }
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                timeSet = true;
                hasTemporalPattern = true;
            } else {
                Matcher time24 = TIME_24H_PATTERN.matcher(text);
                if (time24.find()) {
                    int hour = Integer.parseInt(time24.group(1));
                    int minute = Integer.parseInt(time24.group(2));
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                } else {
                    Matcher atSimple = TIME_AT_SIMPLE_PATTERN.matcher(text);
                    if (atSimple.find()) {
                        int hour = Integer.parseInt(atSimple.group(1));
                        if (hour >= 1 && hour <= 12) {
                            // Default to PM if reasonable daytime hour (e.g. at 5 -> 17:00), otherwise AM
                            if (hour >= 1 && hour <= 6) {
                                hour += 12;
                            }
                        }
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        timeSet = true;
                        hasTemporalPattern = true;
                    }
                }
            }

            if (!timeSet) {
                // Check qualitative time indicators
                if (TIME_MORNING.matcher(text).find()) {
                    cal.set(Calendar.HOUR_OF_DAY, 9);
                    cal.set(Calendar.MINUTE, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                } else if (TIME_NOON.matcher(text).find()) {
                    cal.set(Calendar.HOUR_OF_DAY, 12);
                    cal.set(Calendar.MINUTE, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                } else if (TIME_AFTERNOON.matcher(text).find()) {
                    cal.set(Calendar.HOUR_OF_DAY, 14);
                    cal.set(Calendar.MINUTE, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                } else if (TIME_EVENING.matcher(text).find()) {
                    cal.set(Calendar.HOUR_OF_DAY, 18);
                    cal.set(Calendar.MINUTE, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                } else if (TIME_NIGHT.matcher(text).find()) {
                    cal.set(Calendar.HOUR_OF_DAY, 20);
                    cal.set(Calendar.MINUTE, 0);
                    timeSet = true;
                    hasTemporalPattern = true;
                }
            }

            // If date was set but no time specified, default to 9:00 AM
            if (dateSet && !timeSet) {
                cal.set(Calendar.HOUR_OF_DAY, 9);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
            }

            // If time was set for today and it already passed, push to tomorrow (or next repeat)
            if (!dateSet && timeSet && cal.getTimeInMillis() <= nowEpochMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }

        // If intent was detected but no time or date could be identified, default to tomorrow at 9:00 AM
        if (hasExplicitIntent && !hasTemporalPattern) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, 9);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }

        boolean hasReminder = hasExplicitIntent || hasTemporalPattern;
        long targetTimestamp = cal.getTimeInMillis();

        // 5. Clean and extract the reminder action title
        String actionTitle = extractActionTitle(text);

        // 6. Format human-readable explanation
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault());
        String formattedDate = sdf.format(new Date(targetTimestamp));
        String explanation = formattedDate + (repeatInterval.equals(RepeatInterval.NONE.name()) ? "" : " (Repeats: " + repeatInterval + ")");

        return new ParsedReminder(hasReminder, actionTitle, targetTimestamp, repeatInterval, explanation);
    }

    /**
     * Cleans original input text by removing trigger phrases and temporal mentions.
     *
     * @param text raw memory text
     * @return clean title suitable for notification and memory card
     */
    private static String extractActionTitle(String text) {
        String cleaned = text;

        // Remove trigger prefixes
        cleaned = cleaned.replaceAll("(?i)^\\s*(?:remind\\s+me\\s+to|remember\\s+to|don't\\s+forget\\s+to|i\\s+need\\s+to|i\\s+have\\s+to|schedule\\s+a|schedule\\s+an|schedule)\\s+", "");
        cleaned = cleaned.replaceAll("(?i)\\b(?:remind\\s+me\\s+to|remember\\s+to)\\b", "");

        // Remove repetition phrases
        cleaned = REPEAT_DAILY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = REPEAT_WEEKLY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = REPEAT_MONTHLY_PATTERN.matcher(cleaned).replaceAll("");

        // Remove relative dates and offsets
        cleaned = OFFSET_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = DAY_AFTER_TOMORROW_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TOMORROW_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TODAY_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = DAY_OF_WEEK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = MONTH_NAME_DATE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = DAY_MONTH_NAME_PATTERN.matcher(cleaned).replaceAll("");

        // Remove time phrases
        cleaned = TIME_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TIME_24H_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TIME_AT_SIMPLE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = TIME_MORNING.matcher(cleaned).replaceAll("");
        cleaned = TIME_NOON.matcher(cleaned).replaceAll("");
        cleaned = TIME_AFTERNOON.matcher(cleaned).replaceAll("");
        cleaned = TIME_EVENING.matcher(cleaned).replaceAll("");
        cleaned = TIME_NIGHT.matcher(cleaned).replaceAll("");

        // Clean up punctuation and whitespace
        cleaned = cleaned.replaceAll("(?i)\\b(?:at|on|for|in)\\s*$", "");
        cleaned = cleaned.replaceAll("^[\\s,.-]+|[\\s,.-]+$", "").trim();

        if (cleaned.isEmpty()) {
            cleaned = text.trim();
        }

        // Capitalize first letter
        if (cleaned.length() > 0) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }

        return cleaned;
    }

    private static int parseDayOfWeek(String dow) {
        if (dow == null) return -1;
        switch (dow.toLowerCase(Locale.ROOT)) {
            case "sunday": return Calendar.SUNDAY;
            case "monday": return Calendar.MONDAY;
            case "tuesday": return Calendar.TUESDAY;
            case "wednesday": return Calendar.WEDNESDAY;
            case "thursday": return Calendar.THURSDAY;
            case "friday": return Calendar.FRIDAY;
            case "saturday": return Calendar.SATURDAY;
            default: return -1;
        }
    }

    private static int parseMonth(String month) {
        if (month == null) return -1;
        switch (month.toLowerCase(Locale.ROOT)) {
            case "jan": case "january": return Calendar.JANUARY;
            case "feb": case "february": return Calendar.FEBRUARY;
            case "mar": case "march": return Calendar.MARCH;
            case "apr": case "april": return Calendar.APRIL;
            case "may": return Calendar.MAY;
            case "jun": case "june": return Calendar.JUNE;
            case "jul": case "july": return Calendar.JULY;
            case "aug": case "august": return Calendar.AUGUST;
            case "sep": case "september": return Calendar.SEPTEMBER;
            case "oct": case "october": return Calendar.OCTOBER;
            case "nov": case "november": return Calendar.NOVEMBER;
            case "dec": case "december": return Calendar.DECEMBER;
            default: return -1;
        }
    }
}
