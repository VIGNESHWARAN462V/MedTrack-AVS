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
public class FloorPlanUploadResponse {
    private Long floorId;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private String imageUrl;
    private String message;
    private Instant uploadedAt;
}
