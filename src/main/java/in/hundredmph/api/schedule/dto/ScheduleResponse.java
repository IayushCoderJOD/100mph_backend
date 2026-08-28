package in.hundredmph.api.schedule.dto;

import in.hundredmph.api.domain.schedule.DayOfWeek;
import java.util.Map;

/**
 * The week as the client edits it: one session type per day, or null for rest.
 * Keys are the lower-case day names the app already uses, so this drops
 * straight into its ScheduleMap.
 */
public record ScheduleResponse(String programId, Map<String, String> days) {

    public static ScheduleResponse of(String programId, Map<DayOfWeek, String> days) {
        // Built in week order so the JSON reads Monday-first, like the plan.
        java.util.LinkedHashMap<String, String> ordered = new java.util.LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.ordered()) {
            ordered.put(day.wire(), days.get(day));
        }
        return new ScheduleResponse(programId, ordered);
    }
}
