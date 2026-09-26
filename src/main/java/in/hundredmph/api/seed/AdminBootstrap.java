package in.hundredmph.api.seed;

import in.hundredmph.api.auth.AuthService;
import in.hundredmph.api.config.BootstrapProperties;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin of a fresh deployment.
 *
 * <p>Production runs with seeding off — the demo accounts have published
 * passwords — so an empty database has nobody who can sign in, and every
 * account after the first is created by an admin. Set BOOTSTRAP_ADMIN_EMAIL and
 * BOOTSTRAP_ADMIN_PASSWORD for the first boot; the account is created once, and
 * on every later boot an existing account with that email is left exactly as
 * it is, so the variables can stay set without resetting anyone's password.
 * Change the password from the app afterwards.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    /** Stricter than a member's: this account can see and change everyone. */
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private static final ZoneId PRACTICE_ZONE = ZoneId.of("Asia/Kolkata");

    private final BootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(BootstrapProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = AuthService.normaliseEmail(properties.adminEmail());
        if (email == null || email.isBlank()) {
            return;
        }
        if (users.existsByEmail(email)) {
            log.info("Bootstrap admin {} already exists; leaving it untouched", email);
            return;
        }

        String password = properties.adminPassword();
        if (password == null || password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least "
                    + MIN_ADMIN_PASSWORD_LENGTH + " characters to create the first admin");
        }

        Instant now = Instant.now();
        User admin = new User();
        admin.setId(UUID.randomUUID().toString());
        admin.setFullName(properties.adminName() == null || properties.adminName().isBlank()
                ? "100mph Admin" : properties.adminName().trim());
        admin.setEmail(email);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setTimezone(PRACTICE_ZONE.getId());
        admin.setMemberSince(LocalDate.now(PRACTICE_ZONE));
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        users.save(admin);

        log.info("Bootstrap admin {} created", email);
    }
}
