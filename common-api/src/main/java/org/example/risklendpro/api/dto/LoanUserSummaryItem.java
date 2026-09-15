package org.example.risklendpro.api.dto;

import java.math.BigDecimal;

/**
 * 用户维度的贷款汇总，由 loan-service 聚合后供 user-service 管理端展示。
 */
public record LoanUserSummaryItem(
        Long userId,
        int loanCount,
        BigDecimal totalAmount,
        int activeLoanCount
) {
}