package in.hundredmph.api.me;

import in.hundredmph.api.me.dto.MeResponse;
import in.hundredmph.api.me.dto.SetProgramRequest;
import in.hundredmph.api.me.dto.UpdateMeRequest;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** README §5.2. The user id always comes from the token, never from the path. */
@RestController
@RequestMapping("/v1/me")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    /** GET /v1/me — the boot call. */
    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return meService.boot(principal.userId());
    }

    @PatchMapping
    public MeResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                              @Valid @RequestBody UpdateMeRequest request) {
        return meService.update(principal.userId(), request);
    }

    @PutMapping("/program")
    public MeResponse setProgram(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody SetProgramRequest request) {
        return meService.setProgram(principal.userId(), request.programId());
    }
}
