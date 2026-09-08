package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

public record CreditLimitGrantCommand(
        Long userId,
        String assessmentId,
        BigDecimal approvedLimit
) {
}
