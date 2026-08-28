package in.hundredmph.api.common;

import java.util.Map;

/** A 429 that carries the Retry-After the client should honour (README §7.1). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_ATTEMPTS, message, Map.of("retry_after_sec", retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() { return retryAfterSeconds; }
}
