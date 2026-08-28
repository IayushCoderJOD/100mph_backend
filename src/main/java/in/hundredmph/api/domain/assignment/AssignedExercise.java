package in.hundredmph.api.domain.assignment;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * An exercise a coach has prescribed to one client on top of their program.
 *
 * <p>It points at the shared catalogue so the instructions, purpose and video
 * come along, and carries only what is specific to this person: what they are
 * being asked to do, and why the coach added it.
 */
@Document(collection = "assigned_exercises")
public class AssignedExercise {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("exercise_id")
    private String exerciseId;

    /** The staff member who prescribed it — shown to the client and audited. */
    @Field("assigned_by")
    private String assignedBy;

    private String prescription;

    private String note;

    @Field("sort_order")
    private int sortOrder;

    /** Withdrawn prescriptions are deactivated, not deleted — the history matters. */
    @Field("is_active")
    private boolean active = true;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String exerciseId) { this.exerciseId = exerciseId; }

    public String getAssignedBy() { return assignedBy; }
    public void setAssignedBy(String assignedBy) { this.assignedBy = assignedBy; }

    public String getPrescription() { return prescription; }
    public void setPrescription(String prescription) { this.prescription = prescription; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
