package in.hundredmph.api.domain.checkin;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One day's check-in: did they show up, and how did it feel.
 *
 * <p>This is health data under the DPDP Act — {@code painScore} and especially
 * {@code painLocation}, which is free text a member may type anything into.
 * It must never appear in a log line, a trace, or an error report.
 *
 * <p>Keyed on (user, local date) so a day can be revised but not duplicated;
 * the client PUTs the day, which makes the write idempotent by verb.
 */
@Document(collection = "check_ins")
@CompoundIndex(name = "checkin_user_date_idx", def = "{'user_id': 1, 'local_date': 1}", unique = true)
public class CheckIn {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    /** The member's own calendar day. */
    @Field("local_date")
    private LocalDate localDate;

    @Field("checked_in")
    private boolean checkedIn = true;

    /** 0 = no pain, 10 = severe. Null when the day was logged without a score. */
    @Field("pain_score")
    private Integer painScore;

    /** Free text: where it was felt. Health data — redact everywhere. */
    @Field("pain_location")
    private String painLocation;

    private String note;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDate getLocalDate() { return localDate; }
    public void setLocalDate(LocalDate localDate) { this.localDate = localDate; }

    public boolean isCheckedIn() { return checkedIn; }
    public void setCheckedIn(boolean checkedIn) { this.checkedIn = checkedIn; }

    public Integer getPainScore() { return painScore; }
    public void setPainScore(Integer painScore) { this.painScore = painScore; }

    public String getPainLocation() { return painLocation; }
    public void setPainLocation(String painLocation) { this.painLocation = painLocation; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Never let this class print its own contents — a stray log line or an
     * exception message would leak a member's pain notes.
     */
    @Override
    public String toString() {
        return "CheckIn{id=" + id + ", userId=" + userId + ", localDate=" + localDate + "}";
    }
}
