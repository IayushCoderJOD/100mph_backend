package in.hundredmph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import in.hundredmph.api.domain.auth.RefreshTokenRepository;
import in.hundredmph.api.domain.billing.SubscriptionRepository;
import in.hundredmph.api.domain.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real filter chain against a real Mongo. These are the paths
 * where a mistake is a security bug rather than a broken screen, so they are
 * tested end to end rather than through mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final String MEMBER_EMAIL = "memb1@100mph.in";
    private static final String MEMBER_PASSWORD = "memb@123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired LoginAttemptRepository loginAttempts;
    @Autowired SubscriptionRepository subscriptions;

    @BeforeEach
    void clearThrottle() {
        // The throttle is the one piece of state that leaks between tests.
        loginAttempts.deleteAll();
    }

    // ------------------------------------------------------------- sign in

    @Test
    @DisplayName("a seeded member can sign in and gets a usable token pair")
    void signInSucceeds() throws Exception {
        JsonNode body = login(MEMBER_EMAIL, MEMBER_PASSWORD);

        assertThat(body.get("access_token").asText()).isNotBlank();
        assertThat(body.get("refresh_token").asText()).isNotBlank();
        assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(body.get("expires_in").asLong()).isEqualTo(900);

        JsonNode user = body.get("user");
        assertThat(user.get("email").asText()).isEqualTo(MEMBER_EMAIL);
        assertThat(user.get("role").asText()).isEqualTo("member");
        // The wire format is snake_case, matching src/data/types.ts.
        assertThat(user.has("full_name")).isTrue();
        // The hash must never appear in a response, under any name.
        assertThat(user.has("password_hash")).isFalse();
        assertThat(body.toString()).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("a wrong password and an unknown email fail identically")
    void failuresAreIndistinguishable() throws Exception {
        String wrongPassword = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", MEMBER_EMAIL, "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", "nobody@100mph.in", "password", "not-the-password"))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongCode = json.readTree(wrongPassword).at("/error/code").asText();
        String unknownCode = json.readTree(unknownEmail).at("/error/code").asText();

        assertThat(wrongCode).isEqualTo("invalid_credentials");
        assertThat(unknownCode).isEqualTo(wrongCode);
    }

    @Test
    @DisplayName("errors use the one envelope and carry a request id")
    void errorEnvelopeIsConsistent() throws Exception {
        mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", MEMBER_EMAIL, "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.error.code").value("invalid_credentials"))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("repeated failures lock the account with a Retry-After")
    void throttleLocksAfterRepeatedFailures() throws Exception {
        for (int attempt = 0; attempt < 10; attempt++) {
            mvc.perform(post("/v1/auth/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    Map.of("email", MEMBER_EMAIL, "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("email", MEMBER_EMAIL, "password", MEMBER_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("too_many_attempts"));
    }

    // ------------------------------------------------------------- /me

    @Test
    @DisplayName("GET /me is rejected without a token and served with one")
    void meRequiresAToken() throws Exception {
        mvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("token_missing"));

        String accessToken = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("access_token").asText();

        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(MEMBER_EMAIL))
                .andExpect(jsonPath("$.subscription.plan_id").value("plan_annual"))
                .andExpect(jsonPath("$.entitlement.can_train").value(true))
                .andExpect(jsonPath("$.flags.learn_tab_enabled").value(false));
    }

    @Test
    @DisplayName("a garbled token is rejected as token_invalid, not as missing")
    void garbledTokenIsRejected() throws Exception {
        mvc.perform(get("/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("token_invalid"));
    }

    @Test
    @DisplayName("an admin is entitled to the console but not to a program")
    void adminEntitlementIsStaff() throws Exception {
        String accessToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("access_token").asText();

        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("admin"))
                .andExpect(jsonPath("$.entitlement.can_train").value(false))
                .andExpect(jsonPath("$.entitlement.reason").value("staff_account"));
    }

    @Test
    @DisplayName("PATCH /me updates only what was sent")
    void patchMeIsPartial() throws Exception {
        String accessToken = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("access_token").asText();

        mvc.perform(patch("/v1/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("timezone", "Europe/London"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.user.full_name").value("Ayush Tyagi"));

        // Put it back so the seeded record stays as the other tests expect.
        mvc.perform(patch("/v1/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("timezone", "Asia/Kolkata"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------- refresh

    @Test
    @DisplayName("refreshing rotates the pair and invalidates the old token")
    void refreshRotates() throws Exception {
        String firstRefresh = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("refresh_token").asText();

        JsonNode rotated = json.readTree(mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", firstRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String secondRefresh = rotated.get("refresh_token").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // The new access token must actually work.
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + rotated.get("access_token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("presenting a rotated-away token burns the whole family")
    void refreshReuseRevokesFamily() throws Exception {
        String firstRefresh = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("refresh_token").asText();

        String secondRefresh = json.readTree(mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", firstRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("refresh_token").asText();

        // The stolen copy comes back.
        mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", firstRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_reused"));

        // …and the legitimate client's current token is revoked along with it.
        mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", secondRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_reused"));
    }

    @Test
    @DisplayName("logging out makes the refresh token unusable")
    void logoutRevokes() throws Exception {
        String refreshToken = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("refresh_token").asText();

        mvc.perform(post("/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", refreshToken))))
                .andExpect(status().isNoContent());

        mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_reused"));
    }

    // ------------------------------------------------------------- recovery

    @Test
    @DisplayName("forgot-password answers 202 for a stranger and a member alike")
    void forgotPasswordDoesNotEnumerate() throws Exception {
        mvc.perform(post("/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", MEMBER_EMAIL))))
                .andExpect(status().isAccepted());

        mvc.perform(post("/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nobody@100mph.in"))))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("a bogus reset token is refused")
    void resetRejectsBogusToken() throws Exception {
        mvc.perform(post("/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("token", "made-up", "password", "a-good-password"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("reset_token_invalid"));
    }

    // ------------------------------------------------------------- admin

    @Test
    @DisplayName("the admin tree is closed to members and open to admins")
    void adminRoutesAreRoleGated() throws Exception {
        String memberToken = login(MEMBER_EMAIL, MEMBER_PASSWORD).get("access_token").asText();
        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("forbidden"));

        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("access_token").asText();
        mvc.perform(get("/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists());
    }

    @Test
    @DisplayName("an admin-created account is invited, then signs in and becomes active")
    void createdUserActivatesOnFirstSignIn() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("access_token").asText();
        String email = "created-" + System.currentTimeMillis() + "@100mph.in";

        try {
            mvc.perform(post("/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "full_name", "New Client",
                                    "email", email,
                                    "password", "temp-password-1",
                                    "program_id", "prog_lower_back",
                                    "role", "member"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("invited"))
                    .andExpect(jsonPath("$.active_program_id").value("prog_lower_back"));

            JsonNode signedIn = login(email, "temp-password-1");
            assertThat(signedIn.at("/user/status").asText()).isEqualTo("active");
        } finally {
            users.findByEmail(email).ifPresent(users::delete);
        }
    }

    @Test
    @DisplayName("a duplicate email is a 409, not a second account")
    void duplicateEmailConflicts() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("access_token").asText();

        mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "full_name", "Duplicate Person",
                                "email", MEMBER_EMAIL,
                                "password", "temp-password-1",
                                "program_id", "prog_lower_back",
                                "role", "member"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_already_exists"));
    }

    @Test
    @DisplayName("suspending an account ends its sessions and blocks sign-in")
    void suspensionEndsAccess() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("access_token").asText();
        String email = "suspend-" + System.currentTimeMillis() + "@100mph.in";

        try {
            String created = mvc.perform(post("/v1/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "full_name", "Soon Suspended",
                                    "email", email,
                                    "password", "temp-password-1",
                                    "program_id", "prog_lower_back",
                                    "role", "member"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            String userId = json.readTree(created).get("id").asText();
            String refreshToken = login(email, "temp-password-1").get("refresh_token").asText();

            mvc.perform(patch("/v1/admin/users/" + userId + "/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("status", "suspended"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("suspended"));

            // The open session is gone…
            mvc.perform(post("/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("refresh_token", refreshToken))))
                    .andExpect(status().isUnauthorized());

            // …and they cannot start a new one.
            loginAttempts.deleteAll();
            mvc.perform(post("/v1/auth/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(
                                    Map.of("email", email, "password", "temp-password-1"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("account_suspended"));
        } finally {
            users.findByEmail(email).ifPresent(user -> {
                refreshTokens.deleteAll(refreshTokens.findByUserId(user.getId()));
                users.delete(user);
            });
        }
    }

    // ------------------------------------------------------------- public

    @Test
    @DisplayName("health and the program list need no token")
    void publicRoutesAreOpen() throws Exception {
        mvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        mvc.perform(get("/v1/programs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").exists());
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email,
                                "password", password,
                                "device_id", "test-device",
                                "timezone", "Asia/Kolkata"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
}
