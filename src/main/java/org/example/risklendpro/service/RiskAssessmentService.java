package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.RiskAssessmentRequest;
import org.example.risklendpro.pojo.response.RiskAssessmentResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentStatusResponse;
import org.example.risklendpro.pojo.response.RiskAssessmentResultResponse;

public interface RiskAssessmentService {
    /**
     * 提交风控评估申请
     */
    RiskAssessmentResponse submit(Long userId, RiskAssessmentRequest request);

    /**
     * 轮询查询评估状态
     */
    RiskAssessmentStatusResponse getStatus(String applyId);

    /**
     * 获取最终额度结果
     */
    RiskAssessmentResultResponse getResult(String applyId);
}