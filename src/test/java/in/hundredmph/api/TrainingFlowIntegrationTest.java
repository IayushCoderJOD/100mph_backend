package in.hundredmph.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.domain.assignment.AssignedExerciseRepository;
import in.hundredmph.api.domain.auth.LoginAttemptRepository;
import in.hundredmph.api.domain.checkin.CheckInRepository;
import in.hundredmph.api.domain.progression.UserProgressionRepository;
import in.hundredmph.api.domain.plan.WeeklyPlanRepository;
import in.hundredmph.api.domain.session.SessionLogRepository;
import in.hundredmph.api.seed.DataSeeder;
import java.time.LocalDate;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The training loop: content, the week, logging a session, checking in, and
 * what the coach sees afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrainingFlowIntegrationTest {

    private static final String MEMBER_EMAIL = "memb1@100mph.in";
    private static final String MEMBER_PASSWORD = "memb@123";
    private static final String ADMIN_EMAIL = "admin@100mph.in";
    private static final String ADMIN_PASSWORD = "admin@123";
    private static final String MEMBER_ID = DataSeeder.seedUserId(MEMBER_EMAIL);
    private static final String PROGRAM = "prog_lower_back";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired LoginAttemptRepository loginAttempts;
    @Autowired WeeklyPlanRepository plans;
    @Autowired SessionLogRepository sessionLogs;
    @Autowired CheckInRepository checkIns;
    @Autowired AssignedExerciseRepository assignments;
    @Autowired UserProgressionRepository progressions;

    @BeforeEach
    void reset() {
        loginAttempts.deleteAll();
        sessionLogs.deleteAll();
        checkIns.deleteAll();
        plans.deleteAll();
        assignments.deleteAll();
        progressions.deleteAll();
    }

    // ------------------------------------------------------------- content

    @Test
    @DisplayName("the program catalogue is served whole, from the static content file")
    void programContentIsComplete() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        mvc.perform(get("/v1/programs/" + PROGRAM + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.program.slug").value("lower_back"))
                .andExpect(jsonPath("$.session_types[0].id").exists())
                .andExpect(jsonPath("$.exercises[0].instructions").exists())
                .andExpect(jsonPath("$.session_exercises[0].prescription").exists())
                .andExpect(jsonPath("$.progression_levels[0].goal_label").exists())
                .andExpect(jsonPath("$.learn_content[0].title").exists())
                .andExpect(jsonPath("$.default_schedule[0].day_of_week").exists());
    }

    @Test
    @DisplayName("an unknown program is a 422, not an empty payload")
    void unknownProgramIsRejected() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        mvc.perform(get("/v1/programs/prog_nope/content").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("program_not_found"));
    }

    @Test
    @DisplayName("catalogue reads need a token; the program list does not")
    void catalogueIsGated() throws Exception {
        mvc.perform(get("/v1/programs")).andExpect(status().isOk());
        mvc.perform(get("/v1/programs/" + PROGRAM + "/content")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/exercises/ex_heel_slide")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- plan

    @Test
    @DisplayName("a coach writes the week, the member reads it, and rest days come back empty")
    void coachWritesTheWeek() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        String memberToken = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("monday", List.of(
                Map.of("exercise_id", "ex_glute_bridge", "prescription", "3 x 2m holds"),
                Map.of("exercise_id", "ex_tke_seated", "prescription", "3 x 15 reps")));
        days.put("thursday", List.of(
                Map.of("exercise_id", "ex_walking_lunge", "prescription", "2 x 10 steps each side")));

        String body = mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", days))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(MEMBER_ID))
                .andExpect(jsonPath("$.days.monday.length()").value(2))
                .andExpect(jsonPath("$.days.monday[0].sort_order").value(1))
                .andExpect(jsonPath("$.days.monday[1].exercise.name").value("Terminal Knee Extension (Seated)"))
                .andExpect(jsonPath("$.days.tuesday.length()").value(0))
                .andExpect(jsonPath("$.updated_by_name").value("Dr. Ayush Nair"))
                .andReturn().getResponse().getContentAsString();

        // All seven days present, Monday first, so the app never fills gaps.
        assertThat(json.readTree(body).get("days").fieldNames()).toIterable()
                .containsExactly("monday", "tuesday", "wednesday", "thursday",
                        "friday", "saturday", "sunday");

        mvc.perform(get("/v1/plan").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.monday[0].prescription").value("3 x 2m holds"))
                .andExpect(jsonPath("$.days.thursday[0].exercise_id").value("ex_walking_lunge"));

        // The coach sees the same week on the client's page.
        mvc.perform(get("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.monday.length()").value(2));
    }

    @Test
    @DisplayName("a member with no plan yet reads an empty week, not an error")
    void unplannedMemberReadsEmptyWeek() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        mvc.perform(get("/v1/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.monday").isEmpty())
                .andExpect(jsonPath("$.days.sunday").isEmpty())
                .andExpect(jsonPath("$.updated_at").doesNotExist());
    }

    @Test
    @DisplayName("the plan is validated at the door: unknown exercises, repeats and bad days")
    void planIsValidated() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", Map.of("monday", List.of(
                                Map.of("exercise_id", "ex_nope", "prescription", "x")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("exercise_not_found"))
                .andExpect(jsonPath("$.error.details.day").value("monday"));

        mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", Map.of("monday", List.of(
                                Map.of("exercise_id", "ex_heel_slide", "prescription", "x"),
                                Map.of("exercise_id", "ex_heel_slide", "prescription", "y")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));

        mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", Map.of("funday", List.of(
                                Map.of("exercise_id", "ex_heel_slide", "prescription", "x")))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("schedule_invalid_day"));

        // A blank prescription is a bean-validation failure, caught before the service.
        mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", Map.of("monday", List.of(
                                Map.of("exercise_id", "ex_heel_slide", "prescription", "  ")))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("staff have no week — they do not train here")
    void staffHaveNoPlan() throws Exception {
        String token = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/v1/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
    }

    // ------------------------------------------------------------- sessions

    @Test
    @DisplayName("the day's plan is the week the coach wrote, with each exercise inlined")
    void sessionPlanExpandsExercises() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        // Put work on every day so whichever day the test runs is one.
        putFullWeek();

        mvc.perform(get("/v1/sessions/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.local_date").exists())
                .andExpect(jsonPath("$.exercises[0].exercise.name").value("Glute Bridge"))
                .andExpect(jsonPath("$.exercises[0].prescription").value("3 x 2m holds"))
                .andExpect(jsonPath("$.exercises[1].exercise.id").value("ex_heel_slide"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    @DisplayName("a rest day is a valid plan, not an error")
    void restDayReturnsEmptyPlan() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        // No plan written at all: every day is rest, and that is not an error.
        mvc.perform(get("/v1/sessions/plan").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_type").doesNotExist())
                .andExpect(jsonPath("$.exercises").isEmpty());
    }

    @Test
    @DisplayName("logging the same session twice writes one row, not two")
    void sessionLogIsIdempotent() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        putFullWeek();

        String sessionId = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "id", sessionId,
                "source", "guided",
                "duration_min", 42,
                "exercises", java.util.List.of(
                        Map.of("exercise_id", "ex_heel_slide", "completed", true)));

        mvc.perform(post("/v1/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duration_min").value(42));

        // The retry a flaky connection produces.
        mvc.perform(post("/v1/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated());

        assertThat(sessionLogs.countByUserId(MEMBER_ID)).isEqualTo(1);

        mvc.perform(get("/v1/sessions/plan").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("completed dates come back for the week strip")
    void completedDatesAreListed() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        putFullWeek();

        mvc.perform(post("/v1/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "id", UUID.randomUUID().toString(),
                                "local_date", LocalDate.now().minusDays(1).toString()))))
                .andExpect(status().isCreated());

        mvc.perform(get("/v1/sessions/completed-dates").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(LocalDate.now().minusDays(1).toString()));
    }

    @Test
    @DisplayName("a session cannot be logged for a future day or outside the window")
    void sessionDatesAreBounded() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        for (LocalDate day : List.of(LocalDate.now().plusDays(2), LocalDate.now().minusDays(20))) {
            mvc.perform(post("/v1/sessions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "id", UUID.randomUUID().toString(),
                                    "local_date", day.toString()))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("validation_failed"));
        }
    }

    @Test
    @DisplayName("a date range includes both its endpoints")
    void rangeQueriesAreInclusive() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        putFullWeek();

        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);

        for (LocalDate day : new LocalDate[] {threeDaysAgo, today}) {
            mvc.perform(post("/v1/sessions")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "id", UUID.randomUUID().toString(),
                                    "local_date", day.toString()))))
                    .andExpect(status().isCreated());
            mvc.perform(put("/v1/check-ins/" + day)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pain_score", 5))))
                    .andExpect(status().isOk());
        }

        // Both boundary days must come back. Spring Data's `Between` is
        // exclusive on MongoDB, so a naive query silently loses today.
        mvc.perform(get("/v1/sessions?from=" + threeDaysAgo + "&to=" + today)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/v1/check-ins?from=" + threeDaysAgo + "&to=" + today)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // …and adherence, which is built on that window, must see them.
        String summary = mvc.perform(get("/v1/check-ins/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(summary).get("adherence").asDouble()).isGreaterThan(0.0);
    }

    // ------------------------------------------------------------ check-ins

    @Test
    @DisplayName("a check-in is idempotent by verb and can be revised")
    void checkInIsIdempotent() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        String today = LocalDate.now().toString();

        mvc.perform(put("/v1/check-ins/" + today)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("pain_score", 6, "pain_location", "lower left"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pain_score").value(6));

        // Same day again — a revision, not a second row.
        mvc.perform(put("/v1/check-ins/" + today)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("pain_score", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pain_score").value(3));

        mvc.perform(get("/v1/check-ins").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a pain score outside 0–10 is rejected")
    void painScoreIsBounded() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        mvc.perform(put("/v1/check-ins/" + LocalDate.now())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("pain_score", 42))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_failed"));
    }

    @Test
    @DisplayName("you cannot check in for a day that has not happened")
    void futureCheckInIsRejected() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        mvc.perform(put("/v1/check-ins/" + LocalDate.now().plusDays(3))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("pain_score", 2))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the summary counts a streak across consecutive days")
    void summaryComputesStreak() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        for (int daysAgo = 2; daysAgo >= 0; daysAgo--) {
            mvc.perform(put("/v1/check-ins/" + LocalDate.now().minusDays(daysAgo))
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("pain_score", 4))))
                    .andExpect(status().isOk());
        }

        mvc.perform(get("/v1/check-ins/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_streak").value(3))
                .andExpect(jsonPath("$.total_check_ins").value(3))
                .andExpect(jsonPath("$.average_pain_score").value(4.0))
                .andExpect(jsonPath("$.latest_pain_score").value(4));
    }

    // ----------------------------------------------------------- progression

    @Test
    @DisplayName("a member starts on the first rung and can be moved")
    void progressionDefaultsToFirstLevel() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        String body = mvc.perform(get("/v1/progression").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current_level.level").value(1))
                .andReturn().getResponse().getContentAsString();

        JsonNode first = json.readTree(body).get(0);
        String signatureId = first.get("signature_exercise").get("id").asText();
        String secondLevelId = first.get("levels").get(1).get("id").asText();

        mvc.perform(put("/v1/progression")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "signature_exercise_id", signatureId,
                                "progression_level_id", secondLevelId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current_level.level").value(2));
    }

    // ---------------------------------------------------------------- admin

    @Test
    @DisplayName("a coach can prescribe an exercise, and the member sees it")
    void coachCanPrescribe() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        String memberToken = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        String created = mvc.perform(post("/v1/admin/clients/" + MEMBER_ID + "/assigned-exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "exercise_id", "ex_heel_slide",
                                "prescription", "2 x 8 reps · Slow tempo",
                                "note", "Stop if the back lifts."))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercise.name").exists())
                .andExpect(jsonPath("$.assigned_by_name").value("Dr. Ayush Nair"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/v1/assigned-exercises").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prescription").value("2 x 8 reps · Slow tempo"));

        // Prescribing the same movement twice is a conflict, not a duplicate.
        mvc.perform(post("/v1/admin/clients/" + MEMBER_ID + "/assigned-exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "exercise_id", "ex_heel_slide",
                                "prescription", "again"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("already_assigned"));

        // Withdrawing it takes it off the member's plan…
        String assignmentId = json.readTree(created).get("id").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/v1/admin/clients/" + MEMBER_ID + "/assigned-exercises/" + assignmentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mvc.perform(get("/v1/assigned-exercises").header("Authorization", "Bearer " + memberToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("the roster carries what decides whether to call someone")
    void rosterCarriesAttentionSignals() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(get("/v1/admin/clients").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user.role").value("member"))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("needs_attention")))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("adherence")))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("latest_pain_score")))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("sessions_this_week")))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("checked_in_today")))
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("has_plan")));
    }

    @Test
    @DisplayName("one client detail call carries the whole picture")
    void clientDetailIsOneCall() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(get("/v1/admin/clients/" + MEMBER_ID).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(MEMBER_ID))
                .andExpect(jsonPath("$.plan.days.monday").isArray())
                .andExpect(jsonPath("$.summary.current_streak").exists())
                .andExpect(jsonPath("$.assigned_exercises").isArray())
                .andExpect(jsonPath("$.progression").isArray());
    }

    @Test
    @DisplayName("a member cannot reach another member through the admin tree")
    void membersCannotReachTheConsole() throws Exception {
        String memberToken = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        mvc.perform(get("/v1/admin/clients").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/v1/admin/clients/user_2").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- profile

    @Test
    @DisplayName("a member fills in their age, height and weight, and the coach sees them")
    void memberFillsInProfile() throws Exception {
        String memberToken = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        mvc.perform(patch("/v1/me")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "date_of_birth", "1995-03-14",
                                "height_cm", 176,
                                "weight_kg", 72.5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.date_of_birth").value("1995-03-14"))
                .andExpect(jsonPath("$.user.height_cm").value(176))
                .andExpect(jsonPath("$.user.weight_kg").value(72.5));

        // A partial body leaves the rest alone.
        mvc.perform(patch("/v1/me")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("weight_kg", 71.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.height_cm").value(176))
                .andExpect(jsonPath("$.user.weight_kg").value(71.0));

        // Height in feet is the classic unit slip; it is caught at the door.
        mvc.perform(patch("/v1/me")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("height_cm", 6))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/v1/admin/clients/" + MEMBER_ID).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.date_of_birth").value("1995-03-14"))
                .andExpect(jsonPath("$.user.height_cm").value(176));
    }

    // ------------------------------------------------------------- routines

    @Test
    @DisplayName("routines are served from the catalogue with their exercises inlined")
    void routinesAreServed() throws Exception {
        String token = accessToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        mvc.perform(get("/v1/routines").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].exercises[0].exercise_id").exists())
                .andExpect(jsonPath("$[0].exercises[0].prescription").exists());

        mvc.perform(get("/v1/routines/rt_knee_foundation").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Knee Foundation"));
    }

    @Test
    @DisplayName("a prescription is not fenced by the client's program")
    void prescriptionsCrossPrograms() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);

        // A lower-back member given a knee exercise: the normal case, not an error.
        mvc.perform(post("/v1/admin/clients/" + MEMBER_ID + "/assigned-exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "exercise_id", "ex_tke_seated",
                                "prescription", "3 x 15 reps"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exercise.program_id").value("prog_knee"));
    }

    // -------------------------------------------------------------- helpers

    /** The coach puts the same two exercises on every day, so any day the test runs is a training day. */
    private void putFullWeek() throws Exception {
        String adminToken = accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
        List<Map<String, String>> work = List.of(
                Map.of("exercise_id", "ex_glute_bridge", "prescription", "3 x 2m holds"),
                Map.of("exercise_id", "ex_heel_slide", "prescription", "2 x 8 reps"));
        Map<String, Object> days = new LinkedHashMap<>();
        for (String day : new String[] {"monday", "tuesday", "wednesday", "thursday",
                "friday", "saturday", "sunday"}) {
            days.put(day, work);
        }
        mvc.perform(put("/v1/admin/clients/" + MEMBER_ID + "/plan")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("days", days))))
                .andExpect(status().isOk());
    }

    private String accessToken(String email, String password) throws Exception {
        String body = mvc.perform(post("/v1/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "email", email, "password", password, "timezone", "Asia/Kolkata"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("access_token").asText();
    }
}
