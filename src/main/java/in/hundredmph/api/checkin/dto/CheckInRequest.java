package in.hundredmph.api.checkin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** PUT /v1/check-ins/{date} — idempotent by verb, so a retry is harmless. */
public record CheckInRequest(
        Boolean checkedIn,
        /** 0 = no pain, 10 = severe. Null logs the day without a score. */
        @Min(0) @Max(10) Integer painScore,
        @Size(max = 200) String painLocation,
        @Size(max = 1000) String note) {
}
