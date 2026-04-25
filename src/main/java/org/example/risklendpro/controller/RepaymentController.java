package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.RepaymentExecuteRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.RepaymentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "账务与还款模块", description = "还款相关接口")
@RestController
@RequestMapping("/repayment")
public class RepaymentController {
    
    @Operation(summary = "查看还款计划", description = "用户查看自己的还款计划表，显示每期还款金额、是否逾期")
    @GetMapping("/plans")
    public CommonResponse<Object> plans(@RequestParam Long userId) {
        // 调用service方法
        // return CommonResponse.success(repaymentService.getRepaymentPlans(userId));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "执行还款", description = "用户点击\"还款\"，执行单期还款操作")
    @PostMapping("/execute")
    public CommonResponse<RepaymentResponse> execute(@RequestBody RepaymentExecuteRequest request) {
        // 调用service方法
        // return CommonResponse.success(repaymentService.executeRepayment(request));
        return CommonResponse.success(new RepaymentResponse());
    }
    
    @Operation(summary = "获取还款记录详情", description = "获取指定还款计划的全部还款记录")
    @GetMapping("/record/{planId}")
    public CommonResponse<Object> record(
            @PathVariable Long planId,
            @RequestParam(required = false) String status) {
        // 调用service方法
        // return CommonResponse.success(repaymentService.getRepaymentRecords(planId, status));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取还款提醒", description = "获取用户还款提醒，邮件通知即将到期的还款")
    @GetMapping("/reminder")
    public CommonResponse<Void> reminder(@RequestParam Long userId) {
        // 调用service方法
        // return CommonResponse.success(repaymentService.sendRepaymentReminder(userId));
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取还款统计", description = "获取所有用户的还款成功统计（总数和已还款数）")
    @GetMapping("/statistics")
    public CommonResponse<Object> statistics() {
        // 调用service方法
        // return CommonResponse.success(repaymentService.getRepaymentStatistics());
        return CommonResponse.success(null);
    }
    
    @Operation(summary = "获取逾期统计", description = "获取所有还款计划的逾期统计（总数和逾期数）")
    @GetMapping("/overdue")
    public CommonResponse<Object> overdue() {
        // 调用service方法
        // return CommonResponse.success(repaymentService.getOverdueStatistics());
        return CommonResponse.success(null);
    }
}