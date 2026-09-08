package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

public record LoanBehaviorSnapshot(
        Long userId,
        int activeLoanCount,
        int overdueRecordCount,
        BigDecimal outstandingAmount
) {
}
