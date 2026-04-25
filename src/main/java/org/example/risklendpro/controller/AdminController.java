package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.LimitAdjustRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.MockDataUpdateRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.LimitAdjustResponse;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员审批与看板模块", description = "管理员审批和数据分析相关接口")
@RestController
@RequestMapping("/admin")
public class AdminController {
    
    @Operation(summary = "获取待审批列表", description = "获取所有状态为MANUAL_REVIEW的订单列表")
    @GetMapping("/risk/list")
    public CommonResponse<Object> riskList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        // 调用service方法
        // return CommonResponse.success(adminService.getRiskList(page, size, status));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取详细风控报告", description = "获取Python引擎生成的详细JSON报告，包含得分拆解和数据融合比对")
    @GetMapping("/risk/report/{applyId}")
    public CommonResponse<Object> riskReport(@PathVariable String applyId) {
        // 调用service方法
        // return CommonResponse.success(adminService.getRiskReport(applyId));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "管理员最终审批决策", description = "管理员给出最终贷款额度，审批结果邮件通知用户")
    @PostMapping("/risk/approve")
    public CommonResponse<Void> riskApprove(@RequestBody RiskApproveRequest request) {
        // 调用service方法
        // return CommonResponse.success(adminService.approveRisk(request));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取Vintage曲线数据", description = "获取Vintage曲线数据，用于分析不同放款月份的逾期率趋势")
    @GetMapping("/bi/vintage")
    public CommonResponse<Object> vintage() {
        // 调用service方法
        // return CommonResponse.success(adminService.getVintageData());
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取滚动率数据", description = "获取滚动率数据，用于分析逾期账户在各状态间的转化情况")
    @GetMapping("/bi/roll-rate")
    public CommonResponse<Object> rollRate() {
        // 调用service方法
        // return CommonResponse.success(adminService.getRollRateData());
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取管理员首页统计", description = "获取管理员首页的汇总统计数据")
    @GetMapping("/dashboard/stats")
    public CommonResponse<Object> dashboardStats() {
        // 调用service方法
        // return CommonResponse.success(adminService.getDashboardStats());
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取待审批贷款申请列表", description = "获取所有状态为PENDING_APPROVAL的贷款申请列表，管理员可进行审批操作")
    @GetMapping("/loan/pending-list")
    public CommonResponse<Object> loanPendingList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        // 调用service方法
        // return CommonResponse.success(adminService.getLoanPendingList(page, size));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "管理员审批贷款申请", description = "管理员审批用户提交的额度外贷款申请，审批通过后邮件通知用户")
    @PostMapping("/loan/approve")
    public CommonResponse<LoanApproveResponse> loanApprove(@RequestBody LoanApproveRequest request) {
        // 调用service方法
        // return CommonResponse.success(adminService.approveLoan(request));
        return CommonResponse.success(new LoanApproveResponse());
    }
    
    @Operation(summary = "管理员调整额度", description = "管理员手动或系统自动调整用户额度")
    @PostMapping("/limit/adjust")
    public CommonResponse<LimitAdjustResponse> limitAdjust(@RequestBody LimitAdjustRequest request) {
        // 调用service方法
        // return CommonResponse.success(adminService.adjustLimit(request));
        return CommonResponse.success(new LimitAdjustResponse());
    }
    
    @Operation(summary = "更新模拟数据", description = "管理员为某个身份证号设置\"黑名单\"或\"逾期次数\"等模拟数据")
    @PostMapping("/mock/update")
    public CommonResponse<Void> mockUpdate(@RequestBody MockDataUpdateRequest request) {
        // 调用service方法
        // return CommonResponse.success(adminService.updateMockData(request));
        return CommonResponse.success(null);
    }
}