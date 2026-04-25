package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.LoanRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.LoanResponse;
import org.example.risklendpro.service.LoanService;
import org.example.risklendpro.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "借款/支用模块", description = "借款相关接口")
@RestController
@RequestMapping("/loan")
public class LoanController {
    
    @Autowired
    private LoanService loanService;
    
    @Operation(summary = "发起借款请求", description = "用户输入\"借款金额\"和\"还款期限\"，系统判断借款金额与用户剩余额度的关系")
    @PostMapping("/request")
    public CommonResponse<LoanResponse> request(@RequestBody LoanRequest request, HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        LoanResponse response = loanService.requestLoan(userId, request);
        if (response.getAutoApproved() != null && response.getAutoApproved()) {
            return CommonResponse.success("借款发放成功", response);
        } else {
            return CommonResponse.success("借款申请已提交，等待管理员审批", response);
        }
    }
    
    @Operation(summary = "获取用户借款记录", description = "获取用户的历史借款记录")
    @GetMapping("/user/history")
    public CommonResponse<Object> history(
            HttpServletRequest httpRequest,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        return CommonResponse.success("获取借款记录成功", loanService.getUserLoanHistory(userId, pageNum, pageSize));
    }
    
    @Operation(summary = "获取用户贷款申请记录", description = "获取用户提交的贷款申请记录，包含待审批和已审批结果")
    @GetMapping("/user/applications")
    public CommonResponse<Object> applications(
            HttpServletRequest httpRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        return CommonResponse.success("获取贷款申请记录成功", loanService.getUserLoanApplications(userId, status, pageNum, pageSize));
    }
}