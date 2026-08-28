package in.hundredmph.api.security;

import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.config.JwtProperties;
import in.hundredmph.api.domain.user.User;
import in.hundredmph.api.domain.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the stateless access token. Refresh tokens are not JWTs
 * on purpose — they are opaque random strings kept in Mongo, because a refresh
 * token has to be revocable and a self-contained JWT is not.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes; set the JWT_SECRET environment variable");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(User user, Instant now) {
        Instant expiry = now.plus(properties.accessTtl());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .subject(user.getId())
                .claim(CLAIM_ROLE, user.getRole().wire())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then hands back the identity.
     * Throws the 401 the client should react to by refreshing.
     */
    public AuthPrincipal verifyAccessToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw ApiException.of(ErrorCode.TOKEN_EXPIRED, "Access token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "Access token is not valid");
        }

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "Token is not an access token");
        }

        String subject = claims.getSubject();
        String role = claims.get(CLAIM_ROLE, String.class);
        if (subject == null || role == null) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "Access token is missing required claims");
        }

        UserRole parsedRole;
        try {
            parsedRole = UserRole.from(role);
        } catch (IllegalArgumentException ex) {
            throw ApiException.of(ErrorCode.TOKEN_INVALID, "Access token carries an unknown role");
        }

        return new AuthPrincipal(subject, parsedRole);
    }

    public Duration accessTtl() { return properties.accessTtl(); }

    public Duration refreshTtl() { return properties.refreshTtl(); }
}
