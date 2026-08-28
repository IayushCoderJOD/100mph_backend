package in.hundredmph.api.domain.progression;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Where a member currently sits on a signature exercise's ladder. */
@Document(collection = "user_progressions")
@CompoundIndex(name = "progression_user_signature_idx",
        def = "{'user_id': 1, 'signature_exercise_id': 1}", unique = true)
public class UserProgression {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("signature_exercise_id")
    private String signatureExerciseId;

    @Field("current_progression_level_id")
    private String currentProgressionLevelId;

    /** "self_reported" or "coach_set" — who moved them, for the audit trail. */
    private String reason;

    @Field("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSignatureExerciseId() { return signatureExerciseId; }
    public void setSignatureExerciseId(String signatureExerciseId) {
        this.signatureExerciseId = signatureExerciseId;
    }

    public String getCurrentProgressionLevelId() { return currentProgressionLevelId; }
    public void setCurrentProgressionLevelId(String currentProgressionLevelId) {
        this.currentProgressionLevelId = currentProgressionLevelId;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
