package in.hundredmph.api.progression.dto;

import jakarta.validation.constraints.NotBlank;

public record SetProgressionRequest(
        @NotBlank String signatureExerciseId,
        @NotBlank String progressionLevelId) {
}
