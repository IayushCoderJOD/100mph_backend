package in.hundredmph.api.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What a coach prescribes on top of the program. */
public record AssignExerciseRequest(
        @NotBlank String exerciseId,
        @NotBlank @Size(max = 200) String prescription,
        @Size(max = 1000) String note) {
}
