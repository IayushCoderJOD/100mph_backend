package in.hundredmph.api.admin;

import in.hundredmph.api.admin.dto.ClientDetailResponse;
import in.hundredmph.api.admin.dto.ClientSummaryResponse;
import in.hundredmph.api.assignment.AssignmentService;
import in.hundredmph.api.assignment.dto.AssignExerciseRequest;
import in.hundredmph.api.assignment.dto.AssignedExerciseResponse;
import in.hundredmph.api.plan.PlanService;
import in.hundredmph.api.plan.dto.UpdatePlanRequest;
import in.hundredmph.api.plan.dto.WeeklyPlanResponse;
import in.hundredmph.api.progression.ProgressionService;
import in.hundredmph.api.progression.dto.ProgressionResponse;
import in.hundredmph.api.progression.dto.SetProgressionRequest;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The coach console's client endpoints. Admin-only — enforced on the path in
 * SecurityConfig, so a handler added here cannot be left unguarded.
 */
@RestController
@RequestMapping("/v1/admin/clients")
public class AdminClientController {

    private final AdminClientService clients;
    private final AssignmentService assignments;
    private final ProgressionService progressions;
    private final PlanService plans;

    public AdminClientController(AdminClientService clients,
                                 AssignmentService assignments,
                                 ProgressionService progressions,
                                 PlanService plans) {
        this.clients = clients;
        this.assignments = assignments;
        this.progressions = progressions;
        this.plans = plans;
    }

    /** The roster with adherence and latest pain — backs the Clients tab. */
    @GetMapping
    public List<ClientSummaryResponse> roster() {
        return clients.roster();
    }

    /** Everything about one client, in one call. */
    @GetMapping("/{userId}")
    public ClientDetailResponse detail(@PathVariable String userId) {
        return clients.detail(userId);
    }

    @GetMapping("/{userId}/assigned-exercises")
    public List<AssignedExerciseResponse> assignedExercises(@PathVariable String userId) {
        return assignments.forUser(userId);
    }

    /** Prescribes an exercise. The prescribing coach is taken from the token. */
    @PostMapping("/{userId}/assigned-exercises")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignedExerciseResponse assign(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable String userId,
                                            @Valid @RequestBody AssignExerciseRequest request) {
        return assignments.assign(userId, principal.userId(), request);
    }

    @DeleteMapping("/{userId}/assigned-exercises/{assignmentId}")
    public ResponseEntity<Void> withdraw(@PathVariable String userId,
                                          @PathVariable String assignmentId) {
        assignments.withdraw(assignmentId);
        return ResponseEntity.noContent().build();
    }

    /** The client's week as their physio wrote it — every day, rest days empty. */
    @GetMapping("/{userId}/plan")
    public WeeklyPlanResponse plan(@PathVariable String userId) {
        return plans.forUser(userId);
    }

    /** Writes the whole week. The writing coach is taken from the token. */
    @PutMapping("/{userId}/plan")
    public WeeklyPlanResponse replacePlan(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable String userId,
                                          @Valid @RequestBody UpdatePlanRequest request) {
        return plans.replace(userId, principal.userId(), request);
    }

    /** A coach override of where a client sits on a ladder. */
    @PutMapping("/{userId}/progression")
    public ProgressionResponse setProgression(@PathVariable String userId,
                                               @Valid @RequestBody SetProgressionRequest request) {
        return progressions.set(userId, request.signatureExerciseId(),
                request.progressionLevelId(), "coach_set");
    }
}
