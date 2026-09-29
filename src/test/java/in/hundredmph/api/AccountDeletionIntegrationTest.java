package in.hundredmph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.domain.assignment.AssignedExercise;
import in.hundredmph.api.domain.auth.LoginAttempt;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import in.hundredmph.api.domain.auth.PasswordReset;
import in.hundredmph.api.domain.auth.RefreshToken;
import in.hundredmph.api.domain.billing.Subscription;
import in.hundredmph.api.domain.checkin.CheckIn;
import in.hundredmph.api.domain.plan.WeeklyPlan;
import in.hundredmph.api.domain.progression.UserProgression;
import in.hundredmph.api.domain.session.SessionLog;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import in.hundredmph.api.seed.DataSeeder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deleting an account: by the member from their own Settings, or by an admin
 * from the client's page. Every test makes its own people, because the seeded
 * accounts are what the other suites sign in as.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final String ADMIN_ID = DataSeeder.seedUserId(ADMIN_EMAIL);
    private static final String PASSWORD = "delete-me-please";

    /** Everything keyed on the person, which must all be gone afterwards. */
    private static final List<Class<?>> OWNED = List.of(
            RefreshToken.class, PasswordReset.class, CheckIn.class, SessionLog.class,
            WeeklyPlan.class, AssignedExercise.class, UserProgression.class, Subscription.class);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired MongoTemplate mongo;
    @Autowired LoginAttemptRepository loginAttempts;

    @BeforeEach
    void reset() {
        loginAttempts.deleteAll();
    }

    // --------------------------------------------------------------- member

    @Test
    @DisplayName("a member deletes their own account, and nothing about them is left")
    void memberDeletesOwnAccount() throws Exception {
        String admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        Person member = createPerson(admin, "member");
        giveHistory(admin, member);

        // The wrong password changes nothing — and is not a 401, which would sign the app out.
        mvc.perform(delete("/v1/me")
                        .header("Authorization", "Bearer " + member.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", "not-my-password"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("current_password_incorrect"));
        assertThat(users.existsById(member.id)).isTrue();
        assertThat(rowsFor(member.id)).isPositive();

        mvc.perform(delete("/v1/me")
                        .header("Authorization", "Bearer " + member.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        assertGone(member);

        // The old sign-in is dead: no refresh, no new login, and a write with the
        // still-unexpired access token cannot bring a row back.
        mvc.perform(post("/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refresh_token", member.refresh))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", member.email, "password", PASSWORD, "timezone", "Asia/Kolkata"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/check-ins/" + today())
                        .header("Authorization", "Bearer " + member.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("checked_in", true, "pain_score", 3))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        assertThat(rowsFor(member.id)).isZero();
    }

    // ---------------------------------------------------------------- admin

    @Test
    @DisplayName("an admin deletes a client, who then disappears from the roster")
    void adminDeletesClient() throws Exception {
        String admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        Person client = createPerson(admin, "member");
        giveHistory(admin, client);

        mvc.perform(delete("/v1/admin/users/" + client.id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertGone(client);
        mvc.perform(get("/v1/admin/clients/" + client.id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        String roster = mvc.perform(get("/v1/admin/clients").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(roster).doesNotContain(client.id);

        // Twice is a 404, not a crash.
        mvc.perform(delete("/v1/admin/users/" + client.id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an admin cannot delete themselves from the back office")
    void adminCannotDeleteSelfFromBackOffice() throws Exception {
        String admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(delete("/v1/admin/users/" + ADMIN_ID).header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        assertThat(users.existsById(ADMIN_ID)).isTrue();
    }

    @Test
    @DisplayName("a member cannot delete anyone else")
    void memberCannotDeleteOthers() throws Exception {
        String admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        Person member = createPerson(admin, "member");
        Person other = createPerson(admin, "member");

        mvc.perform(delete("/v1/admin/users/" + other.id).header("Authorization", "Bearer " + member.access))
                .andExpect(status().isForbidden());
        assertThat(users.existsById(other.id)).isTrue();

        deleteAsAdmin(admin, member.id);
        deleteAsAdmin(admin, other.id);
    }

    @Test
    @DisplayName("the practice's last admin cannot delete their account; with another admin they can")
    void lastAdminIsKept() throws Exception {
        String admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        Person second = createPerson(admin, "admin");

        // Leave the new admin as the only one who can sign in.
        Map<String, UserStatus> before = new LinkedHashMap<>();
        for (User other : users.findByRole(UserRole.ADMIN)) {
            if (other.getId().equals(second.id)) continue;
            before.put(other.getId(), other.getStatus());
            other.setStatus(UserStatus.SUSPENDED);
            users.save(other);
        }
        try {
            mvc.perform(delete("/v1/me")
                            .header("Authorization", "Bearer " + second.access)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("password", PASSWORD))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("last_admin"));
            assertThat(users.existsById(second.id)).isTrue();
        } finally {
            before.forEach((id, status) -> users.findById(id).ifPresent(user -> {
                user.setStatus(status);
                users.save(user);
            }));
        }

        mvc.perform(delete("/v1/me")
                        .header("Authorization", "Bearer " + second.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());
        assertGone(second);
        assertThat(users.existsById(ADMIN_ID)).isTrue();
    }

    // -------------------------------------------------------------- helpers

    private record Person(String id, String email, String access, String refresh) {}

    private Person createPerson(String adminToken, String role) throws Exception {
        String email = "delete-" + UUID.randomUUID() + "@test.local";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("full_name", "Deletion Test");
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        if (role.equals("member")) body.put("program_id", "prog_lower_back");
        String created = mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode tokens = login(email, PASSWORD);
        return new Person(json.readTree(created).get("id").asText(), email,
                tokens.get("access_token").asText(), tokens.get("refresh_token").asText());
    }

    /** A week, a prescription, a session, a check-in with a note, and a failed sign-in. */
    private void giveHistory(String adminToken, Person member) throws Exception {
        mvc.perform(put("/v1/admin/clients/" + member.id + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", Map.of("monday", List.of(
                                Map.of("exercise_id", "ex_glute_bridge", "prescription", "3 x 12 reps")))))))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/admin/clients/" + member.id + "/assigned-exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("exercise_id", "ex_heel_slide", "prescription", "2 x 15"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/sessions")
                        .header("Authorization", "Bearer " + member.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "id", UUID.randomUUID().toString(), "local_date", today().toString(), "source", "guided",
                                "exercises", List.of(Map.of("exercise_id", "ex_glute_bridge", "completed", true))))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isLessThan(300));
        mvc.perform(put("/v1/check-ins/" + today())
                        .header("Authorization", "Bearer " + member.access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "checked_in", true, "pain_score", 4, "pain_location", "Lower back", "note", "Sore after work"))))
                .andExpect(status().isOk());
        mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", member.email, "password", "wrong-password", "timezone", "Asia/Kolkata"))))
                .andExpect(status().isUnauthorized());

        for (Class<?> type : List.of(CheckIn.class, SessionLog.class, WeeklyPlan.class, AssignedExercise.class, RefreshToken.class)) {
            assertThat(mongo.count(ownedBy(member.id), type)).as(type.getSimpleName()).isPositive();
        }
        assertThat(mongo.count(Query.query(Criteria.where("email").is(member.email)), LoginAttempt.class)).isPositive();
    }

    private void assertGone(Person person) {
        assertThat(users.existsById(person.id)).isFalse();
        for (Class<?> type : OWNED) {
            assertThat(mongo.count(ownedBy(person.id), type)).as(type.getSimpleName()).isZero();
        }
        assertThat(mongo.count(Query.query(Criteria.where("email").is(person.email)), LoginAttempt.class)).isZero();
    }

    private long rowsFor(String userId) {
        return OWNED.stream().mapToLong(type -> mongo.count(ownedBy(userId), type)).sum();
    }

    private static Query ownedBy(String userId) {
        return Query.query(Criteria.where("userId").is(userId));
    }

    private void deleteAsAdmin(String adminToken, String userId) throws Exception {
        mvc.perform(delete("/v1/admin/users/" + userId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Kolkata"));
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", password, "timezone", "Asia/Kolkata"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private String accessToken(String email, String password) throws Exception {
        return login(email, password).get("access_token").asText();
    }
}
