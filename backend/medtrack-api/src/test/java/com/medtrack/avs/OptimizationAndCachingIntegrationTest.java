package com.medtrack.avs;

import com.medtrack.avs.dto.DepartmentResponse;
import com.medtrack.avs.service.DepartmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OptimizationAndCachingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("Department reference data is cached in application cache")
    void testDepartmentCaching() {
        // Evict first to ensure clean state
        departmentService.evictDepartmentsCache();

        Cache departmentsCache = cacheManager.getCache("departments");
        assertThat(departmentsCache).isNotNull();

        // First call populates cache
        List<DepartmentResponse> depts1 = departmentService.getActiveDepartments();
        assertThat(depts1).isNotEmpty();

        // Verify cache now contains entry
        assertThat(departmentsCache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY)).isNotNull();

        // Second call should return identical cached data
        List<DepartmentResponse> depts2 = departmentService.getActiveDepartments();
        assertThat(depts2).hasSameSizeAs(depts1);
    }

    @Test
    @DisplayName("Actuator sensitive endpoints are protected and not publicly exposed")
    void testActuatorSecurity() throws Exception {
        // Sensitive actuator endpoints like /actuator/env must not be exposed without authentication
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is(not(200)));

        // Beans, configprops, threaddump must not be accessible
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is(not(200)));

        mockMvc.perform(get("/actuator/configprops"))
                .andExpect(status().is(not(200)));
    }
}
