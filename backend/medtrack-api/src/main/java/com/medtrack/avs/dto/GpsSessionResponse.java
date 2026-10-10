package com.medtrack.avs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsSessionResponse {
    private Long id;
    private Long assetId;
    private String assetCode;
    private String assetName;
    private Long startedByUserId;
    private String startedByUserName;
    private Instant startedAt;
    private Instant endedAt;
    private boolean active;
}
