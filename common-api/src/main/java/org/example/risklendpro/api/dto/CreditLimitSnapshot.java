package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

public record CreditLimitSnapshot(
        Long userId,
        BigDecimal totalLimit,
        BigDecimal usedLimit,
        BigDecimal remainingLimit,
        BigDecimal overdueAmount,
        boolean hasOverdue,
        BigDecimal behaviorScore
) {
}
