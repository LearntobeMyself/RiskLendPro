package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.OperationLogItem;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 风控域管理端查询（风控审批日志）。由 risk-service 实现，供 user-service 聚合操作日志。
 */
public interface RiskAdminQueryApi {

    @GetMapping("/internal/admin-logs/risk-approval")
    List<OperationLogItem> listRiskApprovalLogs();
}