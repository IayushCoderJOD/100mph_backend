package in.hundredmph.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The single error envelope, exactly as README §7.1 specifies:
 * {@code { "error": { "code", "message", "details" } }}.
 */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Body(String code, String message, Map<String, Object> details, String requestId) {}

    public static ErrorResponse of(ErrorCode code, String message, Map<String, Object> details, String requestId) {
        return new ErrorResponse(new Body(code.code(), message, details, requestId));
    }
}
