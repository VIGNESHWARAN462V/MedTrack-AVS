package com.medtrack.avs.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsSessionStartRequest {
    @NotNull(message = "assetId is required")
    private Long assetId;
}
