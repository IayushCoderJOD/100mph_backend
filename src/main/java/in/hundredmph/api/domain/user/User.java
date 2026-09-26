package in.hundredmph.api.domain.user;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The user record. Field names are the snake_case the client already expects,
 * so a document read out of Mongo maps onto src/data/types.ts without a
 * translation layer in between.
 *
 * <p>The password hash is the one field that never leaves this class — DTOs are
 * built by hand precisely so it cannot be serialised by accident.
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Field("full_name")
    private String fullName;

    /** Stored lower-cased. The unique index is what actually enforces it. */
    @Indexed(unique = true, sparse = true)
    private String email;

    @Indexed(unique = true, sparse = true)
    private String phone;

    @Field("avatar_url")
    private String avatarUrl;

    /** IANA zone. Drives the local calendar day for streaks and reminders. */
    private String timezone = "Asia/Kolkata";

    private UserRole role = UserRole.MEMBER;

    private UserStatus status = UserStatus.INVITED;

    /** Null for staff: an admin is not a patient with extra buttons. */
    @Field("active_program_id")
    private String activeProgramId;

    /**
     * Filled in by the member from Settings, and health data under the DPDP
     * Act like the pain log: never logged, and part of the export/delete path
     * when that is built.
     */
    @Field("date_of_birth")
    private LocalDate dateOfBirth;

    @Field("height_cm")
    private Integer heightCm;

    @Field("weight_kg")
    private Double weightKg;

    @Field("password_hash")
    private String passwordHash;

    @Field("member_since")
    private LocalDate memberSince;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    /** Set on account-deletion request; a soft-deleted user cannot sign in. */
    @Field("deleted_at")
    private Instant deletedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public String getActiveProgramId() { return activeProgramId; }
    public void setActiveProgramId(String activeProgramId) { this.activeProgramId = activeProgramId; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }

    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public LocalDate getMemberSince() { return memberSince; }
    public void setMemberSince(LocalDate memberSince) { this.memberSince = memberSince; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public boolean isDeleted() { return deletedAt != null; }
}
