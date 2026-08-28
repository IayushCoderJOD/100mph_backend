package in.hundredmph.api.progression;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.content.model.ProgressionLevel;
import in.hundredmph.api.content.model.SignatureExercise;
import in.hundredmph.api.domain.progression.UserProgression;
import in.hundredmph.api.domain.progression.UserProgressionRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.progression.dto.ProgressionResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProgressionService {

    private final UserProgressionRepository progressions;
    private final UserRepository users;
    private final ContentCatalogue catalogue;

    public ProgressionService(UserProgressionRepository progressions,
                              UserRepository users,
                              ContentCatalogue catalogue) {
        this.progressions = progressions;
        this.users = users;
        this.catalogue = catalogue;
    }

    /**
     * Every ladder in the member's program, with where they currently stand.
     * A member who has never been moved sits on level 1 — the ladder's first
     * rung is the default, not an empty state.
     */
    public List<ProgressionResponse> forUser(String userId) {
        User user = requireUser(userId);
        String programId = user.getActiveProgramId();
        if (programId == null) return List.of();

        List<ProgressionResponse> result = new ArrayList<>();

        for (SignatureExercise signature : catalogue.signatureExercisesFor(programId)) {
            List<ProgressionLevel> levels = catalogue.progressionLevelsFor(signature.id());
            if (levels.isEmpty()) continue;

            ProgressionLevel current = progressions
                    .findByUserIdAndSignatureExerciseId(userId, signature.id())
                    .flatMap(stored -> catalogue.progressionLevel(stored.getCurrentProgressionLevelId()))
                    .orElse(levels.get(0));

            result.add(new ProgressionResponse(signature, current, levels));
        }

        return result;
    }

    /** Moves a member to a level. Used by the member and, as an override, by staff. */
    public ProgressionResponse set(String userId, String signatureExerciseId, String levelId, String reason) {
        User user = requireUser(userId);
        String programId = user.getActiveProgramId();
        if (programId == null) {
            throw ApiException.of(ErrorCode.NO_ACTIVE_PROGRAM, "This account is not on a program");
        }

        ProgressionLevel level = catalogue.progressionLevel(levelId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "No progression level with id " + levelId));

        // The level has to be a rung on the ladder it is being set against.
        if (!level.signatureExerciseId().equals(signatureExerciseId)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED,
                    "That level does not belong to that exercise");
        }

        // …and the ladder has to belong to the member's own program.
        SignatureExercise signature = catalogue.signatureExercisesFor(programId).stream()
                .filter(s -> s.id().equals(signatureExerciseId))
                .findFirst()
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND,
                        "No signature exercise with id " + signatureExerciseId));

        Instant now = Instant.now();
        UserProgression stored = progressions
                .findByUserIdAndSignatureExerciseId(userId, signatureExerciseId)
                .orElseGet(() -> {
                    UserProgression created = new UserProgression();
                    created.setUserId(userId);
                    created.setSignatureExerciseId(signatureExerciseId);
                    return created;
                });

        stored.setCurrentProgressionLevelId(levelId);
        stored.setReason(reason);
        stored.setUpdatedAt(now);
        progressions.save(stored);

        return new ProgressionResponse(signature, level, catalogue.progressionLevelsFor(signatureExerciseId));
    }

    private User requireUser(String userId) {
        return users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));
    }
}
