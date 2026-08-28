package in.hundredmph.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Access tokens are short and refresh tokens are long — see README §7.5. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTtl,
        Duration refreshTtl) {
}
