package in.hundredmph.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT /v1/admin/users/{id}/password — a new temporary password, read out to the client. */
public record SetPasswordRequest(@NotBlank @Size(max = 128) String password) {
}
