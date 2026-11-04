package in.hundredmph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.account.ProfileEditor;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.seed.DataSeeder;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Changing someone's details after the account exists: by the person from Edit
 * Details, or by a coach from the client's page. Every test makes its own
 * people, because the seeded accounts are what the other suites sign in as.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileEditIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final String ADMIN_ID = DataSeeder.seedUserId(ADMIN_EMAIL);
    private static final String PASSWORD = "edit-me-please";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ProfileEditor profiles;
    @Autowired LoginAttemptRepository loginAttempts;

    private String admin;

    @BeforeEach
    void signIn() throws Exception {
        loginAttempts.deleteAll();
        admin = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // ---------------------------------------------------------- your own

    @Test
    @DisplayName("a member changes their own name, phone and numbers")
    void memberEditsOwnDetails() throws Exception {
        Person member = createPerson("member");
        String phone = randomPhone();

        saveOwn(member, profile("Rhea Menon", "+91 " + phone, "1995-03-14", 165, 58.5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.full_name").value("Rhea Menon"))
                .andExpect(jsonPath("$.user.phone").value("+91" + phone))
                .andExpect(jsonPath("$.user.date_of_birth").value("1995-03-14"))
                .andExpect(jsonPath("$.user.height_cm").value(165))
                .andExpect(jsonPath("$.user.weight_kg").value(58.5));

        // Read back rather than trusting the response.
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + member.access))
                .andExpect(jsonPath("$.user.full_name").value("Rhea Menon"));
    }

    @Test
    @DisplayName("a blank optional field clears it, which PATCH /me could not do")
    void clearingTakesTheDetailBackOut() throws Exception {
        Person member = createPerson("member");
        saveOwn(member, profile("Rhea Menon", randomPhone(), "1995-03-14", 165, 58.5)).andExpect(status().isOk());

        saveOwn(member, profile("Rhea Menon", "", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.phone").isEmpty())
                .andExpect(jsonPath("$.user.date_of_birth").isEmpty())
                .andExpect(jsonPath("$.user.height_cm").isEmpty())
                .andExpect(jsonPath("$.user.weight_kg").isEmpty());
    }

    @Test
    @DisplayName("your own form cannot move your sign-in email")
    void ownFormIgnoresEmail() throws Exception {
        Person member = createPerson("member");
        Map<String, Object> body = profile("Rhea Menon", null, null, null, null);
        body.put("email", "elsewhere-" + UUID.randomUUID() + "@test.local");

        saveOwn(member, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(member.email));
    }

    @Test
    @DisplayName("an admin changes their own name the same way")
    void adminEditsOwnName() throws Exception {
        Person other = createPerson("admin");

        saveOwn(other, profile("Coach Asha", null, null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.full_name").value("Coach Asha"))
                .andExpect(jsonPath("$.user.role").value("admin"));
    }

    @Test
    @DisplayName("a name has to be a name, and the numbers have to be plausible")
    void rejectsBadValues() throws Exception {
        Person member = createPerson("member");

        saveOwn(member, profile("   ", null, null, null, null)).andExpect(status().isBadRequest());
        saveOwn(member, profile(" a ", null, null, null, null)).andExpect(status().isBadRequest());
        saveOwn(member, profile("Rhea", "12", null, null, null)).andExpect(status().isBadRequest());
        saveOwn(member, profile("Rhea", null, LocalDate.now().plusDays(2).toString(), null, null))
                .andExpect(status().isBadRequest());
        saveOwn(member, profile("Rhea", null, null, 5, null)).andExpect(status().isBadRequest());
        saveOwn(member, profile("Rhea", null, null, null, 900.0)).andExpect(status().isBadRequest());

        assertThat(users.findById(member.id).orElseThrow().getFullName()).isEqualTo("Edit Test");
    }

    // ---------------------------------------------------------- the coach

    @Test
    @DisplayName("an admin corrects a client's details and they keep their session")
    void adminEditsClient() throws Exception {
        Person member = createPerson("member");
        String newEmail = "moved-" + UUID.randomUUID() + "@test.local";
        String phone = randomPhone();

        Map<String, Object> body = profile("Ansh Chauhan", phone, "1990-01-02", 180, 80.0);
        body.put("email", newEmail.toUpperCase());
        saveClient(admin, member.id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.full_name").value("Ansh Chauhan"))
                .andExpect(jsonPath("$.email").value(newEmail))
                .andExpect(jsonPath("$.phone").value(phone))
                .andExpect(jsonPath("$.date_of_birth").value("1990-01-02"))
                .andExpect(jsonPath("$.height_cm").value(180))
                .andExpect(jsonPath("$.weight_kg").value(80.0))
                .andExpect(jsonPath("$.role").value("member"))
                .andExpect(jsonPath("$.status").value("active"));

        // They see it on their own phone, on the session they already had.
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + member.access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.full_name").value("Ansh Chauhan"))
                .andExpect(jsonPath("$.user.email").value(newEmail));

        // The new address signs in; the old one no longer does.
        login(newEmail, PASSWORD);
        mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", member.email, "password", PASSWORD, "timezone", "Asia/Kolkata"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an admin can clear a client's optional details")
    void adminClearsClientDetails() throws Exception {
        Person member = createPerson("member");
        saveOwn(member, profile("Rhea Menon", randomPhone(), "1995-03-14", 165, 58.5)).andExpect(status().isOk());

        Map<String, Object> body = profile("Rhea Menon", "", null, null, null);
        body.put("email", member.email);
        saveClient(admin, member.id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.date_of_birth").isEmpty())
                .andExpect(jsonPath("$.height_cm").isEmpty())
                .andExpect(jsonPath("$.weight_kg").isEmpty());
    }

    @Test
    @DisplayName("only an admin can edit someone else")
    void memberCannotEditAnother() throws Exception {
        Person member = createPerson("member");
        Person other = createPerson("member");
        Map<String, Object> body = profile("Taken Over", null, null, null, null);
        body.put("email", other.email);

        saveClient(member.access, other.id, body).andExpect(status().isForbidden());

        assertThat(users.findById(other.id).orElseThrow().getFullName()).isEqualTo("Edit Test");
    }

    @Test
    @DisplayName("someone else's email or phone cannot be taken, but your own can be kept")
    void uniquenessIsAboutOtherPeople() throws Exception {
        Person member = createPerson("member");
        Person other = createPerson("member");
        String phone = randomPhone();
        saveOwn(other, profile("Other Person", phone, null, null, null)).andExpect(status().isOk());

        Map<String, Object> takesEmail = profile("Edit Test", null, null, null, null);
        takesEmail.put("email", other.email);
        saveClient(admin, member.id, takesEmail)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_already_exists"));

        Map<String, Object> takesPhone = profile("Edit Test", phone, null, null, null);
        takesPhone.put("email", member.email);
        saveClient(admin, member.id, takesPhone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("phone_already_exists"));
        saveOwn(member, profile("Edit Test", phone, null, null, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("phone_already_exists"));

        // Saving your own number again is not a clash with yourself.
        Map<String, Object> unchanged = profile("Renamed Other", phone, null, null, null);
        unchanged.put("email", other.email);
        saveClient(admin, other.id, unchanged).andExpect(status().isOk());
    }

    @Test
    @DisplayName("two people saving the same detail at once: the index decides, and says which")
    void indexRaceNamesTheDetail() throws Exception {
        Person member = createPerson("member");
        Person other = createPerson("member");
        String phone = randomPhone();
        saveOwn(other, profile("Other Person", phone, null, null, null)).andExpect(status().isOk());

        // Past the friendly checks, straight to the unique index.
        User sameEmail = users.findById(member.id).orElseThrow();
        sameEmail.setEmail(other.email);
        assertThatThrownBy(() -> profiles.save(sameEmail))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));

        User samePhone = users.findById(member.id).orElseThrow();
        samePhone.setPhone(phone);
        assertThatThrownBy(() -> profiles.save(samePhone))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PHONE_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("a staff account keeps its email, or the next boot would make a second admin")
    void staffEmailStaysPut() throws Exception {
        Map<String, Object> moved = profile("100mph Admin", null, null, null, null);
        moved.put("email", "moved-" + UUID.randomUUID() + "@test.local");
        saveClient(admin, ADMIN_ID, moved).andExpect(status().isForbidden());

        assertThat(users.findById(ADMIN_ID).orElseThrow().getEmail()).isEqualTo(ADMIN_EMAIL);
    }

    @Test
    @DisplayName("an unknown client is a 404, not a new record")
    void unknownClient() throws Exception {
        Map<String, Object> body = profile("Nobody Here", null, null, null, null);
        body.put("email", "nobody-" + UUID.randomUUID() + "@test.local");

        saveClient(admin, UUID.randomUUID().toString(), body).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------- helpers

    private record Person(String id, String email, String access) {}

    private Person createPerson(String role) throws Exception {
        String email = "edit-" + UUID.randomUUID() + "@test.local";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("full_name", "Edit Test");
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        String created = mvc.perform(post("/v1/admin/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Person(json.readTree(created).get("id").asText(), email, accessToken(email, PASSWORD));
    }

    /** Nulls stay in the map: an explicit null is what clears a field. */
    private static Map<String, Object> profile(String fullName, String phone, String dateOfBirth,
                                               Integer heightCm, Double weightKg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("full_name", fullName);
        body.put("phone", phone);
        body.put("date_of_birth", dateOfBirth);
        body.put("height_cm", heightCm);
        body.put("weight_kg", weightKg);
        return body;
    }

    private ResultActions saveOwn(Person person, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/v1/me/profile")
                .header("Authorization", "Bearer " + person.access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private ResultActions saveClient(String token, String userId, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/v1/admin/users/" + userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    /** The test database outlives a run, so a fixed number would clash with the last one. */
    private static String randomPhone() {
        return "9" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
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
