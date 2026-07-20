package com.ledgersaas.backend.controller;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/premium")
public class PremiumFeatureController {

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('SUBSCRIBER_ACTIVE')")
    public ResponseEntity<Map<String, Object>> dashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Premium dashboard'a hoş geldiniz!",
                "user", authentication.getName(),
                "timestamp", LocalDateTime.now()));
    }
}
