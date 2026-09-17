package com.sam.be.infrastructure.config;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal")
public class HealthCheckController {

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> healthz() {
        return ResponseEntity.ok(Map.of("status", "UP", "timestamp", Instant.now().toString()));
    }
}
