package in.hundredmph.api.domain.exercise;

import in.hundredmph.api.content.model.Exercise;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One movement in the exercise library.
 *
 * <p>Two sources write here. The authored catalogue (catalogue.json, generated
 * from the app's mock.ts) seeds every movement it knows, and an admin adds and
 * edits movements from the app. {@code editedByAdmin} is what keeps them from
 * fighting: a movement nobody has touched in the app keeps following the
 * catalogue — a copy fix in mock.ts still lands on the next deploy — while one
 * an admin has changed belongs to the admin from then on.
 *
 * <p>The id is stable forever: weekly plans, prescriptions and session logs
 * store it. Nothing is ever deleted, only hidden.
 */
@Document(collection = "exercises")
public class ExerciseDocument {

    public static final String ORIGIN_CATALOGUE = "catalogue";
    public static final String ORIGIN_ADMIN = "admin";

    @Id
    private String id;

    @Field("program_id")
    private String programId;

    private String name;

    private String category;

    private String focus;

    /** A media key in the bucket, e.g. demos/glute-bridge-e76ae6c3-720p.mp4. */
    @Field("video_url")
    private String videoUrl;

    @Field("thumbnail_url")
    private String thumbnailUrl;

    private String prerequisites;

    private String instructions;

    private String purpose;

    @Field("suggested_sets")
    private String suggestedSets;

    private boolean hidden;

    /** catalogue | admin — who first wrote it. */
    private String origin;

    @Field("edited_by_admin")
    private boolean editedByAdmin;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    /** The admin who last changed it; null while it still follows the catalogue. */
    @Field("updated_by")
    private String updatedBy;

    public static ExerciseDocument fromCatalogue(Exercise exercise, Instant now) {
        ExerciseDocument doc = new ExerciseDocument();
        doc.id = exercise.id();
        doc.origin = ORIGIN_CATALOGUE;
        doc.createdAt = now;
        doc.applyCatalogue(exercise, now);
        return doc;
    }

    /** Takes the catalogue's copy of everything the catalogue owns. */
    public void applyCatalogue(Exercise exercise, Instant now) {
        programId = exercise.programId();
        name = exercise.name();
        category = exercise.category();
        focus = exercise.focus();
        videoUrl = exercise.videoUrl();
        thumbnailUrl = exercise.thumbnailUrl();
        prerequisites = exercise.prerequisites();
        instructions = exercise.instructions();
        purpose = exercise.purpose();
        suggestedSets = exercise.suggestedSets();
        hidden = exercise.hidden();
        updatedAt = now;
    }

    /** Whether the catalogue's copy says anything this document does not. */
    public boolean differsFrom(Exercise exercise) {
        return !toExercise().equals(exercise);
    }

    public Exercise toExercise() {
        return new Exercise(id, programId, name, category, focus, videoUrl, thumbnailUrl,
                prerequisites, instructions, purpose, suggestedSets, hidden);
    }

    /** Marks the admin as the owner from now on, so the catalogue stops overwriting it. */
    public void touchedBy(String adminId, Instant now) {
        editedByAdmin = true;
        updatedBy = adminId;
        updatedAt = now;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getFocus() { return focus; }
    public void setFocus(String focus) { this.focus = focus; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getSuggestedSets() { return suggestedSets; }
    public void setSuggestedSets(String suggestedSets) { this.suggestedSets = suggestedSets; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public boolean isEditedByAdmin() { return editedByAdmin; }
    public void setEditedByAdmin(boolean editedByAdmin) { this.editedByAdmin = editedByAdmin; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExerciseDocument that && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
