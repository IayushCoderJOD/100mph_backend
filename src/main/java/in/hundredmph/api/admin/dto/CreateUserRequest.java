package in.hundredmph.api.admin.dto;

import in.hundredmph.api.domain.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /v1/admin/users — the server side of app/admin/create-user.tsx.
 *
 * <p>The README models provisioning as an invite the member redeems. This
 * matches the screen that actually exists today: the coach sets a temporary
 * password and reads it back to the client in the room. Invites can be added
 * alongside without changing this.
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email String email,
        String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        /** Required for members, ignored for admins — staff do not train here. */
        String programId,
        @NotNull UserRole role) {
}
