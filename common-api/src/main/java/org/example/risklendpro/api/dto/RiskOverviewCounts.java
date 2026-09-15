package org.example.risklendpro.api.dto;

/**
 * 风控总览统计数据，由 risk-service 提供，供 loan-service 看板聚合。
 */
public record RiskOverviewCounts(
        long totalApplications,
        long pendingReview
) {
}