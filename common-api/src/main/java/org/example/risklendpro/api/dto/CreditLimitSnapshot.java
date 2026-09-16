package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

/**
 * 用户授信额度快照。属主服务：loan-service。
 */
public record CreditLimitSnapshot(
        Long userId,
        BigDecimal totalLimit,
        BigDecimal usedLimit,
        BigDecimal remainingLimit,
        BigDecimal overdueAmount,
        boolean hasOverdue,
        BigDecimal behaviorScore,
        Long bScoreUpdatedAt,
        boolean bCardEnabled,
        Long lastUpdateTime
) {
}