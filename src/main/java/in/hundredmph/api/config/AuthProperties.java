package in.hundredmph.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        int maxFailedLogins,
        Duration failedLoginWindow,
        Duration lockDuration,
        Duration passwordResetTtl,
        int minPasswordLength) {
}
