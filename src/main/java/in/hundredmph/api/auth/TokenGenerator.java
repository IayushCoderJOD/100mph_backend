package in.hundredmph.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Opaque tokens for the things a JWT cannot do: be revoked, and be looked up.
 *
 * <p>The raw token goes to the client once and is never stored. What is stored
 * is a SHA-256 digest — fast, because these are 256 bits of entropy already and
 * a slow KDF buys nothing against a value that cannot be guessed or reused
 * across sites. Passwords are the opposite case, and use BCrypt.
 */
@Component
public class TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    /** A fresh token. Return it to the caller once; keep only {@link #hash}. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return encoder.encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required and must be present", ex);
        }
    }
}
