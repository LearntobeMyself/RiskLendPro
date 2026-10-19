package org.example.risklendpro.risk.bcard;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 A5 必需测试：PSI、缺失率、坏账率与阈值漂移告警。
 */
class BCardMonitorMetricsTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);

    @Test
    void psiIsZeroForIdenticalDistributions() {
        double[] baseline = IntStream.range(0, 100).mapToDouble(i -> i % 10).toArray();
        double[] current = baseline.clone();

        assertEquals(0.0, BCardMonitorMetrics.psi(baseline, current, 10), 1e-9);
    }

    @Test
    void psiDetectsShiftedDistribution() {
        double[] baseline = IntStream.range(0, 100).mapToDouble(i -> i).toArray();
        double[] current = IntStream.range(0, 100).mapToDouble(i -> i + 80).toArray();

        double psi = BCardMonitorMetrics.psi(baseline, current, 10);
        assertTrue(psi > BCardMonitorMetrics.PSI_WARN, "PSI 应显著大于预警线: " + psi);
    }

    @Test
    void psiReturnsNaNWhenSamplesInsufficient() {
        assertTrue(Double.isNaN(BCardMonitorMetrics.psi(new double[]{1, 2, 3}, new double[]{1, 2, 3}, 10)));
    }

    @Test
    void noSnapshotTriggersCriticalDataAlert() {
        List<BCardMonitorMetrics.AlertDecision> alerts = BCardMonitorMetrics.evaluate(
                DATE, 0, 0, 0, 0, null, 0);

        assertEquals(1, alerts.size());
        assertEquals("SNAPSHOT_COUNT", alerts.get(0).metric());
        assertEquals(BCardMonitorMetrics.LEVEL_CRITICAL, alerts.get(0).level());
    }

    @Test
    void missingRateTriggersWarnAndCritical() {
        List<BCardMonitorMetrics.AlertDecision> warn = BCardMonitorMetrics.evaluate(
                DATE, 100, 0.15, 0.01, 0.05, null, 0);
        assertTrue(warn.stream().anyMatch(a -> "MISSING_RATE".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_WARN.equals(a.level())));

        List<BCardMonitorMetrics.AlertDecision> critical = BCardMonitorMetrics.evaluate(
                DATE, 100, 0.30, 0.01, 0.05, null, 0);
        assertTrue(critical.stream().anyMatch(a -> "MISSING_RATE".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_CRITICAL.equals(a.level())));
    }

    @Test
    void psiHitRateBadRateAndThresholdDriftTriggerAlerts() {
        List<BCardMonitorMetrics.AlertDecision> alerts = BCardMonitorMetrics.evaluate(
                DATE, 100, 0.02, 0.35, 0.60, 0.55, 80);

        assertTrue(alerts.stream().anyMatch(a -> "SCORE_PSI".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_CRITICAL.equals(a.level())));
        assertTrue(alerts.stream().anyMatch(a -> "HIT_RATE".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_CRITICAL.equals(a.level())));
        assertTrue(alerts.stream().anyMatch(a -> "BAD_RATE_BIN".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_CRITICAL.equals(a.level())));
        assertTrue(alerts.stream().anyMatch(a -> "THRESHOLD_DRIFT".equals(a.metric())
                && BCardMonitorMetrics.LEVEL_CRITICAL.equals(a.level())));
    }

    @Test
    void healthyMetricsProduceNoAlerts() {
        List<BCardMonitorMetrics.AlertDecision> alerts = BCardMonitorMetrics.evaluate(
                DATE, 100, 0.02, 0.03, 0.05, 0.12, 5);

        assertTrue(alerts.isEmpty(), "健康指标不应产生告警: " + alerts);
    }
}