package in.hundredmph.api.me.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * PATCH /v1/me. Every field is optional — a null means "leave it alone", which
 * is what makes this safe to call with a partial body.
 *
 * <p>The bounds on the body measurements are not clinical, only wide enough to
 * catch a unit mix-up (height in feet, weight in pounds) at the door rather
 * than in a chart later.
 */
public record UpdateMeRequest(
        @Size(min = 2, max = 120) String fullName,
        /** Blank clears it. Normalised and checked for uniqueness server-side. */
        @Size(max = 32) String phone,
        String timezone,
        String avatarUrl,
        @Past LocalDate dateOfBirth,
        @Min(50) @Max(272) Integer heightCm,
        @DecimalMin("2.0") @DecimalMax("500.0") Double weightKg) {
}
