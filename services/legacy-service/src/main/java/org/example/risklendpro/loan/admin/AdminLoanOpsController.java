package org.example.risklendpro.loan.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.loan.borrow.LoanApproveRequest;
import org.example.risklendpro.loan.borrow.LoanApproveResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理员借款审批与看板", description = "额度外借款审批、Vintage 与首页统计")
@RestController
@RequestMapping("/admin")
public class AdminLoanOpsController {

    @Autowired
    private AdminLoanOpsService adminLoanOpsService;

    @Operation(summary = "获取Vintage曲线数据", description = "获取Vintage曲线数据，用于分析不同放款月份的逾期率趋势")
    @GetMapping("/bi/vintage")
    public CommonResponse<Object> vintage() {
        return CommonResponse.success("获取Vintage数据成功", adminLoanOpsService.getVintageData());
    }

    @Operation(summary = "获取滚动率数据", description = "获取滚动率数据，用于分析逾期账户在各状态间的转化情况")
    @GetMapping("/bi/roll-rate")
    public CommonResponse<Object> rollRate() {
        return CommonResponse.success("获取滚动率数据成功", adminLoanOpsService.getRollRateData());
    }

    @Operation(summary = "获取管理员首页统计", description = "获取管理员首页的汇总统计数据")
    @GetMapping("/dashboard/stats")
    public CommonResponse<Object> dashboardStats() {
        return CommonResponse.success("获取统计数据成功", adminLoanOpsService.getDashboardStats());
    }

    @Operation(summary = "获取待审批贷款申请列表", description = "获取所有状态为PENDING_APPROVAL的贷款申请列表，管理员可进行审批操作")
    @GetMapping("/loan/pending-list")
    public CommonResponse<Object> loanPendingList(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {
        return CommonResponse.success("查询成功", adminLoanOpsService.getLoanPendingList(page, size));
    }

    @Operation(summary = "管理员审批贷款申请", description = "管理员审批用户提交的额度外贷款申请，审批通过后邮件通知用户")
    @PostMapping("/loan/approve")
    public CommonResponse<LoanApproveResponse> loanApprove(@RequestBody LoanApproveRequest request) {
        LoanApproveResponse response = adminLoanOpsService.approveLoan(request);
        return CommonResponse.success("审批成功，已邮件通知用户", response);
    }
}
