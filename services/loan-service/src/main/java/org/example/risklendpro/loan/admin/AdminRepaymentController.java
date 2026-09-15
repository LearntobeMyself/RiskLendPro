package org.example.risklendpro.loan.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.loan.repay.RepaymentReminderRequest;
import org.example.risklendpro.loan.repay.RepaymentReportRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.loan.admin.AdminRepaymentQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "还款管理", description = "管理员还款计划与记录")
@RestController
@RequestMapping("/admin/repayment")
public class AdminRepaymentController {

    @Autowired
    private AdminRepaymentQueryService adminRepaymentQueryService;

    @Operation(summary = "还款统计汇总")
    @GetMapping("/summary")
    public CommonResponse<Map<String, Object>> summary() {
        return CommonResponse.success("查询成功", adminRepaymentQueryService.getSummary());
    }

    @Operation(summary = "还款计划列表")
    @GetMapping("/plans")
    public CommonResponse<Map<String, Object>> plans(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long loanId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("获取所有用户还款计划成功",
                adminRepaymentQueryService.listPlans(page, size, loanId, userName, status));
    }

    @Operation(summary = "还款计划详情")
    @GetMapping("/plans/{planId}")
    public CommonResponse<Map<String, Object>> planDetail(@PathVariable Long planId) {
        return CommonResponse.success("查询成功", adminRepaymentQueryService.getPlanDetail(planId));
    }

    @Operation(summary = "逾期统计")
    @GetMapping("/overdue-stats")
    public CommonResponse<Map<String, Object>> overdueStats() {
        return CommonResponse.success("查询成功", adminRepaymentQueryService.getOverdueStats());
    }

    @Operation(summary = "计划还款记录列表")
    @GetMapping("/plans/{planId}/records")
    public CommonResponse<Map<String, Object>> planRecords(
            @PathVariable Long planId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("获取还款记录成功",
                adminRepaymentQueryService.listPlanRecords(planId, page, size, status));
    }

    @Operation(summary = "还款记录统计")
    @GetMapping("/records/stats")
    public CommonResponse<Map<String, Object>> recordsStats(
            @RequestParam(required = false) Long planId) {
        return CommonResponse.success("查询成功", adminRepaymentQueryService.getRecordsStats(planId));
    }

    @Operation(summary = "实际还款记录")
    @GetMapping("/actual-records")
    public CommonResponse<Map<String, Object>> actualRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long loanId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return CommonResponse.success("查询成功",
                adminRepaymentQueryService.listActualRecords(page, size, loanId, userName, startDate, endDate));
    }

    @Operation(summary = "逾期记录列表")
    @GetMapping("/overdue-records")
    public CommonResponse<Map<String, Object>> overdueRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return CommonResponse.success("查询成功",
                adminRepaymentQueryService.listOverdueRecords(page, size));
    }

    @Operation(summary = "发送还款提醒")
    @PostMapping("/reminders")
    public CommonResponse<Map<String, Object>> reminders(@RequestBody RepaymentReminderRequest request) {
        return CommonResponse.success("提醒发送成功", adminRepaymentQueryService.sendReminder(request));
    }

    @Operation(summary = "生成还款报表")
    @PostMapping("/report")
    public CommonResponse<Map<String, Object>> report(@RequestBody RepaymentReportRequest request) {
        return CommonResponse.success("报表生成成功", adminRepaymentQueryService.generateReport(request));
    }
}
