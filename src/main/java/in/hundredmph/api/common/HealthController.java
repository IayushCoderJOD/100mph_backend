package in.hundredmph.api.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /v1/health — the Phase 0 done-when check (README §9). */
@RestController
public class HealthController {

    @GetMapping("/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "100mph-api",
                "time", Instant.now().toString());
    }
}
