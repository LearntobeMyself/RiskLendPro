package org.example.risklendpro.loan.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.LoanAdminQueryApi;
import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.OperationLogItem;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * loan-service 管理端操作日志契约实现（额度调整日志、贷款审批日志），供 user-service 聚合统一操作日志。
 */
@RestController
public class InternalLoanAdminQueryController implements LoanAdminQueryApi {

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private UserServiceClient userServiceClient;

    @Override
    public List<OperationLogItem> listCreditAdjustLogs() {
        return limitAdjustLogMapper.selectList(
                new QueryWrapper<LimitAdjustLog>().orderByDesc("adjust_time")
        ).stream().map(log -> new OperationLogItem(
                "CREDIT",
                "额度调整",
                log.getOperatorId(),
                resolveOperatorName(log.getOperatorId()),
                log.getReason(),
                log.getAdjustTime() == null ? null : log.getAdjustTime().getTime()
        )).toList();
    }

    @Override
    public List<OperationLogItem> listLoanApprovalLogs() {
        return loanMapper.selectList(
                new QueryWrapper<Loan>().isNotNull("approve_time").orderByDesc("approve_time")
        ).stream().map(loan -> new OperationLogItem(
                "LOAN",
                "贷款审批",
                loan.getOperatorId(),
                resolveOperatorName(loan.getOperatorId()),
                loan.getRejectReason(),
                loan.getApproveTime() == null ? null : loan.getApproveTime().getTime()
        )).toList();
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null || operatorId == 0L) {
            return "System";
        }
        try {
            AdminProfile admin = userServiceClient.getAdmin(operatorId);
            if (admin != null && admin.username() != null && !admin.username().isBlank()) {
                return admin.username();
            }
        } catch (Exception ignored) {
            // 管理员不存在或用户域不可用时回退占位名
        }
        return "Admin#" + operatorId;
    }
}
