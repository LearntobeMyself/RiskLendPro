package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentSubmitEligibilityResponse;

public interface RiskAssessmentService {
    /**
     * 提交风控评估申请
     */
    RiskAssessmentResponse submit(Long userId, RiskAssessmentRequest request);

    /**
     * 查询当前用户是否可提交新评估（重复申请预检）
     */
    RiskAssessmentSubmitEligibilityResponse getSubmitEligibility(Long userId);

    /**
     * 轮询查询评估状态（applyId 可选，未传时查该用户最新一条）
     */
    RiskAssessmentStatusResponse getStatusForUser(Long userId, String applyIdOptional);

    /**
     * 获取最终额度结果（applyId 可选，未传时查该用户最新一条）
     */
    RiskAssessmentResultResponse getResultForUser(Long userId, String applyIdOptional);
}