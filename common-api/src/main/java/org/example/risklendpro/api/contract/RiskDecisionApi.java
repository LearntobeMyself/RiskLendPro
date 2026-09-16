package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.BehaviorScoreSnapshot;
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

    @GetMapping("/internal/behavior-scores/limit-multiplier")
    double getLimitMultiplier(@RequestParam("score") double score);
}