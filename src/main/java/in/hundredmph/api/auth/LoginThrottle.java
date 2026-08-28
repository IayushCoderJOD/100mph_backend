package in.hundredmph.api.auth;

import in.hundredmph.api.common.RateLimitedException;
import in.hundredmph.api.config.AuthProperties;
import in.hundredmph.api.domain.auth.LoginAttempt;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Per-account brute-force protection (README §5.1, §7.5).
 *
 * <p>Counting is keyed on the email rather than the user id deliberately: an
 * address with no account must be throttled exactly like one with an account,
 * or the difference in behaviour becomes an enumeration oracle.
 *
 * <p>Backed by Mongo because that is the store this service already has. Under
 * real load this belongs in Redis — the README already provisions REDIS_URL —
 * but the interface here does not change when it moves.
 */
@Component
public class LoginThrottle {

    private final LoginAttemptRepository repository;
    private final AuthProperties properties;

    public LoginThrottle(LoginAttemptRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Throws 429 when the account is locked. Call before checking the password. */
    public void assertNotLocked(String email, Instant now) {
        Instant windowStart = now.minus(properties.failedLoginWindow());
        List<LoginAttempt> recent =
                repository.findByEmailAndCreatedAtAfterOrderByCreatedAtDesc(email, windowStart);

        long failures = recent.stream().filter(attempt -> !attempt.isSucceeded()).count();
        if (failures < properties.maxFailedLogins()) {
            return;
        }

        // The lock runs from the most recent failure, so each further attempt
        // during a lock does not silently extend it — only a new failure does.
        Instant lastFailure = recent.stream()
                .filter(attempt -> !attempt.isSucceeded())
                .map(LoginAttempt::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(now);

        Instant unlockAt = lastFailure.plus(properties.lockDuration());
        if (unlockAt.isAfter(now)) {
            long retryAfter = Math.max(1, Duration.between(now, unlockAt).toSeconds());
            throw new RateLimitedException("Too many failed sign-in attempts", retryAfter);
        }
    }

    public void recordFailure(String email, String requestIp, Instant now) {
        repository.save(LoginAttempt.of(email, false, requestIp, now));
    }

    /** A success clears the slate, so one good login ends the lockout. */
    public void recordSuccess(String email, String requestIp, Instant now) {
        repository.deleteByEmail(email);
        repository.save(LoginAttempt.of(email, true, requestIp, now));
    }
}
