package in.hundredmph.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /v1/auth/password — README §5.1. */
public record PasswordLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        /** Which phone this session belongs to. Optional; recorded, not trusted. */
        String deviceId,
        /** IANA zone, sent on every login per README §8.2 item 8. */
        String timezone) {
}
