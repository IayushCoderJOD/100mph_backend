package in.hundredmph.api.domain.auth;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One issued refresh token. The raw value is never stored — only its SHA-256
 * digest — so a database dump cannot be replayed as a session.
 *
 * <p>{@code familyId} is the rotation lineage: every token descended from one
 * login shares it. That is what makes reuse detection possible. If a token that
 * has already been rotated away is presented again, the presenter is holding a
 * stolen copy, and the whole family is revoked rather than just that one row.
 */
@Document(collection = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Indexed(unique = true)
    @Field("token_hash")
    private String tokenHash;

    /** Ties a token to the phone it was issued to. Informational, not a check. */
    @Field("device_id")
    private String deviceId;

    @Indexed
    @Field("family_id")
    private String familyId;

    @Field("expires_at")
    private Instant expiresAt;

    /** Set when rotated away, on logout, or when the family is burned. */
    @Field("revoked_at")
    private Instant revokedAt;

    @Field("created_at")
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getFamilyId() { return familyId; }
    public void setFamilyId(String familyId) { this.familyId = familyId; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }

    public boolean isUsable(Instant now) { return !isRevoked() && !isExpired(now); }
}
