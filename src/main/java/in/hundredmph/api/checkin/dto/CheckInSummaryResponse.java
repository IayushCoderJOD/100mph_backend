package in.hundredmph.api.checkin.dto;

import java.time.LocalDate;

/**
 * The numbers the progress tab shows. Computed server-side so every client
 * agrees on what a streak is, rather than each one doing its own arithmetic.
 */
public record CheckInSummaryResponse(
        /** Consecutive days up to and including today. */
        int currentStreak,
        int longestStreak,
        int totalCheckIns,
        int totalSessions,
        /** Mean pain over the window, or null when nothing was scored. */
        Double averagePainScore,
        Integer latestPainScore,
        LocalDate latestCheckInDate,
        /** Share of planned sessions actually completed, 0–1, over the window. */
        Double adherence) {
}
