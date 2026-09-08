package org.example.risklendpro.loan.repay;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.loan.repay.RepaymentExecuteRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.loan.repay.RepaymentResponse;
import org.example.risklendpro.loan.repay.RepaymentService;
import org.example.risklendpro.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "账务与还款模块", description = "还款相关接口")
@RestController
@RequestMapping("/repayment")
public class RepaymentController {
    
    @Autowired
    private RepaymentService repaymentService;
    
    @Operation(summary = "查看还款计划", description = "用户查看自己的还款计划表，显示每期还款金额、是否逾期")
    @GetMapping("/plans")
    public CommonResponse<Object> plans(HttpServletRequest request) {
        Long userId = SecurityUtils.getUserIdFromRequest(request);
        return CommonResponse.success("获取还款计划成功", repaymentService.getRepaymentPlans(userId));
    }
    
    @Operation(summary = "执行还款", description = "用户点击\"还款\"，执行单期还款操作")
    @PostMapping("/execute")
    public CommonResponse<RepaymentResponse> execute(@RequestBody RepaymentExecuteRequest request) {
        RepaymentResponse response = repaymentService.executeRepayment(request);
        return CommonResponse.success("还款成功", response);
    }
    
    @Operation(summary = "获取还款记录详情", description = "获取指定还款计划的全部还款记录")
    @GetMapping("/record/{planId}")
    public CommonResponse<Object> record(
            @PathVariable Long planId,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("获取还款记录成功", repaymentService.getRepaymentRecords(planId, status));
    }
    
    @Operation(summary = "获取还款统计", description = "获取所有用户的还款成功统计（总数和已还款数）")
    @GetMapping("/statistics")
    public CommonResponse<Object> statistics() {
        return CommonResponse.success("获取还款统计成功", repaymentService.getRepaymentStatistics());
    }
    
    @Operation(summary = "获取逾期统计", description = "获取所有还款计划的逾期统计（总数和逾期数）")
    @GetMapping("/overdue")
    public CommonResponse<Object> overdue() {
        return CommonResponse.success("获取逾期统计成功", repaymentService.getOverdueStatistics());
    }
}
