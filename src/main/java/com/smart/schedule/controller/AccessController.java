package com.smart.schedule.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Minimal protected endpoints used to verify role separation.
 * Business controllers will be added in later stages.
 */
@RestController
@RequestMapping("/api")
public class AccessController {

    @GetMapping("/admin/ping")
    public Map<String, String> adminPing() {
        return Map.of("role", "ADMIN", "message", "Admin access granted");
    }

    @GetMapping("/doctor/ping")
    public Map<String, String> doctorPing() {
        return Map.of("role", "DOCTOR", "message", "Doctor access granted");
    }

    @GetMapping("/receptionist/ping")
    public Map<String, String> receptionistPing() {
        return Map.of("role", "RECEPTIONIST", "message", "Receptionist access granted");
    }
}
