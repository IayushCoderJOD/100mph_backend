package in.hundredmph.api.seed;

import in.hundredmph.api.config.SeedProperties;
import in.hundredmph.api.domain.billing.Plan;
import in.hundredmph.api.domain.billing.PlanRepository;
import in.hundredmph.api.domain.billing.Subscription;
import in.hundredmph.api.domain.billing.SubscriptionRepository;
import in.hundredmph.api.domain.billing.SubscriptionStatus;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the collections from the app's src/data/mock.ts so the client can be
 * pointed at this API and behave exactly as it does today — same ids, same demo
 * logins, same programs.
 *
 * <p>Two safeguards, because README §8.1 calls seed data a corruption trap:
 * it only runs when {@code app.seed.enabled} is set, and it only writes into an
 * empty collection. It will never overwrite a record someone else created.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String MEMBER_EMAIL = "memb1@100mph.in";
    private static final String PROGRAM_LOWER_BACK = "prog_lower_back";

    /**
     * Seeded users get real UUIDs, like every user the API mints at runtime —
     * an id's shape should never reveal whether a row came from the seeder.
     *
     * <p>The UUID is derived from the email rather than random so that a
     * re-seed of an empty database reproduces the same ids: demo subscriptions,
     * schedules and check-ins keep pointing at the right person, and the
     * integration tests can name a seeded user without hard-coding a literal.
     */
    public static String seedUserId(String email) {
        return UUID.nameUUIDFromBytes(("100mph:user:" + email).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private final PlanRepository plans;
    private final UserRepository users;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;
    private final SeedProperties seedProperties;

    public DataSeeder(PlanRepository plans,
                      UserRepository users,
                      SubscriptionRepository subscriptions,
                      PasswordEncoder passwordEncoder,
                      SeedProperties seedProperties) {
        this.plans = plans;
        this.users = users;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
        this.seedProperties = seedProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }

        seedPlans();
        seedUsers();

        log.info("Seed complete: {} plans, {} users", plans.count(), users.count());
    }

    private void seedPlans() {
        if (plans.count() > 0) {
            return;
        }
        plans.saveAll(List.of(
                new Plan("plan_monthly", "Monthly",
                        "Full access, billed every month. Cancel any time.", 30, "₹1,499 / month"),
                new Plan("plan_quarterly", "Quarterly",
                        "Three months in one go — long enough to feel the change.", 90,
                        "₹3,999 / 3 months"),
                new Plan("plan_annual", "Annual",
                        "The full Long Game, at the lowest monthly rate.", 365, "₹12,999 / year")));
    }

    private void seedUsers() {
        if (users.count() > 0) {
            return;
        }

        // The two demo accounts from mock.demoLogins, so the login screen's
        // dev shortcuts work against a real server unchanged.
        User admin = user(seedUserId(ADMIN_EMAIL), "Dr. Ayush Nair", ADMIN_EMAIL, "+919000011111",
                UserRole.ADMIN, null, "admin@123",
                LocalDate.parse("2026-01-05"), Instant.parse("2026-01-05T09:00:00Z"));

        User member = user(seedUserId(MEMBER_EMAIL), "Ayush Tyagi", MEMBER_EMAIL, "+919000000000",
                UserRole.MEMBER, PROGRAM_LOWER_BACK, "memb@123",
                LocalDate.parse("2026-08-14"), Instant.parse("2026-08-14T09:00:00Z"));

        User rhea = user(seedUserId("memb2@100mph.in"), "Rhea Menon", "memb2@100mph.in", "+919000022222",
                UserRole.MEMBER, PROGRAM_LOWER_BACK, "memb@123",
                LocalDate.parse("2026-06-02"), Instant.parse("2026-06-02T09:00:00Z"));

        User kabir = user(seedUserId("memb3@100mph.in"), "Kabir Shah", "memb3@100mph.in", "+919000033333",
                UserRole.MEMBER, "prog_knee", "memb@123",
                LocalDate.parse("2026-07-19"), Instant.parse("2026-07-19T09:00:00Z"));

        users.saveAll(List.of(admin, member, rhea, kabir));

        if (subscriptions.count() == 0) {
            subscriptions.saveAll(List.of(
                    subscription("sub_1", seedUserId(MEMBER_EMAIL), "plan_annual",
                            LocalDate.parse("2026-08-14"), LocalDate.parse("2027-08-14")),
                    subscription("sub_2", seedUserId("memb2@100mph.in"), "plan_quarterly",
                            LocalDate.parse("2026-06-02"), LocalDate.parse("2026-09-02")),
                    subscription("sub_3", seedUserId("memb3@100mph.in"), "plan_monthly",
                            LocalDate.parse("2026-07-19"), LocalDate.parse("2026-08-19"))));
        }
    }

    private User user(String id, String fullName, String email, String phone, UserRole role,
                      String programId, String password, LocalDate memberSince, Instant createdAt) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setActiveProgramId(programId);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTimezone("Asia/Kolkata");
        user.setMemberSince(memberSince);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(createdAt);
        return user;
    }

    private Subscription subscription(String id, String userId, String planId,
                                       LocalDate start, LocalDate end) {
        Subscription subscription = new Subscription();
        subscription.setId(id);
        subscription.setUserId(userId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(start);
        subscription.setCurrentPeriodEnd(end);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCreatedAt(start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        subscription.setUpdatedAt(start.atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        return subscription;
    }
}
