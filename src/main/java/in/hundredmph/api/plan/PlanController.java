package in.hundredmph.api.plan;

import in.hundredmph.api.plan.dto.WeeklyPlanResponse;
import in.hundredmph.api.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in member's training week. Read-only from this side: the plan is
 * written by the physio through the admin tree, and a member who wants it
 * changed asks them.
 */
@RestController
@RequestMapping("/v1/plan")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @GetMapping
    public WeeklyPlanResponse mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return plans.forUser(principal.userId());
    }
}
