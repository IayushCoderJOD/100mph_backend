package in.hundredmph.api.domain.plan;

import in.hundredmph.api.domain.schedule.DayOfWeek;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A member's training week, written by their physio.
 *
 * <p>Each day carries the exercises to be done that day with a prescription
 * for each; a day with no entries is rest. It is the week, not a row per date:
 * the app computes real dates from the template, and a repeating plan stored
 * as dated rows means writing a future that has not happened yet.
 *
 * <p>One document per member. There is no per-program copy any more — the
 * plan is written for the person, and a knee patient who also needs a hip
 * exercise on Tuesdays is the normal case.
 */
@Document(collection = "weekly_plans")
public class WeeklyPlan {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("user_id")
    private String userId;

    /** day → the work, in the order it is performed. Absent day means rest. */
    private Map<DayOfWeek, List<PlannedExercise>> days = new EnumMap<>(DayOfWeek.class);

    /** The staff member who last wrote it — shown to the client and audited. */
    @Field("updated_by")
    private String updatedBy;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public record PlannedExercise(String exerciseId, int sortOrder, String prescription) {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Map<DayOfWeek, List<PlannedExercise>> getDays() { return days; }
    public void setDays(Map<DayOfWeek, List<PlannedExercise>> days) {
        this.days = days == null ? new EnumMap<>(DayOfWeek.class) : new EnumMap<>(days);
    }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** The work planned for a day; empty on a rest day. */
    public List<PlannedExercise> on(DayOfWeek day) {
        List<PlannedExercise> planned = days.get(day);
        return planned == null ? List.of() : planned;
    }
}
