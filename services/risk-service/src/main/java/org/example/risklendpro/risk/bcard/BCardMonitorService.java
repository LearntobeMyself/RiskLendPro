package org.example.risklendpro.risk.bcard;

import org.example.risklendpro.risk.score.BehaviorScoreEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * B 卡生产监控（阶段 A5）：评分分布 PSI、数据缺失率、决策命中率、分箱坏账率、阈值漂移。
 *
 * <p>指标不可用（样本不足/标签未成熟）时不告警，避免噪声误报。</p>
 */
@Service
public class BCardMonitorService {

    private static final Logger log = LoggerFactory.getLogger(BCardMonitorService.class);
    public static final String ACTIVE_FEATURE_VERSION = BCardFeatureServiceImpl.FEATURE_VERSION;

    private final JdbcTemplate primary;
    private final JdbcTemplate credit;

    @Autowired
    private BehaviorScoreEngine behaviorScoreEngine;

    public BCardMonitorService(@Qualifier("primaryDataSource") DataSource primaryDataSource,
                               @Qualifier("creditDataSource") DataSource creditDataSource) {
        this.primary = new JdbcTemplate(primaryDataSource);
        this.credit = new JdbcTemplate(creditDataSource);
    }

    public List<BCardMonitorMetrics.AlertDecision> runDaily(LocalDate date) {
        long snapshots = countLong(primary, "SELECT COUNT(*) FROM b_card_feature_snapshot "
                + "WHERE feature_version = ? AND as_of_date = ?", ACTIVE_FEATURE_VERSION, Date.valueOf(date));
        long degraded = countLong(primary, "SELECT COUNT(*) FROM b_card_feature_snapshot "
                        + "WHERE feature_version = ? AND as_of_date = ? "
                        + "AND data_quality_status IN ('DEGRADED','INSUFFICIENT')",
                ACTIVE_FEATURE_VERSION, Date.valueOf(date));
        double missingRate = BCardMonitorMetrics.rate(degraded, snapshots);

        long decisions = countLong(primary, "SELECT COUNT(*) FROM b_card_shadow_decision WHERE as_of_date = ?",
                Date.valueOf(date));
        long reduced = countLong(primary, "SELECT COUNT(*) FROM b_card_shadow_decision "
                        + "WHERE as_of_date = ? AND model_multiplier < 1",
                Date.valueOf(date));
        double hitRate = BCardMonitorMetrics.rate(reduced, decisions);

        double scorePsi = computeScorePsi(date);
        Double worstBinBadRate = computeWorstBinBadRate(date);
        double thresholdDrift = computeThresholdDrift();

        List<BCardMonitorMetrics.AlertDecision> alerts = BCardMonitorMetrics.evaluate(
                date, snapshots, missingRate, scorePsi, hitRate, worstBinBadRate, thresholdDrift);
        for (BCardMonitorMetrics.AlertDecision alert : alerts) {
            upsertAlert(alert);
        }
        log.info("B 卡监控完成 date={} snapshots={} degraded={} missingRate={} hitRate={} psi={} badRate={} drift={} alerts={}",
                date, snapshots, degraded, missingRate, hitRate, scorePsi, worstBinBadRate, thresholdDrift, alerts.size());
        return alerts;
    }

    private double computeScorePsi(LocalDate date) {
        double[] baseline = probabilities(date.minusDays(30), date.minusDays(1));
        double[] current = probabilities(date, date);
        return BCardMonitorMetrics.psi(baseline, current, 10);
    }

    private double[] probabilities(LocalDate from, LocalDate to) {
        List<Double> values = primary.query(
                "SELECT model_probability FROM b_card_shadow_decision "
                        + "WHERE as_of_date BETWEEN ? AND ? AND model_probability IS NOT NULL",
                (rs, rowNum) -> rs.getDouble("model_probability"), Date.valueOf(from), Date.valueOf(to));
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private Double computeWorstBinBadRate(LocalDate date) {
        List<Double> rates = primary.query(
                "SELECT AVG(s.label_30dpd_6m) AS bad_rate "
                        + "FROM b_card_shadow_decision d "
                        + "JOIN b_card_feature_snapshot s ON s.user_id = d.user_id "
                        + "  AND s.as_of_date = d.as_of_date AND s.feature_version = d.feature_version "
                        + "WHERE d.as_of_date = ? AND s.sample_status = 'LABELED' "
                        + "GROUP BY d.model_multiplier",
                (rs, rowNum) -> rs.getDouble("bad_rate"), Date.valueOf(date));
        if (rates.isEmpty()) {
            return null;
        }
        return rates.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    private double computeThresholdDrift() {
        double watch = behaviorScoreEngine.getThresholdWatch();
        double reduce = behaviorScoreEngine.getThresholdReduceLimit();
        List<Map<String, Object>> previous = credit.queryForList(
                "SELECT threshold_watch, threshold_reduce_limit FROM behavior_scoring_rules "
                        + "WHERE is_active = 1 ORDER BY activated_at DESC, id DESC LIMIT 2");
        if (previous.size() < 2) {
            return 0.0;
        }
        double prevWatch = ((Number) previous.get(1).get("threshold_watch")).doubleValue();
        double prevReduce = ((Number) previous.get(1).get("threshold_reduce_limit")).doubleValue();
        return Math.max(Math.abs(watch - prevWatch), Math.abs(reduce - prevReduce));
    }

    private void upsertAlert(BCardMonitorMetrics.AlertDecision alert) {
        primary.update("INSERT INTO b_card_monitor_alert "
                        + "(alert_date, metric, level, metric_value, threshold_value, detail, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN') "
                        + "ON DUPLICATE KEY UPDATE metric_value = VALUES(metric_value), "
                        + "threshold_value = VALUES(threshold_value), detail = VALUES(detail)",
                Date.valueOf(alert.alertDate()), alert.metric(), alert.level(),
                alert.value(), alert.threshold(), alert.detail());
    }

    public List<Map<String, Object>> listAlerts(String status) {
        if (status == null || status.isBlank()) {
            return primary.queryForList("SELECT * FROM b_card_monitor_alert ORDER BY id DESC LIMIT 200");
        }
        return primary.queryForList("SELECT * FROM b_card_monitor_alert WHERE status = ? ORDER BY id DESC LIMIT 200",
                status);
    }

    public int acknowledge(long alertId, String actor) {
        return primary.update("UPDATE b_card_monitor_alert SET status = 'ACKED', acked_by = ?, acked_at = NOW() "
                + "WHERE id = ? AND status = 'OPEN'", actor == null ? "system" : actor, alertId);
    }

    public int close(long alertId, String actor) {
        return primary.update("UPDATE b_card_monitor_alert SET status = 'CLOSED', "
                + "acked_by = COALESCE(acked_by, ?), acked_at = COALESCE(acked_at, NOW()) "
                + "WHERE id = ? AND status <> 'CLOSED'", actor == null ? "system" : actor, alertId);
    }

    private static long countLong(JdbcTemplate template, String sql, Object... args) {
        Long value = template.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }
}