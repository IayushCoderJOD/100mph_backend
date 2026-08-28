package in.hundredmph.api.progression.dto;

import in.hundredmph.api.content.model.ProgressionLevel;
import in.hundredmph.api.content.model.SignatureExercise;
import java.util.List;

/** Where the member is on a ladder, and the whole ladder for context. */
public record ProgressionResponse(
        SignatureExercise signatureExercise,
        ProgressionLevel currentLevel,
        List<ProgressionLevel> levels) {
}
