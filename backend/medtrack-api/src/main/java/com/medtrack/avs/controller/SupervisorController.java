package com.medtrack.avs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/supervisor")
public class SupervisorController {

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, String>> getSupervisorDashboard() {
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Welcome to Supervisor Monitoring Console"
        ));
    }
}
