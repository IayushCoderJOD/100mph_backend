package in.hundredmph.api.session.dto;

import in.hundredmph.api.domain.session.SessionLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SessionLogResponse(
        String id,
        String userId,
        String programId,
        String sessionTypeId,
        LocalDate localDate,
        String source,
        Integer durationMin,
        String note,
        List<CompletedExerciseDto> completedExercises,
        Instant completedAt) {

    public record CompletedExerciseDto(String exerciseId, boolean completed, String note) {}

    public static SessionLogResponse from(SessionLog log) {
        return new SessionLogResponse(
                log.getId(),
                log.getUserId(),
                log.getProgramId(),
                log.getSessionTypeId(),
                log.getLocalDate(),
                log.getSource(),
                log.getDurationMin(),
                log.getNote(),
                log.getCompletedExercises().stream()
                        .map(e -> new CompletedExerciseDto(e.exerciseId(), e.completed(), e.note()))
                        .toList(),
                log.getCompletedAt());
    }
}
