package in.hundredmph.api.plan.dto;

import in.hundredmph.api.content.model.Exercise;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The week as the app renders it: every day present, Monday first, with the
 * catalogue exercise inlined on each line so a phone can draw the day without
 * a lookup per row. An empty list is a rest day.
 */
public record WeeklyPlanResponse(
        String userId,
        Map<String, List<PlannedExerciseResponse>> days,
        String updatedBy,
        String updatedByName,
        Instant updatedAt) {

    public record PlannedExerciseResponse(
            String exerciseId,
            int sortOrder,
            String prescription,
            /** Null only if the catalogue no longer has it — the app shows a placeholder. */
            Exercise exercise) {}
}
