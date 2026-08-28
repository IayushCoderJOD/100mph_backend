package in.hundredmph.api.progression;

import in.hundredmph.api.progression.dto.ProgressionResponse;
import in.hundredmph.api.progression.dto.SetProgressionRequest;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/progression")
public class ProgressionController {

    private final ProgressionService progressionService;

    public ProgressionController(ProgressionService progressionService) {
        this.progressionService = progressionService;
    }

    @GetMapping
    public List<ProgressionResponse> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return progressionService.forUser(principal.userId());
    }

    /** A member moving themselves up or down a ladder. */
    @PutMapping
    public ProgressionResponse set(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody SetProgressionRequest request) {
        return progressionService.set(principal.userId(), request.signatureExerciseId(),
                request.progressionLevelId(), "self_reported");
    }
}
