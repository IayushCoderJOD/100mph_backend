package in.hundredmph.api.exercise.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Where to PUT the file, and what to send with it. Once the PUT succeeds, the
 * client attaches {@code key} to the exercise.
 */
public record UploadResponse(String uploadUrl, String method, Map<String, String> headers,
                             String key, Instant expiresAt) {
}
