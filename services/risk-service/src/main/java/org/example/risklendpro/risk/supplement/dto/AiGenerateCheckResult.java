package org.example.risklendpro.risk.supplement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateCheckResult {
    private boolean aiGenerated;
    private double confidence;
    private String reason;
}
