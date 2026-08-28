package in.hundredmph.api.domain.auth;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One sign-in attempt, kept so repeated failures against a single account can be
 * locked out. Rows expire on their own via a TTL index — this is a throttle,
 * not an audit log, and nothing here should outlive its usefulness.
 */
@Document(collection = "login_attempts")
public class LoginAttempt {

    @Id
    private String id;

    /** Lower-cased. Indexed with createdAt because that is how it is queried. */
    @Indexed
    private String email;

    private boolean succeeded;

    @Field("request_ip")
    private String requestIp;

    /** TTL: Mongo drops the document 24h after it is written. */
    @Indexed(expireAfterSeconds = 86400)
    @Field("created_at")
    private Instant createdAt;

    public static LoginAttempt of(String email, boolean succeeded, String requestIp, Instant now) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.email = email;
        attempt.succeeded = succeeded;
        attempt.requestIp = requestIp;
        attempt.createdAt = now;
        return attempt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isSucceeded() { return succeeded; }
    public void setSucceeded(boolean succeeded) { this.succeeded = succeeded; }

    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
