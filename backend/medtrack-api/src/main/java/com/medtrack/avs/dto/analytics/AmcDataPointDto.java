package com.medtrack.avs.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmcDataPointDto {
    private String dateLabel;
    private Instant timestamp;
    private Long successfulUpdates;
    private Long expectedUpdates;
    private Double continuityPercentage;
}
