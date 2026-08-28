package in.hundredmph.api.domain.content;

import java.time.Instant;

/**
 * A rehab program — Lower Back, Knee, and so on. Part of the authored
 * catalogue, so this is a plain value read from the content file rather than a
 * Mongo document. Mirrors Program in src/data/types.ts.
 */
public record Program(
        String id,
        String name,
        String slug,
        String tagline,
        /** Ionicons glyph name; the client renders it directly. */
        String icon,
        Instant createdAt) {
}
