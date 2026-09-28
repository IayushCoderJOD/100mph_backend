package in.hundredmph.api.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /v1/admin/exercises — a new movement, text first. The video is
 * uploaded separately and attached with a PATCH once it has landed, so a slow
 * upload never holds the form hostage.
 */
public record CreateExerciseRequest(
        @NotBlank @Size(min = 2, max = 80) String name,
        @NotBlank String category,
        @Size(max = 100) String focus,
        @Size(max = 200) String prerequisites,
        @Size(max = 2000) String instructions,
        @Size(max = 2000) String purpose,
        @Size(max = 80) String suggestedSets,
        String programId) {
}
