package org.example.risklendpro.api.contract;

import org.example.risklendpro.api.dto.BehaviorScoreSnapshot;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

public interface RiskDecisionApi {

    @GetMapping("/internal/risk-assessments/users/{userId}/latest-final")
    RiskAssessmentSummary getLatestFinalAssessment(@PathVariable("userId") Long userId);

    @GetMapping("/internal/behavior-scores/users/{userId}")
    BehaviorScoreSnapshot getBehaviorScore(@PathVariable("userId") Long userId);

    @PostMapping("/internal/behavior-scores/users/{userId}/recalculate")
    BehaviorScoreSnapshot recalculateBehaviorScore(@PathVariable("userId") Long userId);
}
