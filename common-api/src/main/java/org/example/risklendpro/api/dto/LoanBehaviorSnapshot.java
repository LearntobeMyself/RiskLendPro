package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

/**
 * 用户贷后行为快照，由 loan-service 依据其自有表聚合后经 Feign 提供给 risk 计算行为分。
 */
public record LoanBehaviorSnapshot(
        Long userId,
        int activeLoanCount,
        int overdueRecordCount,
        BigDecimal outstandingAmount,
        int maxOverdueDays,
        int overduePeriodCount,
        double onTimeRate,
        boolean historyAvailable
) {
}