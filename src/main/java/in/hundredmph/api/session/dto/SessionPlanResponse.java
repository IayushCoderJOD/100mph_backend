package in.hundredmph.api.session.dto;

import in.hundredmph.api.content.model.Exercise;
import java.time.LocalDate;
import java.util.List;

/**
 * What a day asks of the member: the exercises in the order they are
 * performed, with the prescription for each. An empty list is a rest day.
 */
public record SessionPlanResponse(
        LocalDate localDate,
        List<PlannedExercise> exercises,
        /** True when this day is already logged as done. */
        boolean completed) {

    public record PlannedExercise(Exercise exercise, String prescription) {}
}
