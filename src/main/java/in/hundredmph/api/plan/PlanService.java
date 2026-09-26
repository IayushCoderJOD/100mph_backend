package in.hundredmph.api.plan;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.ContentCatalogue;
import in.hundredmph.api.domain.plan.WeeklyPlan;
import in.hundredmph.api.domain.plan.WeeklyPlan.PlannedExercise;
import in.hundredmph.api.domain.plan.WeeklyPlanRepository;
import in.hundredmph.api.domain.schedule.DayOfWeek;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.plan.dto.UpdatePlanRequest;
import in.hundredmph.api.plan.dto.WeeklyPlanResponse;
import in.hundredmph.api.plan.dto.WeeklyPlanResponse.PlannedExerciseResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The member's week, as written by their physio.
 *
 * <p>A member who has not been given a plan yet reads an empty week rather
 * than an error — a fresh account is the normal state, not a fault — and the
 * app renders every day as rest until the coach fills it in.
 */
@Service
public class PlanService {

    private final WeeklyPlanRepository plans;
    private final UserRepository users;
    private final ContentCatalogue catalogue;

    public PlanService(WeeklyPlanRepository plans, UserRepository users, ContentCatalogue catalogue) {
        this.plans = plans;
        this.users = users;
        this.catalogue = catalogue;
    }

    public WeeklyPlanResponse forUser(String userId) {
        User member = requireMember(userId);
        return toResponse(member.getId(), plans.findByUserId(userId).orElse(null));
    }

    /** Replaces the whole week. Staff-only; the writer's id is recorded. */
    public WeeklyPlanResponse replace(String userId, String updatedBy, UpdatePlanRequest request) {
        User member = requireMember(userId);
        Map<DayOfWeek, List<PlannedExercise>> days = parseAndValidate(request.days());

        Instant now = Instant.now();
        WeeklyPlan plan = plans.findByUserId(userId).orElseGet(() -> {
            WeeklyPlan fresh = new WeeklyPlan();
            fresh.setId(UUID.randomUUID().toString());
            fresh.setUserId(member.getId());
            fresh.setCreatedAt(now);
            return fresh;
        });

        plan.setDays(days);
        plan.setUpdatedBy(updatedBy);
        plan.setUpdatedAt(now);
        plans.save(plan);

        return toResponse(member.getId(), plan);
    }

    /** The work planned for a member on a date; empty on a rest day or with no plan. */
    public List<PlannedExercise> plannedOn(String userId, DayOfWeek day) {
        return plans.findByUserId(userId).map(plan -> plan.on(day)).orElse(List.of());
    }

    public boolean hasWorkOn(String userId, DayOfWeek day) {
        return !plannedOn(userId, day).isEmpty();
    }

    /** Whether the coach has written anything at all for this member. */
    public boolean hasAnyWork(String userId) {
        return plans.findByUserId(userId)
                .map(plan -> plan.getDays().values().stream().anyMatch(lines -> !lines.isEmpty()))
                .orElse(false);
    }

    // ---------------------------------------------------------------- helpers

    private Map<DayOfWeek, List<PlannedExercise>> parseAndValidate(
            Map<String, List<UpdatePlanRequest.PlannedExerciseInput>> requested) {
        Map<DayOfWeek, List<PlannedExercise>> days = new EnumMap<>(DayOfWeek.class);

        for (Map.Entry<String, List<UpdatePlanRequest.PlannedExerciseInput>> entry : requested.entrySet()) {
            DayOfWeek day;
            try {
                day = DayOfWeek.from(entry.getKey());
            } catch (IllegalArgumentException ex) {
                throw new ApiException(ErrorCode.SCHEDULE_INVALID_DAY,
                        "Not a day of the week: " + entry.getKey(), Map.of("day", entry.getKey()));
            }

            List<UpdatePlanRequest.PlannedExerciseInput> lines = entry.getValue();
            if (lines == null || lines.isEmpty()) continue;

            // The same movement twice in a day is a longer set, not two rows.
            Set<String> seen = new HashSet<>();
            List<PlannedExercise> planned = new ArrayList<>(lines.size());
            for (UpdatePlanRequest.PlannedExerciseInput line : lines) {
                String exerciseId = line.exerciseId().trim();
                if (catalogue.exercise(exerciseId).isEmpty()) {
                    throw new ApiException(ErrorCode.EXERCISE_NOT_FOUND,
                            "No exercise with id " + exerciseId, Map.of("day", day.wire()));
                }
                if (!seen.add(exerciseId)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            exerciseId + " appears twice on " + day.wire(), Map.of("day", day.wire()));
                }
                planned.add(new PlannedExercise(exerciseId, planned.size() + 1, line.prescription().trim()));
            }
            days.put(day, List.copyOf(planned));
        }

        return days;
    }

    private WeeklyPlanResponse toResponse(String userId, WeeklyPlan plan) {
        // Every day present, Monday first, so the client never has to fill gaps.
        Map<String, List<PlannedExerciseResponse>> days = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.ordered()) {
            List<PlannedExercise> planned = plan == null ? List.of() : plan.on(day);
            days.put(day.wire(), planned.stream()
                    .map(line -> new PlannedExerciseResponse(
                            line.exerciseId(),
                            line.sortOrder(),
                            line.prescription(),
                            catalogue.exercise(line.exerciseId()).orElse(null)))
                    .toList());
        }

        if (plan == null) {
            return new WeeklyPlanResponse(userId, days, null, null, null);
        }

        String updatedByName = Optional.ofNullable(plan.getUpdatedBy())
                .flatMap(users::findById)
                .map(User::getFullName)
                .orElse(null);

        return new WeeklyPlanResponse(userId, days, plan.getUpdatedBy(), updatedByName, plan.getUpdatedAt());
    }

    private User requireMember(String userId) {
        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        // Staff do not train here, so there is no week to read or write.
        if (user.getRole() != UserRole.MEMBER) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Only a member has a training week");
        }
        return user;
    }
}
