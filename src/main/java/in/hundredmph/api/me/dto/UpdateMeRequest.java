package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH /v1/me. Every field is optional — a null means "leave it alone", which
 * is what makes this safe to call with a partial body.
 */
public record UpdateMeRequest(
        @Size(min = 2, max = 120) String fullName,
        String timezone,
        String avatarUrl) {
}
