package in.hundredmph.api.admin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PUT /v1/admin/users/{id} — the server side of app/admin/edit/[id].tsx. The
 * same whole-form save as {@code UpdateProfileRequest}, plus the sign-in email.
 */
public record UpdateUserRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Email String email,
        /** Blank clears it. Normalised and checked for uniqueness server-side. */
        @Size(max = 32) String phone,
        @Past LocalDate dateOfBirth,
        @Min(50) @Max(272) Integer heightCm,
        @DecimalMin("2.0") @DecimalMax("500.0") Double weightKg) {
}
