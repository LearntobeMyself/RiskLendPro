package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.BehaviorScoreSnapshot;
import org.example.risklendpro.api.dto.BorrowGateResult;
import org.example.risklendpro.api.dto.LimitMultiplierDecision;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.api.dto.RiskOverviewCounts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 风控域对外提供的契约。由 risk-service 实现，loan/user 消费。
 */
public interface RiskDecisionApi {

    @GetMapping("/internal/risk-assessments/users/{userId}/latest-final")
    RiskAssessmentSummary getLatestFinalAssessment(@PathVariable("userId") Long userId);

    /** 批量查询多用户最终授信评估（用户管理列表批量填充，避免 N+1）。 */
    @PostMapping("/internal/risk-assessments/users/latest-final/batch")
    Map<Long, RiskAssessmentSummary> listLatestFinalAssessments(@RequestBody List<Long> userIds);

    @GetMapping("/internal/behavior-scores/users/{userId}")
    BehaviorScoreSnapshot getBehaviorScore(@PathVariable("userId") Long userId);

    @PostMapping("/internal/behavior-scores/users/{userId}/recalculate")
    BehaviorScoreSnapshot recalculateBehaviorScore(@PathVariable("userId") Long userId);

    @PostMapping("/internal/behavior-scores/users/{userId}/activate")
    BehaviorScoreSnapshot activateBehaviorScore(@PathVariable("userId") Long userId,
                                                @RequestParam("idCard") String idCard);

    @GetMapping("/internal/risk-assessments/overview-counts")
    RiskOverviewCounts getOverviewCounts();

    /**
     * 查询指定用户的 B 卡额度系数决策。
     *
     * <p>额度系数查询必须携带 userId：可信度（scoreReliable）、数据状态（dataStatus）、
     * 决策是否放行（decisionAllowed）与模型版本在同一决策点生成，
     * 对外不再暴露无 userId 的裸系数接口，避免不可信 B 分进入额度流程。</p>
     */
    @GetMapping("/internal/behavior-scores/users/{userId}/limit-multiplier")
    LimitMultiplierDecision getLimitMultiplierForUser(@PathVariable("userId") Long userId,
                                                      @RequestParam("score") double score);

    /**
     * 借款前授信/黑名单门禁。实现可随风控升级替换，调用方只消费 allowed。
     */
    @GetMapping("/internal/risk-assessments/users/{userId}/borrow-gate")
    BorrowGateResult getBorrowGate(@PathVariable("userId") Long userId);

    /** 用户已上传的身份证明材料类型（目录产品申请的身份校验用）。 */
    @GetMapping("/internal/supplement/users/{userId}/identity-proofs")
    List<String> listIdentityProofs(@PathVariable("userId") Long userId);
}