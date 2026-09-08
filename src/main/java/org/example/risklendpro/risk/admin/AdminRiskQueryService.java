package org.example.risklendpro.risk.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

public interface AdminRiskQueryService {

    Page<Map<String, Object>> getRiskList(Integer page, Integer size, String status);

    Map<String, Object> getRiskReport(String applyId);

    void approveRisk(RiskApproveRequest request);

    List<Map<String, Object>> getBCardMonitor();

    Map<String, Object> recalculateBCard(Long userId);
}
