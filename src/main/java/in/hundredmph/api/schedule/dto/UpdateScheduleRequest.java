package in.hundredmph.api.schedule.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * PUT the whole week at once. A partial update of a plan is ambiguous — is a
 * missing day a rest day, or untouched? — so the client sends all seven and the
 * verb stays idempotent.
 */
public record UpdateScheduleRequest(@NotNull Map<String, String> days) {
}
