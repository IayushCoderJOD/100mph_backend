package in.hundredmph.api.checkin;

import in.hundredmph.api.checkin.dto.CheckInRequest;
import in.hundredmph.api.checkin.dto.CheckInResponse;
import in.hundredmph.api.checkin.dto.CheckInSummaryResponse;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The daily check-in and the numbers derived from it. */
@RestController
@RequestMapping("/v1/check-ins")
public class CheckInController {

    private final CheckInService checkInService;

    public CheckInController(CheckInService checkInService) {
        this.checkInService = checkInService;
    }

    @GetMapping
    public List<CheckInResponse> range(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return checkInService.range(principal.userId(), from, to);
    }

    /** Streaks, adherence and the latest pain score — all computed server-side. */
    @GetMapping("/summary")
    public CheckInSummaryResponse summary(@AuthenticationPrincipal AuthPrincipal principal) {
        return checkInService.summary(principal.userId());
    }

    @GetMapping("/{date}")
    public CheckInResponse forDate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return checkInService.forDate(principal.userId(), date);
    }

    /** PUT the day: writing it twice is writing it once. */
    @PutMapping("/{date}")
    public CheckInResponse put(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody CheckInRequest request) {
        return checkInService.put(principal.userId(), date, request);
    }
}
