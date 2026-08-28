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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** No token at all on a protected route. Same envelope, no browser challenge. */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setStatus(ErrorCode.TOKEN_MISSING.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(ErrorCode.TOKEN_MISSING, "Authentication required", Map.of(),
                RequestIdFilter.current(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
