package com.javadsamastery.controller;

import com.javadsamastery.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final boolean aiConfigured;
    private final Optional<DataSource> dataSource;

    @org.springframework.beans.factory.annotation.Autowired
    public HealthController(
            @Value("${gemini.api.key:#{null}}") String key,
            Optional<DataSource> dataSource) {
        String effectiveKey = key != null && !key.isBlank() ? key : System.getenv("GEMINI_API_KEY");
        this.aiConfigured = effectiveKey != null && !effectiveKey.isBlank();
        this.dataSource = dataSource;
    }

    public HealthController(@Value("${gemini.api.key:#{null}}") String key) {
        this(key, Optional.empty());
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean dbHealthy = true;
        if (dataSource.isPresent()) {
            try (Connection conn = dataSource.get().getConnection()) {
                dbHealthy = conn.isValid(2);
            } catch (Exception e) {
                dbHealthy = false;
            }
        }

        if (!dbHealthy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthResponse(
                            "DOWN",
                            "1.0.0",
                            7,
                            aiConfigured
                    ));
        }

        return ResponseEntity.ok(new HealthResponse(
                "UP",
                "1.0.0",
                7,
                aiConfigured
        ));
    }
}
