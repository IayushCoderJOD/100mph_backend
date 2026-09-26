package in.hundredmph.api.config;

import com.mongodb.ConnectionString;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot a deployment that is still wearing its development settings.
 *
 * <p>Every unsafe default in application.yml exists so a laptop can run the API
 * with no configuration: a JWT key that is committed to the repository, demo
 * accounts with published passwords, CORS open to the whole LAN. The prod
 * profile turns them all off — but only if someone remembers to activate it,
 * and forgetting would put a well-known admin password on the internet.
 *
 * <p>So the check keys off something a deployment cannot hide: the database.
 * Pointed at anything other than a Mongo on this machine, those defaults are a
 * mistake, and the process stops with a message saying which one.
 */
@Component
public class DeploymentGuard {

    private static final Logger log = LoggerFactory.getLogger(DeploymentGuard.class);

    /** The fallback in application.yml. Anything signed with it is forgeable. */
    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-before-you-ship-0123456789";

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    public DeploymentGuard(Environment environment) {
        String uri = environment.getProperty("spring.data.mongodb.uri", "");
        if (isLocal(uri)) {
            return;
        }

        List<String> problems = new ArrayList<>();
        if (DEV_JWT_SECRET.equals(environment.getProperty("app.jwt.secret"))) {
            problems.add("JWT_SECRET is not set, so tokens are signed with the key committed to the repository");
        }
        if (environment.getProperty("app.seed.enabled", Boolean.class, false)) {
            problems.add("demo seeding is on, which creates admin@100mph.in with a published password");
        }
        if (environment.getProperty("app.cors.allow-local-dev-origins", Boolean.class, false)) {
            problems.add("CORS still accepts any LAN origin");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start against a non-local database with development settings: "
                            + String.join("; ", problems)
                            + ". Set SPRING_PROFILES_ACTIVE=prod, JWT_SECRET (32+ bytes) and CORS_ALLOWED_ORIGINS.");
        }

        if (environment.getProperty("app.cors.allowed-origins", "").isBlank()) {
            // Not fatal: the phone apps send no Origin and do not need it. The
            // web build does, and would fail every request without it.
            log.warn("CORS_ALLOWED_ORIGINS is empty; the web app will not be able to call this API");
        }
    }

    static boolean isLocal(String uri) {
        if (uri == null || uri.isBlank()) {
            return true;
        }
        try {
            return new ConnectionString(uri).getHosts().stream()
                    .map(DeploymentGuard::hostOf)
                    .allMatch(LOCAL_HOSTS::contains);
        } catch (IllegalArgumentException ex) {
            // An unparseable URI will fail the Mongo client on its own; this is
            // not the place to report it.
            return true;
        }
    }

    private static String hostOf(String hostAndPort) {
        if (hostAndPort.startsWith("[")) {
            int end = hostAndPort.indexOf(']');
            return end > 0 ? hostAndPort.substring(0, end + 1) : hostAndPort;
        }
        int colon = hostAndPort.lastIndexOf(':');
        return (colon > 0 ? hostAndPort.substring(0, colon) : hostAndPort).toLowerCase();
    }
}
