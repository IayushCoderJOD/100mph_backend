package in.hundredmph.api.me;

import in.hundredmph.api.account.AccountDeletion;
import in.hundredmph.api.auth.AuthService;
import in.hundredmph.api.auth.dto.AuthResponse;
import in.hundredmph.api.me.dto.ChangePasswordRequest;
import in.hundredmph.api.me.dto.DeleteAccountRequest;
import in.hundredmph.api.me.dto.MeResponse;
import in.hundredmph.api.me.dto.SetProgramRequest;
import in.hundredmph.api.me.dto.UpdateMeRequest;
import in.hundredmph.api.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final AuthService authService;
    private final AccountDeletion accountDeletion;

    public MeController(MeService meService, AuthService authService, AccountDeletion accountDeletion) {
        this.meService = meService;
        this.authService = authService;
        this.accountDeletion = accountDeletion;
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

    /** Returns a fresh token pair: every other session is ended, this one carries on. */
    @PutMapping("/password")
    public AuthResponse changePassword(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(principal.userId(), request.currentPassword(),
                request.newPassword(), request.deviceId());
    }

    /** Deletes the caller's account and everything recorded about them, for good. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody DeleteAccountRequest request) {
        authService.confirmPassword(principal.userId(), request.password());
        accountDeletion.delete(principal.userId());
        return ResponseEntity.noContent().build();
    }
}
