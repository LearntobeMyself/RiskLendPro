package org.example.risklendpro.api.dto;

/**
 * B 卡监控所需的还款聚焦数据，由 loan-service 基于自身还款计划/期次表聚合后提供。
 * 若用户无有效计划，planId/recordStatus 等字段为 null，daysToDue 为 null。
 */
public record BCardRepaymentSnapshot(
        Long userId,
        int activePlanCount,
        Long planId,
        String planStatus,
        String overdueLevel,
        Integer overdueDays,
        Long dueDate,
        Integer currentPeriod,
        String recordStatus,
        Integer daysToDue
) {
}
