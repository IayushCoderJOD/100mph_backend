package in.hundredmph.api.assignment.dto;

import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.domain.assignment.AssignedExercise;
import java.time.Instant;

/**
 * A prescription, with the catalogue exercise inlined so the client can render
 * the card without a second lookup.
 */
public record AssignedExerciseResponse(
        String id,
        String userId,
        String exerciseId,
        Exercise exercise,
        String assignedBy,
        String assignedByName,
        String prescription,
        String note,
        int sortOrder,
        Instant createdAt) {

    public static AssignedExerciseResponse of(AssignedExercise assignment, Exercise exercise, String assignedByName) {
        return new AssignedExerciseResponse(
                assignment.getId(),
                assignment.getUserId(),
                assignment.getExerciseId(),
                exercise,
                assignment.getAssignedBy(),
                assignedByName,
                assignment.getPrescription(),
                assignment.getNote(),
                assignment.getSortOrder(),
                assignment.getCreatedAt());
    }
}
