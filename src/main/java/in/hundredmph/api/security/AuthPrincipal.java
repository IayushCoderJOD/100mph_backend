package in.hundredmph.api.security;

import in.hundredmph.api.domain.user.UserRole;

/**
 * What a verified access token resolves to. Deliberately just the identity and
 * the role: anything else a handler needs, it loads, so a stale claim in a
 * 15-minute-old token can never stand in for the current record.
 */
public record AuthPrincipal(String userId, UserRole role) {

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
