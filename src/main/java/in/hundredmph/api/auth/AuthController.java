package in.hundredmph.api.auth;

import in.hundredmph.api.auth.dto.AuthResponse;
import in.hundredmph.api.auth.dto.ForgotPasswordRequest;
import in.hundredmph.api.auth.dto.LogoutRequest;
import in.hundredmph.api.auth.dto.PasswordLoginRequest;
import in.hundredmph.api.auth.dto.RefreshRequest;
import in.hundredmph.api.auth.dto.ResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** README §5.1. Everything here is reachable without a token, by definition. */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /v1/auth/password — what the app's login screen calls. */
    @PostMapping("/password")
    public AuthResponse signInWithPassword(@Valid @RequestBody PasswordLoginRequest request,
                                            HttpServletRequest httpRequest) {
        return authService.signInWithPassword(request, clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken(), request.timezone());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Always 202, whether or not the address is a member (README §5.1). The
     * response must not vary — that is the whole point of the endpoint.
     */
    @PostMapping("/password/forgot")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.email()).ifPresent(token ->
                // Stands in for the email send. Wire this to the mailer in Phase 1;
                // the token must never travel back in the HTTP response.
                log.info("Password reset token issued (dev log only): {}", token));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Best-effort client address for the throttle. X-Forwarded-For is only
     * meaningful behind a proxy that sets it; it is used for logging and
     * counting, never for authorisation.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
