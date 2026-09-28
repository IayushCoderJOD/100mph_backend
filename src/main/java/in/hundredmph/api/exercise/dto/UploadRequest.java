package in.hundredmph.api.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** POST /v1/admin/exercises/uploads — asks for somewhere to put one file. */
public record UploadRequest(
        /** video | poster */
        @NotBlank String kind,
        @NotBlank String contentType,
        @Positive long sizeBytes,
        /** Only used to make the stored key readable. */
        @Size(max = 200) String fileName) {
}
