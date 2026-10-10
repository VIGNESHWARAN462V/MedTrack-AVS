package com.medtrack.avs.service;

import com.medtrack.avs.dto.DepartmentResponse;
import com.medtrack.avs.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing hospital departments with safe application-level caching
 * for static reference data (Phase 20 Optimization).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Cacheable(value = "departments")
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getActiveDepartments() {
        log.debug("[Cache Miss] Fetching active departments from database");
        return departmentRepository.findAll().stream()
                .filter(d -> d.isActive())
                .map(d -> DepartmentResponse.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .code(d.getCode())
                        .description(d.getDescription())
                        .active(d.isActive())
                        .createdAt(d.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "departments", allEntries = true)
    public void evictDepartmentsCache() {
        log.info("[Cache Evict] Evicting departments reference cache");
    }
}
