package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /v1/me/password. {@code deviceId} keeps the fresh session tied to this device. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(max = 128) String newPassword,
        String deviceId) {
}
