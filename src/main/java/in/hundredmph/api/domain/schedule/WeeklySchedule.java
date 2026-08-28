package in.hundredmph.api.domain.schedule;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A member's training week.
 *
 * <p>The plan is the week, not a row per date — the app already computes real
 * dates from the template, and storing dated rows for a repeating plan means
 * writing rows for a future that has not happened. Days map to a session type
 * id, or to null for rest.
 *
 * <p>One document per user per program, so switching programs keeps the week
 * you had on the old one.
 */
@Document(collection = "weekly_schedules")
@CompoundIndex(name = "schedule_user_program_idx", def = "{'user_id': 1, 'program_id': 1}", unique = true)
public class WeeklySchedule {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("program_id")
    private String programId;

    /** day → session type id, or null for a rest day. */
    private Map<DayOfWeek, String> days = new EnumMap<>(DayOfWeek.class);

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProgramId() { return programId; }
    public void setProgramId(String programId) { this.programId = programId; }

    public Map<DayOfWeek, String> getDays() { return days; }
    public void setDays(Map<DayOfWeek, String> days) {
        this.days = days == null ? new EnumMap<>(DayOfWeek.class) : new EnumMap<>(days);
    }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /** The session type planned for a day, or null on a rest day. */
    public String sessionTypeOn(DayOfWeek day) {
        return days.get(day);
    }
}
