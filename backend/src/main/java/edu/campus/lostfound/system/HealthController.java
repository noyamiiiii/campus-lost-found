package edu.campus.lostfound.system;

import edu.campus.lostfound.common.ApiResponse;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final String version;

    public HealthController(@Value("${info.app.version:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping("/health")
    public ApiResponse<HealthData> health() {
        return ApiResponse.ok(new HealthData("UP", "campus-lost-found-backend", version, Instant.now()));
    }

    public record HealthData(String status, String service, String version, Instant timestamp) {
    }
}

