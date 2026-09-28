package in.hundredmph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import in.hundredmph.api.domain.auth.RefreshTokenRepository;
import in.hundredmph.api.domain.exercise.ExerciseDocument;
import in.hundredmph.api.domain.exercise.ExerciseRepository;
import in.hundredmph.api.domain.plan.WeeklyPlanRepository;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.exercise.ExerciseLibrary;
import in.hundredmph.api.media.MediaProperties;
import in.hundredmph.api.media.MediaStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The admin's exercise library, end to end through the real filter chain and
 * a real Mongo. Only the bucket is simulated: signing is covered by
 * MediaStorageTest, and what matters here is what the API does with the answer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseLibraryIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final String MEMBER_EMAIL = "memb1@100mph.in";
    private static final String MEMBER_PASSWORD = "memb@123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExerciseLibrary library;
    @Autowired ExerciseRepository exercises;
    @Autowired ContentCatalogue catalogue;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired WeeklyPlanRepository plans;
    @Autowired LoginAttemptRepository loginAttempts;

    @MockitoBean MediaStorage media;

    private final List<String> createdExercises = new ArrayList<>();
    private final List<String> createdEmails = new ArrayList<>();

    @BeforeEach
    void bucket() {
        loginAttempts.deleteAll();
        when(media.isConfigured()).thenReturn(true);
        when(media.properties()).thenReturn(new MediaProperties("acct", "key", "secret", "100mph-media", null,
                "demos/uploads", 150, 2048, Duration.ofMinutes(15)));
        when(media.presignUpload(anyString(), anyString(), anyLong())).thenAnswer(call ->
                new MediaStorage.PresignedUpload("https://acct.r2.cloudflarestorage.com/100mph-media/"
                        + call.getArgument(0) + "?X-Amz-Signature=test",
                        call.getArgument(0), Map.of("Content-Type", call.getArgument(1)),
                        Instant.now().plusSeconds(900)));
        when(media.head(anyString())).thenReturn(Optional.empty());
    }

    @AfterEach
    void cleanUp() {
        createdEmails.forEach(email -> users.findByEmail(email).ifPresent(user -> {
            plans.findByUserId(user.getId()).ifPresent(plans::delete);
            refreshTokens.deleteAll(refreshTokens.findByUserId(user.getId()));
            users.delete(user);
        }));
        exercises.deleteAllById(createdExercises);
        library.reload();
    }

    // ------------------------------------------------------------- reading

    @Test
    @DisplayName("the picker gets filmed movements only; the admin also sees the drafts")
    void publishedLibraryIsFilmedOnly() throws Exception {
        JsonNode published = getJson("/v1/exercises", token(MEMBER_EMAIL, MEMBER_PASSWORD));
        assertThat(published.size()).isGreaterThanOrEqualTo(33);
        published.forEach(exercise -> {
            assertThat(exercise.get("video_url").asText()).isNotBlank();
            assertThat(exercise.get("hidden").asBoolean()).isFalse();
        });
        assertThat(published.findValuesAsText("id")).contains("ex_glute_bridge").doesNotContain("ex_bird_dog");
        // Seeded with the sets a coach starts from.
        assertThat(published.findValuesAsText("suggested_sets")).isNotEmpty();

        JsonNode all = getJson("/v1/admin/exercises", token(ADMIN_EMAIL, ADMIN_PASSWORD));
        assertThat(all.findValuesAsText("id")).contains("ex_bird_dog", "ex_glute_bridge");
    }

    @Test
    @DisplayName("members cannot manage the library")
    void libraryManagementIsAdminOnly() throws Exception {
        String member = token(MEMBER_EMAIL, MEMBER_PASSWORD);
        mvc.perform(get("/v1/admin/exercises").header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v1/admin/exercises").header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sneaky", "category", "core"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------ the whole loop

    @Test
    @DisplayName("an admin adds a movement, films it, prescribes it, and the member sees it")
    void adminAddsFilmsAndPrescribes() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD);

        // 1. Text first. Not in the picker yet: there is nothing to watch.
        JsonNode created = send(post("/v1/admin/exercises"), admin, Map.of(
                "name", "Copenhagen Plank " + System.nanoTime(),
                "category", "hips_glutes",
                "focus", "Adductors",
                "instructions", "Side plank with the top leg on a bench.",
                "suggested_sets", "3 x 20s each side"), 201);
        String id = created.get("id").asText();
        createdExercises.add(id);
        assertThat(id).startsWith("ex_copenhagen_plank");
        assertThat(created.get("video_url").isNull()).isTrue();
        assertThat(getJson("/v1/exercises", admin).findValuesAsText("id")).doesNotContain(id);

        // 2. Somewhere to put the video.
        JsonNode upload = send(post("/v1/admin/exercises/uploads"), admin, Map.of(
                "kind", "video", "content_type", "video/mp4",
                "size_bytes", 12_000_000, "file_name", "Copenhagen plank (final).MP4"), 200);
        String key = upload.get("key").asText();
        assertThat(key).matches("demos/uploads/copenhagen-plank-final-[0-9a-f]{8}\\.mp4");
        assertThat(upload.get("method").asText()).isEqualTo("PUT");
        assertThat(upload.get("upload_url").asText()).contains(key);

        // 3. The browser PUTs it; the bucket now has it; the admin attaches it.
        when(media.head(eq(key))).thenReturn(Optional.of(new MediaStorage.StoredObject(key, 12_000_000L, "video/mp4")));
        String poster = "demos/uploads/copenhagen-plank-final-0000aaaa-poster.jpg";
        when(media.head(eq(poster))).thenReturn(Optional.of(new MediaStorage.StoredObject(poster, 80_000L, "image/jpeg")));
        JsonNode filmed = send(patch("/v1/admin/exercises/" + id), admin,
                Map.of("video_key", key, "thumbnail_key", poster), 200);
        assertThat(filmed.get("video_url").asText()).isEqualTo(key);
        assertThat(filmed.get("thumbnail_url").asText()).isEqualTo(poster);
        assertThat(getJson("/v1/exercises", admin).findValuesAsText("id")).contains(id);

        // 3b. Replaced with a new take whose poster could not be captured: the
        // new video goes in, the old poster comes out, and the words stay put.
        String retake = "demos/uploads/copenhagen-plank-retake-1111bbbb.mp4";
        when(media.head(eq(retake))).thenReturn(Optional.of(new MediaStorage.StoredObject(retake, 9_000_000L, "video/mp4")));
        JsonNode replaced = send(patch("/v1/admin/exercises/" + id), admin,
                Map.of("video_key", retake, "thumbnail_key", ""), 200);
        assertThat(replaced.get("video_url").asText()).isEqualTo(retake);
        assertThat(replaced.get("thumbnail_url").isNull()).isTrue();
        assertThat(replaced.get("name").asText()).isEqualTo(created.get("name").asText());
        assertThat(replaced.get("instructions").asText()).isEqualTo("Side plank with the top leg on a bench.");
        key = retake;

        // 4. On a client's week, and the client sees it.
        String email = "library-" + System.nanoTime() + "@100mph.in";
        createdEmails.add(email);
        String memberId = send(post("/v1/admin/users"), admin, Map.of(
                "full_name", "Library Client", "email", email,
                "password", "temp-password-1", "role", "member"), 201).get("id").asText();
        send(put("/v1/admin/clients/" + memberId + "/plan"), admin, Map.of("days", Map.of(
                "monday", List.of(Map.of("exercise_id", id, "prescription", "3 x 20s each side")))), 200);

        JsonNode week = getJson("/v1/plan", token(email, "temp-password-1"));
        assertThat(week.at("/days/monday/0/exercise/name").asText()).startsWith("Copenhagen Plank");
        assertThat(week.at("/days/monday/0/exercise/video_url").asText()).isEqualTo(key);

        // 5. Video removed: back to a draft, out of the picker, still on the week.
        JsonNode unfilmed = send(patch("/v1/admin/exercises/" + id), admin, Map.of("video_key", ""), 200);
        assertThat(unfilmed.get("video_url").isNull()).isTrue();
        assertThat(unfilmed.get("thumbnail_url").isNull()).isTrue();
        assertThat(getJson("/v1/exercises", admin).findValuesAsText("id")).doesNotContain(id);
        JsonNode drafted = getJson("/v1/plan", token(email, "temp-password-1"));
        assertThat(drafted.at("/days/monday/0/exercise/name").asText()).startsWith("Copenhagen Plank");
        assertThat(drafted.at("/days/monday/0/exercise/video_url").isNull()).isTrue();
        // …and filming it again puts it straight back.
        send(patch("/v1/admin/exercises/" + id), admin, Map.of("video_key", key), 200);
        assertThat(getJson("/v1/exercises", admin).findValuesAsText("id")).contains(id);

        // 6. Retired: gone from the picker, still on the week that uses it.
        send(patch("/v1/admin/exercises/" + id), admin, Map.of("hidden", true), 200);
        assertThat(getJson("/v1/exercises", admin).findValuesAsText("id")).doesNotContain(id);
        assertThat(getJson("/v1/plan", token(email, "temp-password-1"))
                .at("/days/monday/0/exercise/name").asText()).startsWith("Copenhagen Plank");
        mvc.perform(get("/v1/exercises/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------ refusals

    @Test
    @DisplayName("an upload that never landed cannot be attached")
    void missingUploadIsRefused() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD);
        String id = send(post("/v1/admin/exercises"), admin,
                Map.of("name", "Ghost Upload " + System.nanoTime(), "category", "core"), 201).get("id").asText();
        createdExercises.add(id);

        mvc.perform(patch("/v1/admin/exercises/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("video_key", "demos/uploads/never-landed-0000.mp4"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("upload_not_found"));

        // A key that tries to leave the bucket's namespace never reaches it.
        mvc.perform(patch("/v1/admin/exercises/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("video_key", "../../etc/passwd"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the wrong kind or size of file is refused before a URL is signed")
    void wrongFilesAreRefused() throws Exception {
        String admin = token(ADMIN_EMAIL, ADMIN_PASSWORD);
        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("kind", "video", "content_type", "application/pdf", "size_bytes", 1000),
                Map.of("kind", "video", "content_type", "video/mp4", "size_bytes", 400L * 1024 * 1024),
                Map.of("kind", "poster", "content_type", "image/jpeg", "size_bytes", 5L * 1024 * 1024))) {
            mvc.perform(post("/v1/admin/exercises/uploads").header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(bad)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("unsupported_media"));
        }

        mvc.perform(post("/v1/admin/exercises").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "No Such Shelf", "category", "juggling"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("without bucket credentials, uploads say so instead of failing mysteriously")
    void uploadsNeedABucket() throws Exception {
        when(media.isConfigured()).thenReturn(false);
        mvc.perform(post("/v1/admin/exercises/uploads")
                        .header("Authorization", "Bearer " + token(ADMIN_EMAIL, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "kind", "video", "content_type", "video/mp4", "size_bytes", 1000))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("media_not_configured"));
    }

    // ------------------------------------------------- catalogue vs admin

    @Test
    @DisplayName("an admin's edit survives a deploy; an untouched movement follows the catalogue")
    void adminEditsWinOverTheCatalogue() throws Exception {
        String id = "ex_supine_hamstring_stretch";
        Exercise authored = catalogue.authoredExercises().stream()
                .filter(exercise -> exercise.id().equals(id)).findFirst().orElseThrow();

        try {
            // Untouched and stale: the next sync puts the catalogue's copy back.
            ExerciseDocument stale = exercises.findById(id).orElseThrow();
            stale.setPurpose("An out-of-date sentence.");
            exercises.save(stale);
            library.syncFromCatalogue();
            assertThat(exercises.findById(id).orElseThrow().getPurpose()).isEqualTo(authored.purpose());

            // Edited in the app: the sync leaves it alone from then on.
            send(patch("/v1/admin/exercises/" + id), token(ADMIN_EMAIL, ADMIN_PASSWORD),
                    Map.of("purpose", "The coach's own words."), 200);
            library.syncFromCatalogue();
            assertThat(exercises.findById(id).orElseThrow().getPurpose()).isEqualTo("The coach's own words.");
            assertThat(library.find(id).orElseThrow().purpose()).isEqualTo("The coach's own words.");
        } finally {
            ExerciseDocument restored = exercises.findById(id).orElseThrow();
            restored.applyCatalogue(authored, Instant.now());
            restored.setEditedByAdmin(false);
            restored.setUpdatedBy(null);
            exercises.save(restored);
            library.reload();
        }
    }

    // ------------------------------------------------------------- helpers

    private String token(String email, String password) throws Exception {
        String body = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("access_token").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        String body = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode send(MockHttpServletRequestBuilder request,
                          String token, Object payload, int expectedStatus) throws Exception {
        String body = mvc.perform(request.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return body.isEmpty() ? null : json.readTree(body);
    }
}
