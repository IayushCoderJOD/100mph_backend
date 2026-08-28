package in.hundredmph.api.common;

import java.util.Map;

/** The one exception the handlers translate into the §7.1 envelope. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message);
    }
}
