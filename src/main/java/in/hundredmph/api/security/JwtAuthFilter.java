package in.hundredmph.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.hundredmph.api.common.ApiException;
import in.hundredmph.api.common.ErrorCode;
import in.hundredmph.api.common.ErrorResponse;
import in.hundredmph.api.common.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a Bearer access token into an authenticated principal.
 *
 * <p>A malformed or expired token is rejected here rather than left to Spring's
 * default entry point, so the client sees the same §7.1 envelope — and a
 * distinct {@code token_expired} code — that it sees everywhere else. That code
 * is what tells it to refresh instead of bouncing the user to the login screen.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER.length()).trim();
        AuthPrincipal principal;
        try {
            principal = jwtService.verifyAccessToken(token);
        } catch (ApiException ex) {
            SecurityContextHolder.clearContext();
            writeError(request, response, ex);
            return;
        }

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, ApiException ex)
            throws IOException {
        ErrorCode code = ex.code();
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(code, ex.getMessage(), Map.of(), RequestIdFilter.current(request));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
