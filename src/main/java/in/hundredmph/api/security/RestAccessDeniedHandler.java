package in.hundredmph.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.common.ErrorResponse;
import in.hundredmph.api.common.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Authenticated, but not allowed here — a member reaching for /admin. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setStatus(ErrorCode.FORBIDDEN.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorCode.FORBIDDEN, "Not permitted", Map.of(),
                RequestIdFilter.current(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
