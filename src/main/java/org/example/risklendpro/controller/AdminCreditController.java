package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.BatchCreditAdjustRequest;
import org.example.risklendpro.pojo.request.LimitAdjustRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.service.AdminCreditQueryService;
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

@Tag(name = "信用额度", description = "管理员额度管理与统计")
@RestController
@RequestMapping("/admin/credit")
public class AdminCreditController {

    @Autowired
    private AdminCreditQueryService adminCreditQueryService;

    @Operation(summary = "额度统计")
    @GetMapping("/stats")
    public CommonResponse<Map<String, Object>> stats() {
        return CommonResponse.success("查询成功", adminCreditQueryService.getStats());
    }

    @Operation(summary = "额度列表")
    @GetMapping("/limits")
    public CommonResponse<Map<String, Object>> limits(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Boolean hasOverdue,
            @RequestParam(required = false) String status) {
        return CommonResponse.success("查询成功",
                adminCreditQueryService.listLimits(page, size, userName, phone, hasOverdue, status));
    }

    @Operation(summary = "调整用户额度")
    @PostMapping("/limits/{userId}/adjust")
    public CommonResponse<Map<String, Object>> adjust(
            @PathVariable Long userId, @RequestBody LimitAdjustRequest request) {
        return CommonResponse.success("调整成功",
                adminCreditQueryService.adjustLimit(userId, request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "批量调整额度")
    @PostMapping("/limits/batch-adjust")
    public CommonResponse<Map<String, Object>> batchAdjust(@RequestBody BatchCreditAdjustRequest request) {
        return CommonResponse.success("批量调整完成",
                adminCreditQueryService.batchAdjust(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "额度调整历史")
    @GetMapping("/limits/{userId}/history")
    public CommonResponse<Map<String, Object>> history(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return CommonResponse.success("查询成功", adminCreditQueryService.getAdjustHistory(userId, page, size));
    }

    @Operation(summary = "逾期调额规则")
    @GetMapping({"/overdue-rules", "/overdue-adjust-rules"})
    public CommonResponse<Map<String, Object>> overdueRules() {
        return CommonResponse.success("查询成功", adminCreditQueryService.getOverdueRules());
    }
}
