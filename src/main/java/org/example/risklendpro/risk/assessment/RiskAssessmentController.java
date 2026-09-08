package org.example.risklendpro.risk.assessment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.risk.assessment.RiskAssessmentRequest;
import org.example.risklendpro.common.api.CommonResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentStatusResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentResultResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentSubmitEligibilityResponse;
import org.example.risklendpro.risk.assessment.RiskAssessmentService;
import org.example.risklendpro.common.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "授信评估模块", description = "风控评估相关接口")
@RestController
@RequestMapping("/risk/assessment")
public class RiskAssessmentController {
    
    @Autowired
    private RiskAssessmentService riskAssessmentService;
    
    @Operation(summary = "提交风控评估申请", description = "用户点击提交申请，启动风控评估流程")
    @PostMapping("/submit")
    public CommonResponse<RiskAssessmentResponse> submit(@RequestBody RiskAssessmentRequest request, HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        RiskAssessmentResponse response = riskAssessmentService.submit(userId, request);
        return CommonResponse.success("申请已受理，风控评估启动", response);
    }

    @Operation(summary = "可否提交新评估", description = "重复申请预检：已通过/处理中/30天冷却期内不可提交")
    @GetMapping("/submit-eligibility")
    public CommonResponse<RiskAssessmentSubmitEligibilityResponse> submitEligibility(HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        RiskAssessmentSubmitEligibilityResponse response = riskAssessmentService.getSubmitEligibility(userId);
        return CommonResponse.success("提交资格查询", response);
    }
    
    @Operation(summary = "轮询查询评估状态", description = "applyId 可选；未传时按 Token 查该用户最新评估，含补充材料状态")
    @GetMapping("/status")
    public CommonResponse<RiskAssessmentStatusResponse> status(
            @RequestParam(required = false) String applyId,
            HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        RiskAssessmentStatusResponse response = riskAssessmentService.getStatusForUser(userId, applyId);
        return CommonResponse.success("风控信息查询", response);
    }
    
    @Operation(summary = "获取最终额度结果", description = "applyId 可选；未传时按 Token 查该用户最新终态评估")
    @GetMapping("/result")
    public CommonResponse<RiskAssessmentResultResponse> result(
            @RequestParam(required = false) String applyId,
            HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.getUserIdFromRequest(httpRequest);
        RiskAssessmentResultResponse response = riskAssessmentService.getResultForUser(userId, applyId);
        return CommonResponse.success("获取评估结果成功", response);
    }
}
