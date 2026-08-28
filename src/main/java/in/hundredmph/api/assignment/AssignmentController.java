package in.hundredmph.api.assignment;

import in.hundredmph.api.assignment.dto.AssignedExerciseResponse;
import in.hundredmph.api.security.AuthPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The member's own prescriptions. Writing them is a staff action and lives
 * under /admin; this is the read side the Plan tab uses.
 */
@RestController
@RequestMapping("/v1/assigned-exercises")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<AssignedExerciseResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return assignmentService.forUser(principal.userId());
    }
}
