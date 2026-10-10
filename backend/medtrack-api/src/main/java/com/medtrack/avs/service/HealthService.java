package com.medtrack.avs.service;

import com.medtrack.avs.dto.HealthResponse;
import com.medtrack.avs.util.AppConstants;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthResponse getHealthStatus() {
        return HealthResponse.builder()
                .status("UP")
                .service(AppConstants.APP_NAME)
                .build();
    }
}
