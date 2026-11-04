package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PUT /v1/me/profile — the Edit Details form, saved whole. Unlike PATCH /v1/me,
 * a null here clears the field. The sign-in email is not on this form: with no
 * mailer to confirm a new address, only a coach can change it.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(min = 2, max = 120) String fullName,
        /** Blank clears it. Normalised and checked for uniqueness server-side. */
        @Size(max = 32) String phone,
        @Past LocalDate dateOfBirth,
        @Min(50) @Max(272) Integer heightCm,
        @DecimalMin("2.0") @DecimalMax("500.0") Double weightKg) {
}
