package in.hundredmph.api.exercise.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /v1/admin/exercises/{id}. Every field is optional; null leaves it
 * alone. {@code videoKey} and {@code thumbnailKey} attach a file that has
 * already been uploaded — the server checks the bucket before accepting it.
 */
public record UpdateExerciseRequest(
        @Size(max = 80) String name,
        String category,
        @Size(max = 100) String focus,
        @Size(max = 200) String prerequisites,
        @Size(max = 2000) String instructions,
        @Size(max = 2000) String purpose,
        @Size(max = 80) String suggestedSets,
        Boolean hidden,
        @Size(max = 300) String videoKey,
        /** Blank removes the poster. */
        @Size(max = 300) String thumbnailKey) {
}
