package in.hundredmph.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DeploymentGuardTest {

    @Test
    @DisplayName("a Mongo on this machine counts as local, anything else does not")
    void recognisesLocalDatabases() {
        assertThat(DeploymentGuard.isLocal("mongodb://localhost:27017/hundredmph")).isTrue();
        assertThat(DeploymentGuard.isLocal("mongodb://127.0.0.1/hundredmph")).isTrue();
        assertThat(DeploymentGuard.isLocal("")).isTrue();
        assertThat(DeploymentGuard.isLocal("mongodb+srv://user:pw@cluster0.abcd.mongodb.net/db")).isFalse();
        assertThat(DeploymentGuard.isLocal("mongodb://db.internal:27017/hundredmph")).isFalse();
    }

    @Test
    @DisplayName("a remote database with the dev defaults refuses to boot")
    void refusesDevDefaultsRemotely() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.data.mongodb.uri", "mongodb+srv://u:p@cluster0.abcd.mongodb.net/db")
                .withProperty("app.jwt.secret", DeploymentGuard.DEV_JWT_SECRET)
                .withProperty("app.seed.enabled", "true")
                .withProperty("app.cors.allow-local-dev-origins", "true");

        assertThatThrownBy(() -> new DeploymentGuard(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("demo seeding");
    }

    @Test
    @DisplayName("a remote database with production settings boots")
    void acceptsProductionSettings() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.data.mongodb.uri", "mongodb+srv://u:p@cluster0.abcd.mongodb.net/db")
                .withProperty("app.jwt.secret", "a-real-secret-that-is-at-least-32-bytes-long")
                .withProperty("app.seed.enabled", "false")
                .withProperty("app.cors.allow-local-dev-origins", "false")
                .withProperty("app.cors.allowed-origins", "https://app.100mph.in");

        new DeploymentGuard(env);
    }
}
