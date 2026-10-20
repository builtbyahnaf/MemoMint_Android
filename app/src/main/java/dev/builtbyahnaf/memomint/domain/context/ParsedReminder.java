package dev.builtbyahnaf.memomint.domain.context;

import dev.builtbyahnaf.memomint.domain.model.RepeatInterval;

/**
 * Encapsulates structured reminder details extracted intelligently from unstructured text.
 */
public class ParsedReminder {

    private final boolean hasReminderIntent;
    private final String reminderTitle;
    private final long targetTimestamp;
    private final String repeatInterval;
    private final String explanation;

    public ParsedReminder(
            boolean hasReminderIntent,
            String reminderTitle,
            long targetTimestamp,
            String repeatInterval,
            String explanation
    ) {
        this.hasReminderIntent = hasReminderIntent;
        this.reminderTitle = (reminderTitle != null) ? reminderTitle.trim() : "";
        this.targetTimestamp = targetTimestamp;
        this.repeatInterval = (repeatInterval != null) ? repeatInterval : RepeatInterval.NONE.name();
        this.explanation = (explanation != null) ? explanation : "";
    }

    public boolean hasReminderIntent() {
        return hasReminderIntent;
    }

    public String getReminderTitle() {
        return reminderTitle;
    }

    public long getTargetTimestamp() {
        return targetTimestamp;
    }

    public String getRepeatInterval() {
        return repeatInterval;
    }

    public String getExplanation() {
        return explanation;
    }

    @Override
    public String toString() {
        return "ParsedReminder{"
                + "hasIntent=" + hasReminderIntent
                + ", title='" + reminderTitle + '\''
                + ", targetTimestamp=" + targetTimestamp
                + ", repeatInterval='" + repeatInterval + '\''
                + ", explanation='" + explanation + '\''
                + '}';
    }
}
