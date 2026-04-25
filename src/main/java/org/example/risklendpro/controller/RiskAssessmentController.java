package org.example.risklendpro.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.CommonResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "授信评估模块", description = "风控评估相关接口")
@RestController
@RequestMapping("/risk/assessment")
public class RiskAssessmentController {
    
    @Operation(summary = "提交风控评估申请", description = "用户点击提交申请，启动风控评估流程")
    @PostMapping("/submit")
    public CommonResponse<RiskAssessmentResponse> submit(@RequestBody RiskAssessmentRequest request) {
        // 调用service方法
        // return CommonResponse.success(riskAssessmentService.submit(request));
        return CommonResponse.success(new RiskAssessmentResponse());
    }
    
    @Operation(summary = "轮询查询评估状态", description = "Android端轮询查询评估状态，当isFinal=true时停止轮询")
    @GetMapping("/status")
    public CommonResponse<RiskAssessmentStatusResponse> status(@RequestParam String applyId) {
        // 调用service方法
        // return CommonResponse.success(riskAssessmentService.getStatus(applyId));
        return CommonResponse.success(new RiskAssessmentStatusResponse());
    }
    
    @Operation(summary = "获取最终额度结果", description = "获取最终评估额度（如20,000）和失效日期")
    @GetMapping("/result")
    public CommonResponse<RiskAssessmentResultResponse> result(@RequestParam String applyId) {
        // 调用service方法
        // return CommonResponse.success(riskAssessmentService.getResult(applyId));
        return CommonResponse.success(new RiskAssessmentResultResponse());
    }
}