package in.hundredmph.api.schedule;

import in.hundredmph.api.schedule.dto.ScheduleResponse;
import in.hundredmph.api.schedule.dto.UpdateScheduleRequest;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in member's training week. */
@RestController
@RequestMapping("/v1/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ScheduleResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return scheduleService.forUser(principal.userId());
    }

    /** PUT the whole week — idempotent, and unambiguous about rest days. */
    @PutMapping
    public ScheduleResponse replace(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody UpdateScheduleRequest request) {
        return scheduleService.replace(principal.userId(), request.days());
    }
}
