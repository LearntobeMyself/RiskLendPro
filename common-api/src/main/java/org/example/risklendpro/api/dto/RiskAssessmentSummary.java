package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

public record RiskAssessmentSummary(
        String assessmentId,
        Long userId,
        String status,
        String systemDecision,
        Integer totalScore,
        BigDecimal approvedLimit,
        boolean finalResult
) {
}
