package in.hundredmph.api.me.dto;

import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRole;
import in.hundredmph.api.domain.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The user as the client sees it. Built by hand rather than serialising the
 * document, so {@code password_hash} cannot escape by someone adding a field.
 * Shape matches the User interface in src/data/types.ts.
 */
public record UserDto(
        String id,
        String fullName,
        String email,
        String phone,
        String avatarUrl,
        String timezone,
        String activeProgramId,
        UserRole role,
        UserStatus status,
        LocalDate memberSince,
        Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getTimezone(),
                user.getActiveProgramId(),
                user.getRole(),
                user.getStatus(),
                user.getMemberSince(),
                user.getCreatedAt());
    }
}
