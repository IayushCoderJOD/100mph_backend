package in.hundredmph.api.session.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

/**
 * POST a completed session.
 *
 * <p>{@code id} is generated on the device when the member taps finish, and
 * becomes the document id. A retry after a dropped connection therefore writes
 * the same row instead of a second one — the app is offline-capable and phones
 * drop connections mid-request.
 */
public record LogSessionRequest(
        @NotBlank String id,
        /** The member's calendar day. Defaults to their today if absent. */
        LocalDate localDate,
        String sessionTypeId,
        /** "guided" or "logged". Defaults to "logged". */
        String source,
        Integer durationMin,
        String note,
        List<CompletedExerciseInput> exercises) {

    public record CompletedExerciseInput(String exerciseId, Boolean completed, String note) {}
}
