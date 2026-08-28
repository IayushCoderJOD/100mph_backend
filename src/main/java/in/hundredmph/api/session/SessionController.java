package in.hundredmph.api.session;

import in.hundredmph.api.security.AuthPrincipal;
import in.hundredmph.api.session.dto.LogSessionRequest;
import in.hundredmph.api.session.dto.SessionLogResponse;
import in.hundredmph.api.session.dto.SessionPlanResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Training days: what today asks for, and what actually got done. */
@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Today's plan, or another day's. A rest day returns a null session type. */
    @GetMapping("/plan")
    public SessionPlanResponse plan(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return sessionService.planFor(principal.userId(), date);
    }

    /** Records a completed session. Safe to retry with the same id. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionLogResponse log(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody LogSessionRequest request) {
        return sessionService.log(principal.userId(), request);
    }

    @GetMapping
    public List<SessionLogResponse> history(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sessionService.history(principal.userId(), from, to);
    }

    /** Just the dates — all the week strip needs, without the payload. */
    @GetMapping("/completed-dates")
    public List<LocalDate> completedDates(@AuthenticationPrincipal AuthPrincipal principal) {
        return sessionService.completedDates(principal.userId());
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                        @PathVariable String sessionId) {
        sessionService.delete(principal.userId(), sessionId);
        return ResponseEntity.noContent().build();
    }
}
