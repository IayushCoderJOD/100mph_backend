package in.hundredmph.api.content.model;

import in.hundredmph.api.domain.schedule.DayOfWeek;

/** The program's suggested week, before a member edits it. */
public record DefaultScheduleEntry(String programId, DayOfWeek dayOfWeek, String sessionTypeId) {
}
