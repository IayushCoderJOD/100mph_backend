package in.hundredmph.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** The first admin of a fresh deployment. See {@link in.hundredmph.api.seed.AdminBootstrap}. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminEmail, String adminPassword, String adminName) {
}
