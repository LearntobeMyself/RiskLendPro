package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.OperationLogItem;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 贷款域管理端查询（额度调整日志、贷款审批日志）。由 loan-service 实现，供 user-service 聚合操作日志。
 */
public interface LoanAdminQueryApi {

    @GetMapping("/internal/admin-logs/credit-adjust")
    List<OperationLogItem> listCreditAdjustLogs();

    @GetMapping("/internal/admin-logs/loan-approval")
    List<OperationLogItem> listLoanApprovalLogs();
}