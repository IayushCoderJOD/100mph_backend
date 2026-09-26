package in.hundredmph.api.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * PUT the whole week. Days left out are rest, so a partial body is never
 * ambiguous. Order within a day is the order in the list.
 */
public record UpdatePlanRequest(@NotNull Map<String, List<@Valid PlannedExerciseInput>> days) {

    public record PlannedExerciseInput(
            @NotBlank String exerciseId,
            @NotBlank @Size(max = 200) String prescription) {}
}
