package com.carmarket.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Circuit breaker fallback responses.
 * Returns a 503 with a meaningful message so clients can handle gracefully.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {
    @GetMapping("/auth")
    public ResponseEntity<Map<String, String>> authFallback() {
        return fallback("auth-service");
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, String>> userFallback() {
        return fallback("user-service");
    }

    @GetMapping("/car")
    public ResponseEntity<Map<String, String>> carFallback() {
        return fallback("car-service");
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, String>> searchFallback() {
        return fallback("search-service");
    }

    private ResponseEntity<Map<String, String>> fallback(String service) {
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of(
                "error", "Service temporarily unavailable",
                "service", service,
                "message", "Please try again in a few moments"
            ));
    }
}
