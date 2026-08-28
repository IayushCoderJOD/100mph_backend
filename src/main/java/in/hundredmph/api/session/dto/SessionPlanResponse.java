package in.hundredmph.api.session.dto;

import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.content.model.SessionType;
import java.util.List;

/**
 * What today's session asks of the member: the session type, and the exercises
 * in the order they are performed with the prescription for each.
 */
public record SessionPlanResponse(
        SessionType sessionType,
        List<PlannedExercise> exercises,
        /** True when this day is already logged as done. */
        boolean completed) {

    public record PlannedExercise(Exercise exercise, String prescription) {}
}
