package in.hundredmph.api.admin.dto;

import in.hundredmph.api.assignment.dto.AssignedExerciseResponse;
import in.hundredmph.api.checkin.dto.CheckInResponse;
import in.hundredmph.api.checkin.dto.CheckInSummaryResponse;
import in.hundredmph.api.me.dto.UserDto;
import in.hundredmph.api.progression.dto.ProgressionResponse;
import in.hundredmph.api.schedule.dto.ScheduleResponse;
import in.hundredmph.api.session.dto.SessionLogResponse;
import java.util.List;

/**
 * The full picture of one client, in one request — what the coach opens before
 * a consultation. Assembled server-side because six round trips on a phone in
 * a clinic is six chances to show half a screen.
 */
public record ClientDetailResponse(
        UserDto user,
        ScheduleResponse schedule,
        List<SessionLogResponse> recentSessions,
        List<CheckInResponse> recentCheckIns,
        CheckInSummaryResponse summary,
        List<AssignedExerciseResponse> assignedExercises,
        List<ProgressionResponse> progression) {
}
