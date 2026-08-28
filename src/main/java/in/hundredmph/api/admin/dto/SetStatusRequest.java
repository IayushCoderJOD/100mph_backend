package in.hundredmph.api.admin.dto;

import in.hundredmph.api.domain.user.UserStatus;
import jakarta.validation.constraints.NotNull;

public record SetStatusRequest(@NotNull UserStatus status) {
}
