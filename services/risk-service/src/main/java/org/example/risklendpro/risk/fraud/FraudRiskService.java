package org.example.risklendpro.risk.fraud;

import org.example.risklendpro.risk.credit.UserExternalFeatures;
import org.example.risklendpro.risk.score.CreditScoreEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 反欺诈 / 早期逾期预警评分（可解释规则版）。
 *
 * <p>输入三类信号：</p>
 * <ol>
 *   <li>黑名单命中等级（姓名+地域+出生年份）；</li>
 *   <li>外部征信特征（多头查询、换号天数、活跃贷款、历史拒绝、逾期代理、第三方评分）；</li>
 *   <li>B 卡动态特征（当前 DPD、12 个月最大 DPD、准时率、最近逾期距今、额度使用率）。</li>
 * </ol>
 *
 * <p>输出 0-100 分与 LOW/MEDIUM/HIGH/CRITICAL 等级，并给出命中的具体原因。
 * 规则透明、可审计；后续可将分数作为模型特征或替换为监督学习模型。</p>
 */
@Service
public class FraudRiskService {

    public static final String RULE_VERSION = "FRAUD_RULES_V1";

    public static final int CRITICAL_SCORE = 70;
    public static final int HIGH_SCORE = 45;
    public static final int MEDIUM_SCORE = 25;

    public enum Level { LOW, MEDIUM, HIGH, CRITICAL }

    /** 单条命中信号。 */
    public record Signal(String code, int points, String detail) {
    }

    /** 反欺诈评估结果。 */
    public record Result(int score, Level level, List<Signal> signals, String summary) {
        public boolean isHighRisk() {
            return level == Level.HIGH || level == Level.CRITICAL;
        }
    }

    /** A 卡申请时点：黑名单 + 外部特征 + 自填/后台收入偏差。 */
    public Result evaluateApplication(String selfIncomeLabel,
                                      CreditScoreEngine.BlacklistMatchResult blacklist,
                                      UserExternalFeatures external) {
        return evaluate(blacklist, external, null, 0, selfIncomeLabel);
    }

    /** 存量用户早筛：黑名单 + 外部特征 + B 卡动态 + 近 30 天申请次数。 */
    public Result evaluateUser(CreditScoreEngine.BlacklistMatchResult blacklist,
                               UserExternalFeatures external,
                               Map<String, Double> bcardFeatures,
                               int applications30d) {
        return evaluate(blacklist, external, bcardFeatures, applications30d, null);
    }

    private Result evaluate(CreditScoreEngine.BlacklistMatchResult blacklist,
                            UserExternalFeatures external,
                            Map<String, Double> bcard,
                            int applications30d,
                            String selfIncomeLabel) {
        List<Signal> signals = new ArrayList<>();

        // 1) 黑名单
        if (blacklist != null) {
            switch (blacklist.getMatchLevel()) {
                case FULL -> signals.add(new Signal("BLACKLIST_FULL", 80, "姓名+地域+出生年份命中黑名单"));
                case NAME_AREA -> signals.add(new Signal("BLACKLIST_NAME_AREA", 45, "姓名+地域命中黑名单"));
                case NAME_ONLY -> signals.add(new Signal("BLACKLIST_NAME_ONLY", 12, "仅姓名命中黑名单"));
                default -> { }
            }
        }

        // 2) 外部征信特征
        if (external != null) {
            int week = nvl(external.getCreditBureauWeek());
            int mon = nvl(external.getCreditBureauMon());
            int phoneDays = nvl(external.getDaysLastPhoneChange());
            int activeLoans = nvl(external.getActiveLoansCount());
            int refused = nvl(external.getPrevRefusedCount());
            int overdue = nvl(external.getLoanOverdueMax6m());
            double ext2 = doubleOf(external.getExtSource2());
            double ext3 = doubleOf(external.getExtSource3());
            double ccUtil = doubleOf(external.getCcUtilization());

            if (week >= 6) {
                signals.add(new Signal("MULTI_HEAD_WEEK", 18, "近 1 周征信查询 " + week + " 次"));
            } else if (week >= 3) {
                signals.add(new Signal("MULTI_HEAD_WEEK", 9, "近 1 周征信查询 " + week + " 次"));
            }
            if (mon >= 12) {
                signals.add(new Signal("MULTI_HEAD_MONTH", 14, "近 1 月征信查询 " + mon + " 次"));
            } else if (mon >= 6) {
                signals.add(new Signal("MULTI_HEAD_MONTH", 7, "近 1 月征信查询 " + mon + " 次"));
            }
            if (phoneDays > 0 && phoneDays < 30) {
                signals.add(new Signal("PHONE_CHANGE_RECENT", 14, "手机号最近 " + phoneDays + " 天更换过"));
            } else if (phoneDays > 0 && phoneDays < 90) {
                signals.add(new Signal("PHONE_CHANGE_RECENT", 7, "手机号最近 " + phoneDays + " 天更换过"));
            }
            if (activeLoans >= 5) {
                signals.add(new Signal("ACTIVE_LOANS_HIGH", 12, "活跃贷款 " + activeLoans + " 笔"));
            } else if (activeLoans >= 3) {
                signals.add(new Signal("ACTIVE_LOANS_HIGH", 6, "活跃贷款 " + activeLoans + " 笔"));
            }
            if (refused >= 3) {
                signals.add(new Signal("PREV_REFUSED_HIGH", 14, "历史被拒 " + refused + " 次"));
            } else if (refused >= 1) {
                signals.add(new Signal("PREV_REFUSED_HIGH", 5, "历史被拒 " + refused + " 次"));
            }
            if (overdue >= 3) {
                signals.add(new Signal("OVERDUE_PROXY_HIGH", 22, "近 6 月逾期次数代理 " + overdue));
            } else if (overdue >= 1) {
                signals.add(new Signal("OVERDUE_PROXY_HIGH", 12, "近 6 月逾期次数代理 " + overdue));
            }
            if (ccUtil >= 1.0) {
                signals.add(new Signal("CC_UTILIZATION_HIGH", 10, "信用卡使用率 " + ccUtil));
            } else if (ccUtil >= 0.8) {
                signals.add(new Signal("CC_UTILIZATION_HIGH", 6, "信用卡使用率 " + ccUtil));
            }
            if (ext2 > 0 && ext2 < 0.3) {
                signals.add(new Signal("EXT_SOURCE_2_LOW", 12, "第三方评分 A 偏低 " + ext2));
            }
            if (ext3 > 0 && ext3 < 0.3) {
                signals.add(new Signal("EXT_SOURCE_3_LOW", 10, "第三方评分 B 偏低 " + ext3));
            }

            // 3) 自填收入 vs 征信收入
            if (selfIncomeLabel != null) {
                double self = representativeIncome(selfIncomeLabel);
                double backend = external.getAmtIncomeTotal() == null
                        ? 0.0 : external.getAmtIncomeTotal().doubleValue() / 12.0;
                if (backend > 0 && self > 0) {
                    double over = (self - backend) / Math.max(backend, 1.0);
                    if (over > 0.5) {
                        signals.add(new Signal("INCOME_OVERSTATED", 18,
                                String.format("自填收入高于征信 %.0f%%", over * 100)));
                    } else if (over > 0.15) {
                        signals.add(new Signal("INCOME_OVERSTATED", 8,
                                String.format("自填收入高于征信 %.0f%%", over * 100)));
                    }
                }
            }
        }

        // 4) B 卡动态（存量用户早筛）
        if (bcard != null && !bcard.isEmpty()) {
            double currentDpd = value(bcard, "currentMaxDpd");
            double onTime = value(bcard, "onTimeRate12m");
            double utilization = value(bcard, "utilizationRate");
            Double daysSince = bcard.get("daysSinceLastOverdue");
            if (currentDpd >= 30) {
                signals.add(new Signal("BCARD_DPD_30_PLUS", 20, "当前最大 DPD " + (int) currentDpd));
            } else if (currentDpd >= 7) {
                signals.add(new Signal("BCARD_DPD_7_PLUS", 10, "当前最大 DPD " + (int) currentDpd));
            }
            if (onTime >= 0 && onTime < 0.5) {
                signals.add(new Signal("BCARD_ON_TIME_LOW", 14, "近 12 月准时率 " + onTime));
            }
            if (daysSince != null && daysSince >= 0 && daysSince < 30) {
                signals.add(new Signal("BCARD_RECENT_OVERDUE", 10, "最近一次逾期在 " + daysSince.intValue() + " 天前"));
            }
            if (utilization >= 0.9) {
                signals.add(new Signal("BCARD_UTILIZATION_HIGH", 8, "额度使用率 " + utilization));
            }
        }

        // 5) 申请频率（短时间多次申请）
        if (applications30d >= 3) {
            signals.add(new Signal("APPLY_VELOCITY", 8, "近 30 天申请 " + applications30d + " 次"));
        }

        int score = Math.min(100, signals.stream().mapToInt(Signal::points).sum());
        Level level = score >= CRITICAL_SCORE ? Level.CRITICAL
                : score >= HIGH_SCORE ? Level.HIGH
                : score >= MEDIUM_SCORE ? Level.MEDIUM
                : Level.LOW;

        String summary = signals.stream()
                .sorted(Comparator.comparingInt(Signal::points).reversed())
                .limit(3)
                .map(Signal::detail)
                .reduce((a, b) -> a + "；" + b)
                .orElse("未命中明显欺诈信号");
        return new Result(score, level, signals, summary);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static double doubleOf(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static double value(Map<String, Double> map, String key) {
        Double v = map.get(key);
        return v == null ? 0.0 : v;
    }

    /** 把收入档位映射为代表性月收入（用于偏差计算）。 */
    static double representativeIncome(String label) {
        return switch (label) {
            case "3000以下" -> 2500;
            case "3000-5000" -> 4000;
            case "5000-8000" -> 6500;
            case "8000-15000" -> 11500;
            case "15000以上" -> 20000;
            default -> 0;
        };
    }
}