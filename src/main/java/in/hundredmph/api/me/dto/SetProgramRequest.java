package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.NotBlank;

public record SetProgramRequest(@NotBlank String programId) {
}
