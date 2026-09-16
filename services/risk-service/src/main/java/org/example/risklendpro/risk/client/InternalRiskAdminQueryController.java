package org.example.risklendpro.risk.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.RiskAdminQueryApi;
import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.OperationLogItem;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        return riskAssessmentMapper.selectList(
                new QueryWrapper<RiskAssessment>().isNotNull("approval_time").orderByDesc("approval_time")
        ).stream().map(ra -> {
            Long operatorId = ra.getOperatorId();
            String operatorName = resolveOperatorName(operatorId);
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
