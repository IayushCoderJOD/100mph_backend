package in.hundredmph.api.auth.dto;

import in.hundredmph.api.me.dto.UserDto;

/**
 * What a successful sign-in or refresh returns. The user record rides along so
 * the client can render the shell without waiting on a second GET /me.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserDto user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
