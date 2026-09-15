package org.example.risklendpro.loan.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.loan.borrow.BatchLoanApproveRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.loan.admin.AdminLoanQueryService;
import org.example.risklendpro.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "贷款管理", description = "管理员贷款申请与借款记录")
@RestController
@RequestMapping("/admin/loan")
public class AdminLoanController {

    @Autowired
    private AdminLoanQueryService adminLoanQueryService;

    @Operation(summary = "贷款申请详情")
    @GetMapping("/applications/{loanId}")
    public CommonResponse<Map<String, Object>> applicationDetail(
            @PathVariable Long loanId) {
        return CommonResponse.success("查询成功", adminLoanQueryService.getApplicationDetail(loanId));
    }

    @Operation(summary = "审批记录列表", description = "分页返回 list+total")
    @GetMapping("/approval-records")
    public CommonResponse<Map<String, Object>> approvalRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long loanId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return CommonResponse.success("查询成功",
                adminLoanQueryService.listApprovalRecords(page, size, userId, loanId, status, startDate, endDate));
    }

    @Operation(summary = "借款记录列表", description = "分页返回 list+total")
    @GetMapping("/records")
    public CommonResponse<Map<String, Object>> records(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return CommonResponse.success("查询成功",
                adminLoanQueryService.listRecords(page, size, status, userName, startDate, endDate));
    }

    @Operation(summary = "借款记录详情")
    @GetMapping("/records/{loanId}")
    public CommonResponse<Map<String, Object>> recordDetail(@PathVariable Long loanId) {
        return CommonResponse.success("查询成功", adminLoanQueryService.getRecordDetail(loanId));
    }

    @Operation(summary = "批量审批")
    @PostMapping("/batch-approve")
    public CommonResponse<Map<String, Object>> batchApprove(@RequestBody BatchLoanApproveRequest request) {
        return CommonResponse.success("批量审批完成",
                adminLoanQueryService.batchApprove(request, SecurityUtils.getAdminId()));
    }

    @Operation(summary = "借款记录导出")
    @GetMapping("/records/export")
    public CommonResponse<Map<String, Object>> exportRecords(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return CommonResponse.success("导出成功",
                adminLoanQueryService.exportRecords(status, userName, startDate, endDate));
    }
}
