package org.example.risklendpro.api.dto;

/**
 * 跨服务操作日志项，供 user-service 聚合统一的操作日志。
 */
public record OperationLogItem(
        String module,
        String action,
        Long operatorId,
        String operatorName,
        String detail,
        Long createTime
) {
}