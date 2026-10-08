package org.example.risklendpro.risk.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

public interface AdminRiskQueryService {

    Page<Map<String, Object>> getRiskList(Integer page, Integer size, String status);

    Map<String, Object> getRiskReport(String applyId);

    /**
     * @return 通知邮件是否发送成功。审批数据提交后再发信，失败不会回滚审批。
     */
    boolean approveRisk(RiskApproveRequest request);

    List<Map<String, Object>> getBCardMonitor();

    Map<String, Object> recalculateBCard(Long userId);
}
