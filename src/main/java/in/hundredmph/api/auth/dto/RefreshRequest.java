package in.hundredmph.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /v1/auth/refresh. */
public record RefreshRequest(
        @NotBlank String refreshToken,
        /**
         * IANA zone, re-sent on every refresh. Sign-in alone is not enough: the
         * refresh token lives 30 days, so a member who relocates or travels
         * would otherwise carry a stale zone until it expires.
         */
        String timezone) {
}
