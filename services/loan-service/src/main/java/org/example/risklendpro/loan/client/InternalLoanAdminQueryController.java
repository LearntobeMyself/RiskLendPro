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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
        List<LimitAdjustLog> logs = limitAdjustLogMapper.selectList(
                new QueryWrapper<LimitAdjustLog>().orderByDesc("adjust_time"));
        Map<Long, String> operatorNames = resolveOperatorNames(
                logs.stream().map(LimitAdjustLog::getOperatorId).toList());
        return logs.stream().map(log -> new OperationLogItem(
                "CREDIT",
                "额度调整",
                log.getOperatorId(),
                operatorName(operatorNames, log.getOperatorId()),
                log.getReason(),
                log.getAdjustTime() == null ? null : log.getAdjustTime().getTime()
        )).toList();
    }

    @Override
    public List<OperationLogItem> listLoanApprovalLogs() {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().isNotNull("approve_time").orderByDesc("approve_time"));
        Map<Long, String> operatorNames = resolveOperatorNames(
                loans.stream().map(Loan::getOperatorId).toList());
        return loans.stream().map(loan -> new OperationLogItem(
                "LOAN",
                "贷款审批",
                loan.getOperatorId(),
                operatorName(operatorNames, loan.getOperatorId()),
                loan.getRejectReason(),
                loan.getApproveTime() == null ? null : loan.getApproveTime().getTime()
        )).toList();
    }

    /**
     * 按请求内去重后逐个解析操作人姓名：N 行日志最多调用 distinct(管理员数) 次 Feign，
     * 替代原先每行一次的 N+1 模式。单个查询失败不阻塞整体。
     */
    private Map<Long, String> resolveOperatorNames(List<Long> operatorIds) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> distinct = new HashSet<>();
        for (Long id : operatorIds) {
            if (id == null || id == 0L) {
                continue;
            }
            distinct.add(id);
        }
        for (Long id : distinct) {
            names.put(id, fetchAdminName(id));
        }
        return names;
    }

    private String fetchAdminName(Long operatorId) {
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

    private String operatorName(Map<Long, String> resolved, Long operatorId) {
        if (operatorId == null || operatorId == 0L) {
            return "System";
        }
        return resolved.getOrDefault(operatorId, "Admin#" + operatorId);
    }
}
