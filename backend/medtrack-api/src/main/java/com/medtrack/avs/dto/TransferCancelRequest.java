package com.medtrack.avs.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferCancelRequest {

    @Size(max = 1000, message = "Cancellation reason cannot exceed 1000 characters")
    private String reason;
}
