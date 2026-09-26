package in.hundredmph.api.admin.dto;

import in.hundredmph.api.me.dto.UserDto;
import java.time.LocalDate;

/**
 * One row of the roster. Carries the two things that decide whether a client
 * needs a nudge — how well they are keeping to the plan, and what they last
 * reported — so the list is a worklist rather than an address book.
 */
public record ClientSummaryResponse(
        UserDto user,
        Double adherence,
        Integer latestPainScore,
        Double averagePainScore,
        LocalDate lastActiveDate,
        int currentStreak,
        int totalCheckIns,
        boolean checkedInToday,
        /** Sessions logged in the last seven days, today included. */
        int sessionsThisWeek,
        int activeAssignments,
        /** False until the coach has written this client's week. */
        boolean hasPlan,
        /** True when the coach should look at this client. */
        boolean needsAttention) {
}
