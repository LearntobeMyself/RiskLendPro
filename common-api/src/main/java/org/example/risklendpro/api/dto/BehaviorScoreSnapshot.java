package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

public record BehaviorScoreSnapshot(
        Long userId,
        BigDecimal baseScore,
        BigDecimal adjustment,
        BigDecimal finalScore,
        String riskLevel
) {
}
