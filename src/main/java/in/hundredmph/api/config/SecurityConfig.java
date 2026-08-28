package in.hundredmph.api.config;

import in.hundredmph.api.security.JwtAuthFilter;
import in.hundredmph.api.security.RestAccessDeniedHandler;
import in.hundredmph.api.security.RestAuthEntryPoint;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final Environment environment;

    public SecurityConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * BCrypt at cost 12. The README calls for Argon2id; BCrypt is chosen here
     * because it needs no native library and the cost is tunable, and because
     * Spring's DelegatingPasswordEncoder prefix makes moving to Argon2 later a
     * re-hash-on-next-login, not a migration.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthFilter jwtAuthFilter,
                                            RestAuthEntryPoint authEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                // No cookies, no sessions: the token is the whole credential, so
                // there is nothing for a CSRF attack to ride on.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/v1/health", "/actuator/health/**").permitAll()
                        // Sign-in and recovery are, necessarily, pre-auth.
                        .requestMatchers("/v1/auth/**").permitAll()
                        // Program list backs the pre-selection screen (README §5.3).
                        .requestMatchers(HttpMethod.GET, "/v1/programs").permitAll()
                        .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String configured = environment.getProperty("app.cors.allowed-origins", "");
        List<String> origins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);

        // Expo's web dev server takes whichever port is free (8081, 8082, …),
        // so pinning ports here means the web build breaks the moment one is
        // already in use. In development any loopback origin is allowed; in
        // production only the explicit list above applies.
        if (environment.getProperty("app.cors.allow-local-dev-origins", Boolean.class, true)) {
            // Loopback covers the web build and the iOS simulator; the private
            // ranges cover Expo web opened at the dev machine's LAN address,
            // which is what a second machine or a phone browser actually sends.
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:[*]",
                    "http://127.0.0.1:[*]",
                    "http://192.168.*.*:[*]",
                    "http://10.*.*.*:[*]",
                    "http://172.*.*.*:[*]"));
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
