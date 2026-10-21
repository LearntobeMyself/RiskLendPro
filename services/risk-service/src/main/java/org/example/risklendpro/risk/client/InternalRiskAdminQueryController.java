package org.example.risklendpro.risk.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.RiskAdminQueryApi;
import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.OperationLogItem;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * risk-service 管理端操作日志契约实现（风控审批日志），供 user-service 聚合统一操作日志。
 */
@RestController
public class InternalRiskAdminQueryController implements RiskAdminQueryApi {

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private UserServiceClient userServiceClient;

    @Override
    public List<OperationLogItem> listRiskApprovalLogs() {
        List<RiskAssessment> assessments = riskAssessmentMapper.selectList(
                new QueryWrapper<RiskAssessment>().isNotNull("approval_time").orderByDesc("approval_time"));
        // 按 operatorId 去重后解析姓名，替代每行一次 getAdmin 的 N+1
        Map<Long, String> operatorNames = new HashMap<>();
        Set<Long> distinct = new HashSet<>();
        for (RiskAssessment ra : assessments) {
            Long operatorId = ra.getOperatorId();
            if (operatorId != null && operatorId != 0L) {
                distinct.add(operatorId);
            }
        }
        for (Long operatorId : distinct) {
            operatorNames.put(operatorId, resolveOperatorName(operatorId));
        }
        return assessments.stream().map(ra -> {
            Long operatorId = ra.getOperatorId();
            String operatorName = operatorId == null || operatorId == 0L
                    ? "System"
                    : operatorNames.getOrDefault(operatorId, "Admin#" + operatorId);
            return new OperationLogItem(
                    "RISK",
                    "风控终审",
                    operatorId,
                    operatorName,
                    ra.getAuditRemark(),
                    ra.getApprovalTime() == null ? null : ra.getApprovalTime().getTime()
            );
        }).toList();
    }

    private String resolveOperatorName(Long operatorId) {
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
