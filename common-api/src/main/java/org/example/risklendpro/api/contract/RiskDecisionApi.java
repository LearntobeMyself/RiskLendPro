package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.BehaviorScoreSnapshot;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.api.dto.RiskOverviewCounts;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 风控域对外提供的契约。由 risk-service 实现，loan/user 消费。
 */
public interface RiskDecisionApi {

    @GetMapping("/internal/risk-assessments/users/{userId}/latest-final")
    RiskAssessmentSummary getLatestFinalAssessment(@PathVariable("userId") Long userId);

    @GetMapping("/internal/behavior-scores/users/{userId}")
    BehaviorScoreSnapshot getBehaviorScore(@PathVariable("userId") Long userId);

    @PostMapping("/internal/behavior-scores/users/{userId}/recalculate")
    BehaviorScoreSnapshot recalculateBehaviorScore(@PathVariable("userId") Long userId);

    @PostMapping("/internal/behavior-scores/users/{userId}/activate")
    BehaviorScoreSnapshot activateBehaviorScore(@PathVariable("userId") Long userId,
                                                @RequestParam("idCard") String idCard);

    @GetMapping("/internal/risk-assessments/overview-counts")
    RiskOverviewCounts getOverviewCounts();
}