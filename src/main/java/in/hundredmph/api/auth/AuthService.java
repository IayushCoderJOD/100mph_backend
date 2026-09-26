package in.hundredmph.api.auth;

import in.hundredmph.api.auth.dto.AuthResponse;
import in.hundredmph.api.auth.dto.PasswordLoginRequest;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.config.AuthProperties;
import in.hundredmph.api.domain.auth.PasswordReset;
import in.hundredmph.api.domain.auth.PasswordResetRepository;
import in.hundredmph.api.domain.auth.RefreshToken;
import in.hundredmph.api.domain.auth.RefreshTokenRepository;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRepository;
import in.hundredmph.api.domain.user.UserStatus;
import in.hundredmph.api.me.dto.UserDto;
import in.hundredmph.api.security.JwtService;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Sign-in, token rotation and password recovery.
 *
 * <p>Two rules shape everything here. First, an unauthenticated caller learns
 * nothing about who has an account: a bad email and a bad password fail
 * identically, and a forgot-password request for a stranger looks exactly like
 * one for a member. Second, a refresh token is single-use — presenting one
 * mints its replacement and revokes it, and presenting it a second time is
 * treated as theft.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetRepository passwordResets;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final JwtService jwtService;
    private final LoginThrottle loginThrottle;
    private final AuthProperties authProperties;
    private final MongoTemplate mongo;

    public AuthService(UserRepository users,
                       RefreshTokenRepository refreshTokens,
                       PasswordResetRepository passwordResets,
                       PasswordEncoder passwordEncoder,
                       TokenGenerator tokenGenerator,
                       JwtService jwtService,
                       LoginThrottle loginThrottle,
                       AuthProperties authProperties,
                       MongoTemplate mongo) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResets = passwordResets;
        this.passwordEncoder = passwordEncoder;
        this.tokenGenerator = tokenGenerator;
        this.jwtService = jwtService;
        this.loginThrottle = loginThrottle;
        this.authProperties = authProperties;
        this.mongo = mongo;
    }

    // ---------------------------------------------------------------- sign in

    public AuthResponse signInWithPassword(PasswordLoginRequest request, String requestIp) {
        Instant now = Instant.now();
        String email = normaliseEmail(request.email());

        loginThrottle.assertNotLocked(email, now);

        Optional<User> found = users.findByEmail(email);

        // The password is verified even when no user matched, against a dummy
        // hash, so a missing account and a wrong password take the same time.
        // Without this, response latency alone answers "is this person a member".
        String storedHash = found.map(User::getPasswordHash).orElse(null);
        boolean passwordMatches = verifyPassword(request.password(), storedHash);

        if (found.isEmpty() || !passwordMatches || found.get().isDeleted()) {
            loginThrottle.recordFailure(email, requestIp, now);
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Email and password do not match an account");
        }

        User user = found.get();

        // A suspended account gets a distinct code: they have proved who they
        // are, so telling them why they are locked out reveals nothing new.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            loginThrottle.recordFailure(email, requestIp, now);
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended");
        }

        loginThrottle.recordSuccess(email, requestIp, now);

        boolean dirty = false;

        // "Invited" means provisioned but never used. The first successful
        // sign-in is what makes it a live account — matching the copy on the
        // admin's create-user screen.
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
            dirty = true;
        }

        String timezone = validTimezone(request.timezone());
        if (timezone != null && !timezone.equals(user.getTimezone())) {
            user.setTimezone(timezone);
            dirty = true;
        }

        if (dirty) {
            user.setUpdatedAt(now);
            users.save(user);
        }

        // A new login starts a new family: signing in on a second phone must not
        // let a compromise there revoke the session on the first.
        return issueTokens(user, UUID.randomUUID().toString(), request.deviceId(), now);
    }

    // ---------------------------------------------------------------- refresh

    /**
     * Rotates a refresh token, with reuse detection (README §7.5).
     *
     * <p>If the presented token was already rotated away, two parties hold it —
     * the legitimate client and whoever copied it. There is no way to tell which
     * one is calling, so the entire family is revoked and both are forced to
     * sign in again. That is the intended outcome, not collateral damage.
     */
    public AuthResponse refresh(String rawRefreshToken, String timezone) {
        Instant now = Instant.now();
        String hash = tokenGenerator.hash(rawRefreshToken);

        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.of(ErrorCode.REFRESH_TOKEN_INVALID,
                        "Refresh token is not recognised"));

        if (stored.isRevoked()) {
            revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; family {} revoked",
                    stored.getUserId(), stored.getFamilyId());
            throw ApiException.of(ErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token has already been used; sign in again");
        }

        if (stored.isExpired(now)) {
            throw ApiException.of(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token has expired");
        }

        User user = users.findById(stored.getUserId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.REFRESH_TOKEN_INVALID,
                        "Refresh token does not resolve to an account"));

        // Status is re-read here, not taken from the token: suspending someone
        // has to end their session at the next refresh, not 30 days later.
        if (user.getStatus() == UserStatus.SUSPENDED) {
            revokeFamily(stored.getFamilyId(), now);
            throw ApiException.of(ErrorCode.ACCOUNT_SUSPENDED, "This account has been suspended");
        }

        // The zone is re-read here for the same reason status is: a refresh
        // token lasts 30 days, and a member who moves or travels must not spend
        // that long having their local day computed in the place they left.
        // Every day-boundary in the app (session plan, check-in) reads it.
        String zone = validTimezone(timezone);
        if (zone != null && !zone.equals(user.getTimezone())) {
            user.setTimezone(zone);
            user.setUpdatedAt(now);
            users.save(user);
        }

        // Claim the token atomically. Reading it above and saving it here would
        // let two requests racing with the same token both pass the revoked
        // check and both walk away with a fresh pair — exactly the copy the
        // reuse detection exists to catch. Only one update can flip it.
        boolean claimed = mongo.updateFirst(
                Query.query(Criteria.where("id").is(stored.getId()).and("revokedAt").is(null)),
                Update.update("revokedAt", now),
                RefreshToken.class).getModifiedCount() == 1;
        if (!claimed) {
            revokeFamily(stored.getFamilyId(), now);
            log.warn("Concurrent refresh with one token for user {}; family {} revoked",
                    stored.getUserId(), stored.getFamilyId());
            throw ApiException.of(ErrorCode.REFRESH_TOKEN_REUSED,
                    "Refresh token has already been used; sign in again");
        }

        return issueTokens(user, stored.getFamilyId(), stored.getDeviceId(), now);
    }

    // ----------------------------------------------------------------- logout

    /**
     * Ends one session. Unknown or already-revoked tokens still return quietly —
     * logging out is not a place to report failure, and the desired end state
     * (this token is not usable) already holds.
     */
    public void logout(String rawRefreshToken) {
        Instant now = Instant.now();
        refreshTokens.findByTokenHash(tokenGenerator.hash(rawRefreshToken)).ifPresent(stored -> {
            if (!stored.isRevoked()) {
                stored.setRevokedAt(now);
                refreshTokens.save(stored);
            }
        });
    }

    // --------------------------------------------------------- password reset

    /**
     * Always succeeds from the caller's point of view (README §5.1). The token
     * is returned to the caller only so the controller can hand it to whatever
     * sends the email; it is never put in the HTTP response.
     */
    public Optional<String> requestPasswordReset(String rawEmail) {
        Instant now = Instant.now();
        String email = normaliseEmail(rawEmail);

        Optional<User> found = users.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .filter(user -> user.getStatus() != UserStatus.SUSPENDED);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        String rawToken = tokenGenerator.generate();

        PasswordReset reset = new PasswordReset();
        reset.setUserId(found.get().getId());
        reset.setTokenHash(tokenGenerator.hash(rawToken));
        reset.setExpiresAt(now.plus(authProperties.passwordResetTtl()));
        reset.setCreatedAt(now);
        passwordResets.save(reset);

        return Optional.of(rawToken);
    }

    /** Consumes the grant, sets the password, and ends every existing session. */
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now();

        PasswordReset reset = passwordResets.findByTokenHash(tokenGenerator.hash(rawToken))
                .filter(candidate -> candidate.isUsable(now))
                .orElseThrow(() -> ApiException.of(ErrorCode.RESET_TOKEN_INVALID,
                        "This reset link is invalid or has expired"));

        User user = users.findById(reset.getUserId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.RESET_TOKEN_INVALID,
                        "This reset link is invalid or has expired"));

        assertPasswordStrongEnough(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        users.save(user);

        reset.setConsumedAt(now);
        passwordResets.save(reset);

        // Changing a password is how someone reacts to a compromise, so it has
        // to log every other device out, not just set a new secret.
        revokeAllSessions(user.getId(), now);
    }

    /**
     * A coach sets a new temporary password for someone who is locked out.
     * Every session the account had is ended, so the old password — and any
     * device still holding a token — stops working at once.
     */
    public void setPassword(String userId, String newPassword) {
        Instant now = Instant.now();
        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        assertPasswordStrongEnough(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        users.save(user);

        revokeAllSessions(userId, now);
    }

    /**
     * A signed-in member replaces their own password. Every other device is
     * signed out; this one is handed a fresh pair so it stays signed in.
     */
    public AuthResponse changePassword(String userId, String currentPassword, String newPassword, String deviceId) {
        Instant now = Instant.now();
        User user = users.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND, "No such user"));

        if (!verifyPassword(currentPassword, user.getPasswordHash())) {
            throw ApiException.of(ErrorCode.CURRENT_PASSWORD_INCORRECT, "Your current password is not right");
        }
        assertPasswordStrongEnough(newPassword);
        if (verifyPassword(newPassword, user.getPasswordHash())) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "The new password must be different");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(now);
        users.save(user);

        revokeAllSessions(userId, now);
        return issueTokens(user, UUID.randomUUID().toString(), deviceId, now);
    }

    public void assertPasswordStrongEnough(String password) {
        if (password == null || password.length() < authProperties.minPasswordLength()) {
            throw ApiException.of(ErrorCode.PASSWORD_TOO_WEAK,
                    "Password must be at least " + authProperties.minPasswordLength() + " characters");
        }
    }

    public void revokeAllSessions(String userId, Instant now) {
        List<RefreshToken> active = refreshTokens.findByUserId(userId).stream()
                .filter(token -> !token.isRevoked())
                .peek(token -> token.setRevokedAt(now))
                .toList();
        if (!active.isEmpty()) {
            refreshTokens.saveAll(active);
        }
    }

    // ---------------------------------------------------------------- helpers

    private AuthResponse issueTokens(User user, String familyId, String deviceId, Instant now) {
        String accessToken = jwtService.issueAccessToken(user, now);
        String rawRefreshToken = tokenGenerator.generate();

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setTokenHash(tokenGenerator.hash(rawRefreshToken));
        record.setFamilyId(familyId);
        record.setDeviceId(deviceId);
        record.setExpiresAt(now.plus(jwtService.refreshTtl()));
        record.setCreatedAt(now);
        refreshTokens.save(record);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.accessTtl().toSeconds(),
                UserDto.from(user));
    }

    private void revokeFamily(String familyId, Instant now) {
        List<RefreshToken> family = refreshTokens.findByFamilyId(familyId).stream()
                .filter(token -> !token.isRevoked())
                .peek(token -> token.setRevokedAt(now))
                .toList();
        if (!family.isEmpty()) {
            refreshTokens.saveAll(family);
        }
    }

    /**
     * A BCrypt hash of a fixed string, used only to burn the same CPU time when
     * there is no account to check against. The value is irrelevant; that the
     * work happens is the point.
     */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.eS7.4Rm/9G7ZgOQ8N4bH6zLc.9CvV1u";

    private boolean verifyPassword(String rawPassword, String storedHash) {
        return passwordEncoder.matches(rawPassword, storedHash == null ? DUMMY_HASH : storedHash);
    }

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** Ignores a zone the client got wrong rather than failing the sign-in over it. */
    private static String validTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return null;
        }
        try {
            return ZoneId.of(timezone.trim()).getId();
        } catch (Exception ex) {
            return null;
        }
    }
}
