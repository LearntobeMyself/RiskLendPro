package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.AntiFraudHandleRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.service.AdminRiskDataService;
import org.example.risklendpro.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "风控数据", description = "管理员风控数据查询与导出")
@RestController
@RequestMapping("/admin/risk")
public class AdminRiskDataController {

    @Autowired
    private AdminRiskDataService adminRiskDataService;

    @Operation(summary = "风控概览")
    @GetMapping("/overview")
    public CommonResponse<Map<String, Object>> overview() {
        return CommonResponse.success("查询成功", adminRiskDataService.getOverview());
    }

    @Operation(summary = "信用评分列表")
    @GetMapping("/credit-scores")
    public CommonResponse<Map<String, Object>> creditScores(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String riskLevel) {
        return CommonResponse.success("查询成功",
                adminRiskDataService.listCreditScores(page, size, userName, riskLevel));
    }

    @Operation(summary = "用户风控详情")
    @GetMapping("/users/{userId}")
    public CommonResponse<Map<String, Object>> userRisk(@PathVariable Long userId) {
        return CommonResponse.success("查询成功", adminRiskDataService.getUserRiskDetail(userId));
    }

    @Operation(summary = "反欺诈告警列表")
    @GetMapping("/anti-fraud")
    public CommonResponse<Map<String, Object>> antiFraud(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("查询成功", adminRiskDataService.listAntiFraud(page, size, status));
    }

    @Operation(summary = "处理反欺诈告警")
    @PostMapping("/anti-fraud/{id}/handle")
    public CommonResponse<Map<String, Object>> handleAntiFraud(
            @PathVariable String id, @RequestBody AntiFraudHandleRequest request) {
        return CommonResponse.success("处理成功",
                adminRiskDataService.handleAntiFraud(id, request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "多头借贷列表")
    @GetMapping("/multi-loan")
    public CommonResponse<Map<String, Object>> multiLoan(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer minActiveLoans) {
        return CommonResponse.success("查询成功",
                adminRiskDataService.listMultiLoan(page, size, minActiveLoans));
    }

    @Operation(summary = "征信报告列表")
    @GetMapping("/credit-reports")
    public CommonResponse<Map<String, Object>> creditReports(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("查询成功",
                adminRiskDataService.listCreditReports(page, size, status));
    }

    @Operation(summary = "用户征信报告详情")
    @GetMapping("/credit-reports/{userId}")
    public CommonResponse<Map<String, Object>> creditReportDetail(@PathVariable Long userId) {
        return CommonResponse.success("查询成功", adminRiskDataService.getCreditReportDetail(userId));
    }

    @Operation(summary = "风控数据导出")
    @GetMapping("/export")
    public CommonResponse<Map<String, Object>> export(
            @RequestParam(required = false) String status) {
        return CommonResponse.success("导出成功", adminRiskDataService.exportRiskData(status));
    }
}
