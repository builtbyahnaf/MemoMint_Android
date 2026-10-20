package dev.builtbyahnaf.memomint.domain.model;

/**
 * Supported repetition frequencies for scheduled reminders and notifications.
 *
 * <h2>Values</h2>
 * <ul>
 *   <li>{@link #NONE}: Fires once at the specified date and time.</li>
 *   <li>{@link #DAILY}: Fires every 24 hours at the specified wall-clock time.</li>
 *   <li>{@link #WEEKLY}: Fires every 7 days at the specified day and time.</li>
 *   <li>{@link #MONTHLY}: Fires once a month on the same day and time.</li>
 * </ul>
 */
public enum RepeatInterval {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    /**
     * Parses a string representation into a {@link RepeatInterval}, falling back to {@link #NONE} if invalid.
     *
     * @param value raw string value
     * @return matching RepeatInterval enum constant
     */
    public static RepeatInterval fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        try {
            return RepeatInterval.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
