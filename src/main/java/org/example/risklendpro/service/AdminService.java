package org.example.risklendpro.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import java.util.Map;
import java.util.List;

public interface AdminService {
    /**
     * 获取待审批列表
     */
    Page<Map<String, Object>> getRiskList(Integer page, Integer size, String status);

    /**
     * 获取详细风控报告
     */
    Map<String, Object> getRiskReport(String applyId);

    /**
     * 管理员最终审批决策
     */
    void approveRisk(RiskApproveRequest request);

    /**
     * 获取Vintage曲线数据
     */
    Map<String, Object> getVintageData();

    /**
     * 获取滚动率数据
     */
    Map<String, Object> getRollRateData();

    /**
     * 获取管理员首页统计
     */
    Map<String, Object> getDashboardStats();

    /**
     * 获取待审批贷款申请列表
     */
    Page<Map<String, Object>> getLoanPendingList(Integer page, Integer size);

    /**
     * 管理员审批贷款申请
     */
    LoanApproveResponse approveLoan(LoanApproveRequest request);

    /**
     * B 卡贷后监控列表（已启用 B 卡用户）
     */
    List<Map<String, Object>> getBCardMonitor();

    /**
     * 手动重算 B 卡分数（演示/seed 后刷新）
     */
    Map<String, Object> recalculateBCard(Long userId);

}
