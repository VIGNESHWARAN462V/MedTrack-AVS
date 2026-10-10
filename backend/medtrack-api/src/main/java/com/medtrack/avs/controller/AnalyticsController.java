package com.medtrack.avs.controller;

import com.medtrack.avs.dto.analytics.*;
import com.medtrack.avs.entity.User;
import com.medtrack.avs.exception.BadRequestException;
import com.medtrack.avs.repository.UserRepository;
import com.medtrack.avs.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long assetId
    ) {
        User currentUser = getCurrentUser();
        Instant fromInstant = parseTimestamp(from, false);
        Instant toInstant = parseTimestamp(to, true);
        AnalyticsOverviewResponse response = analyticsService.calculateOverview(fromInstant, toInstant, departmentId, assetId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/amc")
    public ResponseEntity<AmcMetricResponse> getAmc(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long assetId
    ) {
        User currentUser = getCurrentUser();
        Instant fromInstant = parseTimestamp(from, false);
        Instant toInstant = parseTimestamp(to, true);
        AmcMetricResponse response = analyticsService.calculateAmc(fromInstant, toInstant, departmentId, assetId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/hfi")
    public ResponseEntity<HfiMetricResponse> getHfi(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long assetId
    ) {
        User currentUser = getCurrentUser();
        Instant fromInstant = parseTimestamp(from, false);
        Instant toInstant = parseTimestamp(to, true);
        HfiMetricResponse response = analyticsService.calculateHfi(fromInstant, toInstant, departmentId, assetId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/gub")
    public ResponseEntity<GubMetricResponse> getGub(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long departmentId
    ) {
        User currentUser = getCurrentUser();
        Instant fromInstant = parseTimestamp(from, false);
        Instant toInstant = parseTimestamp(to, true);
        GubMetricResponse response = analyticsService.calculateGub(fromInstant, toInstant, departmentId, currentUser);
        return ResponseEntity.ok(response);
    }

    private Instant parseTimestamp(String input, boolean endOfDay) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        String s = input.trim();
        try {
            if (s.contains("T")) {
                return Instant.parse(s);
            } else {
                LocalDate d = LocalDate.parse(s);
                return endOfDay
                        ? d.atTime(LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant()
                        : d.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
        } catch (Exception e) {
            throw new BadRequestException("Invalid timestamp format: '" + input + "'. Expected ISO-8601 format (e.g. '2026-10-01T00:00:00Z' or '2026-10-01')");
        }
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("Full authentication is required to access analytics endpoints");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found: " + auth.getName()));
    }
}
