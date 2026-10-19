package org.example.risklendpro.risk.bcard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 监控指标与告警规则（阶段 A5，纯函数，便于单测）。
 *
 * <p>覆盖 PSI、缺失率、命中率、分箱坏账率与阈值漂移；指标不可用（数据不足）时
 * 返回 null/NaN 且不产生告警，避免用噪声触发误报。</p>
 */
public final class BCardMonitorMetrics {

    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_CRITICAL = "CRITICAL";

    public static final double MISSING_WARN = 0.10;
    public static final double MISSING_CRITICAL = 0.25;
    public static final double PSI_WARN = 0.10;
    public static final double PSI_CRITICAL = 0.25;
    public static final double HIT_RATE_WARN = 0.30;
    public static final double HIT_RATE_CRITICAL = 0.50;
    public static final double BAD_RATE_WARN = 0.30;
    public static final double BAD_RATE_CRITICAL = 0.50;
    public static final double THRESHOLD_DRIFT_WARN = 30.0;
    public static final double THRESHOLD_DRIFT_CRITICAL = 60.0;
    public static final int MIN_SAMPLES = 30;

    private BCardMonitorMetrics() {
    }

    /** 以 baseline 的分位数切箱，计算 current 相对 baseline 的 PSI；样本不足返回 NaN。 */
    public static double psi(double[] baseline, double[] current, int bins) {
        if (baseline == null || current == null
                || baseline.length < MIN_SAMPLES || current.length < MIN_SAMPLES) {
            return Double.NaN;
        }
        double[] sorted = baseline.clone();
        Arrays.sort(sorted);
        List<Double> edges = new ArrayList<>();
        edges.add(Double.NEGATIVE_INFINITY);
        for (int i = 1; i < bins; i++) {
            int idx = (int) Math.round((double) i / bins * (sorted.length - 1));
            double edge = sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
            if (edge > edges.get(edges.size() - 1)) {
                edges.add(edge);
            }
        }
        edges.add(Double.POSITIVE_INFINITY);

        double[] baseHist = histogram(sorted, edges);
        double[] curHist = histogram(current, edges);
        double psi = 0.0;
        for (int i = 0; i < baseHist.length; i++) {
            double b = Math.max(baseHist[i], 1e-6);
            double c = Math.max(curHist[i], 1e-6);
            psi += (c - b) * Math.log(c / b);
        }
        return psi;
    }

    private static double[] histogram(double[] values, List<Double> edges) {
        double[] counts = new double[edges.size() - 1];
        for (double value : values) {
            int bin = binIndex(value, edges);
            counts[bin]++;
        }
        for (int i = 0; i < counts.length; i++) {
            counts[i] /= values.length;
        }
        return counts;
    }

    private static int binIndex(double value, List<Double> edges) {
        for (int i = 1; i < edges.size(); i++) {
            if (value < edges.get(i)) {
                return i - 1;
            }
        }
        return edges.size() - 2;
    }

    public static double rate(long numerator, long denominator) {
        return denominator <= 0 ? Double.NaN : (double) numerator / denominator;
    }

    /** 依据当日指标生成告警（按严重度从高到低）。 */
    public static List<AlertDecision> evaluate(LocalDate date,
                                               long snapshotCount,
                                               double missingRate,
                                               double scorePsi,
                                               double hitRate,
                                               Double worstBinBadRate,
                                               double thresholdDrift) {
        List<AlertDecision> alerts = new ArrayList<>();
        if (snapshotCount <= 0) {
            alerts.add(new AlertDecision(date, "SNAPSHOT_COUNT", LEVEL_CRITICAL, 0, 1,
                    "当日没有 BF_V2.2 快照，数据链路可能中断"));
            return alerts;
        }
        if (!Double.isNaN(missingRate)) {
            if (missingRate >= MISSING_CRITICAL) {
                alerts.add(new AlertDecision(date, "MISSING_RATE", LEVEL_CRITICAL, missingRate, MISSING_CRITICAL,
                        "数据质量不合格比例过高"));
            } else if (missingRate >= MISSING_WARN) {
                alerts.add(new AlertDecision(date, "MISSING_RATE", LEVEL_WARN, missingRate, MISSING_WARN,
                        "数据质量不合格比例超过预警线"));
            }
        }
        if (!Double.isNaN(scorePsi)) {
            if (scorePsi >= PSI_CRITICAL) {
                alerts.add(new AlertDecision(date, "SCORE_PSI", LEVEL_CRITICAL, scorePsi, PSI_CRITICAL,
                        "模型分数分布显著漂移"));
            } else if (scorePsi >= PSI_WARN) {
                alerts.add(new AlertDecision(date, "SCORE_PSI", LEVEL_WARN, scorePsi, PSI_WARN,
                        "模型分数分布出现漂移"));
            }
        }
        if (!Double.isNaN(hitRate)) {
            if (hitRate >= HIT_RATE_CRITICAL) {
                alerts.add(new AlertDecision(date, "HIT_RATE", LEVEL_CRITICAL, hitRate, HIT_RATE_CRITICAL,
                        "降额/拒绝命中率异常偏高"));
            } else if (hitRate >= HIT_RATE_WARN) {
                alerts.add(new AlertDecision(date, "HIT_RATE", LEVEL_WARN, hitRate, HIT_RATE_WARN,
                        "降额/拒绝命中率偏高"));
            }
        }
        if (worstBinBadRate != null) {
            if (worstBinBadRate >= BAD_RATE_CRITICAL) {
                alerts.add(new AlertDecision(date, "BAD_RATE_BIN", LEVEL_CRITICAL, worstBinBadRate,
                        BAD_RATE_CRITICAL, "分箱坏账率超过严重阈值"));
            } else if (worstBinBadRate >= BAD_RATE_WARN) {
                alerts.add(new AlertDecision(date, "BAD_RATE_BIN", LEVEL_WARN, worstBinBadRate,
                        BAD_RATE_WARN, "分箱坏账率超过预警线"));
            }
        }
        if (!Double.isNaN(thresholdDrift)) {
            double abs = Math.abs(thresholdDrift);
            if (abs >= THRESHOLD_DRIFT_CRITICAL) {
                alerts.add(new AlertDecision(date, "THRESHOLD_DRIFT", LEVEL_CRITICAL, thresholdDrift,
                        THRESHOLD_DRIFT_CRITICAL, "阈值相对基线漂移过大"));
            } else if (abs >= THRESHOLD_DRIFT_WARN) {
                alerts.add(new AlertDecision(date, "THRESHOLD_DRIFT", LEVEL_WARN, thresholdDrift,
                        THRESHOLD_DRIFT_WARN, "阈值相对基线出现漂移"));
            }
        }
        return alerts;
    }

    public record AlertDecision(LocalDate alertDate, String metric, String level,
                                double value, double threshold, String detail) {
    }
}