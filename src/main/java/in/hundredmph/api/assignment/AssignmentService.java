package in.hundredmph.api.assignment;

import in.hundredmph.api.assignment.dto.AssignExerciseRequest;
import in.hundredmph.api.assignment.dto.AssignedExerciseResponse;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.content.model.Exercise;
import in.hundredmph.api.domain.assignment.AssignedExercise;
import in.hundredmph.api.domain.assignment.AssignedExerciseRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.exercise.ExerciseLibrary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AssignmentService {

    private final AssignedExerciseRepository assignments;
    private final UserRepository users;
    private final ExerciseLibrary exercises;

    public AssignmentService(AssignedExerciseRepository assignments,
                             UserRepository users,
                             ExerciseLibrary exercises) {
        this.assignments = assignments;
        this.users = users;
        this.exercises = exercises;
    }

    /** The member's active prescriptions, in the order the coach set. */
    public List<AssignedExerciseResponse> forUser(String userId) {
        List<AssignedExercise> active = assignments.findByUserIdAndActiveTrueOrderBySortOrderAsc(userId);
        if (active.isEmpty()) return List.of();

        // One lookup for every coach named, rather than one per row.
        Map<String, String> namesById = users
                .findAllById(active.stream().map(AssignedExercise::getAssignedBy).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));

        return active.stream()
                .map(assignment -> AssignedExerciseResponse.of(
                        assignment,
                        exercises.find(assignment.getExerciseId()).orElse(null),
                        namesById.getOrDefault(assignment.getAssignedBy(), "Staff")))
                .toList();
    }

    /** Prescribes an exercise. Staff-only; the caller's id is recorded. */
    public AssignedExerciseResponse assign(String userId, String assignedBy, AssignExerciseRequest request) {
        User client = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such client"));

        Exercise exercise = exercises.find(request.exerciseId())
                .orElseThrow(() -> ApiException.of(ErrorCode.EXERCISE_NOT_FOUND,
                        "No exercise with id " + request.exerciseId()));

        // Deliberately not fenced by program. The program is a starting point;
        // what a member actually does is the routine and prescriptions the coach
        // chose for them, and a knee patient who also needs a hip exercise is the
        // normal case, not an error.

        List<AssignedExercise> existing = assignments.findByUserIdAndActiveTrueOrderBySortOrderAsc(userId);
        if (existing.stream().anyMatch(a -> a.getExerciseId().equals(request.exerciseId()))) {
            throw ApiException.of(ErrorCode.ALREADY_ASSIGNED,
                    "That exercise is already assigned to this client");
        }

        Instant now = Instant.now();
        AssignedExercise assignment = new AssignedExercise();
        assignment.setId(UUID.randomUUID().toString());
        assignment.setUserId(userId);
        assignment.setExerciseId(request.exerciseId());
        assignment.setAssignedBy(assignedBy);
        assignment.setPrescription(request.prescription().trim());
        assignment.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        assignment.setSortOrder(existing.size() + 1);
        assignment.setActive(true);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        assignments.save(assignment);

        String assignedByName = users.findById(assignedBy).map(User::getFullName).orElse("Staff");
        return AssignedExerciseResponse.of(assignment, exercise, assignedByName);
    }

    /**
     * Withdraws a prescription. Deactivated rather than deleted — what a client
     * was once asked to do is part of their clinical history.
     */
    public void withdraw(String assignmentId) {
        AssignedExercise assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such assignment"));

        assignment.setActive(false);
        assignment.setUpdatedAt(Instant.now());
        assignments.save(assignment);
    }

    /** Used by the admin client view, which shows several clients' counts. */
    public Map<String, Integer> activeCountsFor(List<String> userIds) {
        return userIds.stream().collect(Collectors.toMap(
                Function.identity(),
                id -> assignments.findByUserIdAndActiveTrueOrderBySortOrderAsc(id).size(),
                (a, b) -> a));
    }
}
