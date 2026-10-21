package org.example.risklendpro.cs.service;

import org.example.risklendpro.api.dto.BCardRepaymentSnapshot;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.cs.client.LoanServiceClient;
import org.example.risklendpro.cs.client.RiskServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/**
 * 实时数据应答：通过 Feign 调用 risk-service / loan-service 的内部契约，
 * 把用户额度、授信评估状态、还款计划、补充材料拼成自然语言返回给 AI 客服。
 * 每个数据源独立降级，单个接口失败不影响其他段落。
 */
@Service
public class RealtimeDataService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeDataService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] LIMIT_KEYS = {"额度", "还能借", "可借"};
    private static final String[] ASSESSMENT_KEYS = {"评估", "申请进度", "到哪一步", "审批", "申请状态"};
    private static final String[] REPAYMENT_KEYS = {"借款", "还款", "下一期", "逾期", "结清", "账单"};
    private static final String[] PROOF_KEYS = {"补充材料", "材料", "身份证明", "上传"};

    private final RiskServiceClient riskServiceClient;
    private final LoanServiceClient loanServiceClient;

    public RealtimeDataService(RiskServiceClient riskServiceClient, LoanServiceClient loanServiceClient) {
        this.riskServiceClient = riskServiceClient;
        this.loanServiceClient = loanServiceClient;
    }

    /**
     * 根据用户提问识别关心的数据域，实时查询并拼装回答。
     */
    public String describe(Long userId, String message) {
        boolean wantLimit = containsAny(message, LIMIT_KEYS);
        boolean wantAssessment = containsAny(message, ASSESSMENT_KEYS);
        boolean wantRepayment = containsAny(message, REPAYMENT_KEYS);
        boolean wantProof = containsAny(message, PROOF_KEYS);
        // 没有明显倾向时默认汇总额度 + 评估 + 还款
        if (!wantLimit && !wantAssessment && !wantRepayment && !wantProof) {
            wantLimit = true;
            wantAssessment = true;
            wantRepayment = true;
        }

        StringBuilder sb = new StringBuilder();
        if (wantLimit) {
            sb.append(describeLimit(userId));
        }
        if (wantAssessment) {
            sb.append(describeAssessment(userId));
        }
        if (wantRepayment) {
            sb.append(describeRepayment(userId));
        }
        if (wantProof) {
            sb.append(describeProofs(userId));
        }
        if (sb.isEmpty()) {
            sb.append("暂未查询到您的相关账户信息。");
        }
        sb.append("（以上为实时查询结果，最终以平台实际记录为准。）");
        return sb.toString();
    }

    private String describeLimit(Long userId) {
        CreditLimitSnapshot limit = safe(() -> loanServiceClient.getCreditLimit(userId), "额度查询");
        if (limit == null) {
            return "暂未查询到您的授信额度记录。\n";
        }
        StringBuilder sb = new StringBuilder("您的授信额度：总额度 ")
                .append(money(limit.totalLimit())).append(" 元，已使用 ")
                .append(money(limit.usedLimit())).append(" 元，剩余可用 ")
                .append(money(limit.remainingLimit())).append(" 元。");
        if (limit.hasOverdue()) {
            sb.append("您当前存在逾期金额 ").append(money(limit.overdueAmount()))
                    .append(" 元，请尽快处理以免影响后续使用。");
        }
        return sb.append("\n").toString();
    }

    private String describeAssessment(Long userId) {
        RiskAssessmentSummary assessment =
                safe(() -> riskServiceClient.getLatestFinalAssessment(userId), "评估状态查询");
        if (assessment == null) {
            return "暂未查询到您的授信评估记录，可能是尚未提交申请。\n";
        }
        StringBuilder sb = new StringBuilder("您最近一次授信评估状态：")
                .append(resolveAssessmentStatus(assessment.status()));
        if (assessment.approvedLimit() != null) {
            sb.append("，获批额度 ").append(money(assessment.approvedLimit())).append(" 元");
        }
        return sb.append("。\n").toString();
    }

    private String describeRepayment(Long userId) {
        BCardRepaymentSnapshot repay =
                safe(() -> loanServiceClient.getBCardRepaymentMonitor(userId), "还款计划查询");
        if (repay == null) {
            return "暂未查询到您的借款/还款记录。\n";
        }
        if (repay.activePlanCount() == 0) {
            return "您当前没有进行中的借款，暂无待还账单。\n";
        }
        StringBuilder sb = new StringBuilder("您当前有 ").append(repay.activePlanCount())
                .append(" 笔进行中的还款计划。");
        if ("OVERDUE".equals(repay.planStatus()) || "OVERDUE".equals(repay.recordStatus())) {
            sb.append("最近一笔存在逾期");
            if (repay.overdueDays() != null && repay.overdueDays() > 0) {
                sb.append("，已逾期 ").append(repay.overdueDays()).append(" 天");
            }
            sb.append("，请尽快还款。");
        } else if (repay.daysToDue() != null) {
            int d = repay.daysToDue();
            if (d < 0) {
                sb.append("最近一期已逾期 ").append(-d).append(" 天，请尽快还款。");
            } else if (d == 0) {
                sb.append("最近一期今日到期，请及时还款。");
            } else {
                sb.append("最近一期还款日为 ").append(fmtDate(repay.dueDate()))
                        .append("，距今还有 ").append(d).append(" 天。");
            }
        }
        return sb.append("\n").toString();
    }

    private String describeProofs(Long userId) {
        List<String> proofs = safe(() -> riskServiceClient.listIdentityProofs(userId), "补充材料查询");
        if (proofs == null || proofs.isEmpty()) {
            return "您当前没有已上传的身份证明材料记录。\n";
        }
        return "您已上传的身份证明材料：" + String.join("、", proofs) + "。\n";
    }

    /** 状态码 -> 用户友好说法，与 cs-system-prompt 的「用户端状态说法」保持一致。 */
    private String resolveAssessmentStatus(String status) {
        if (status == null) {
            return "暂无";
        }
        return switch (status) {
            case "WAITING" -> "处理中";
            case "SYSTEM_REJECT" -> "综合评估未通过";
            case "MANUAL_REVIEW" -> "人工复核中";
            case "FINAL_PASS" -> "已通过";
            case "FINAL_REJECT" -> "未通过";
            default -> status;
        };
    }

    private <T> T safe(Supplier<T> supplier, String label) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("实时数据 {} 失败，降级处理: {}", label, e.getMessage());
            return null;
        }
    }

    private static String money(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String fmtDate(Long millis) {
        if (millis == null) {
            return "—";
        }
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT);
    }

    private boolean containsAny(String message, String[] keys) {
        for (String key : keys) {
            if (message.contains(key)) {
                return true;
            }
        }
        return false;
    }
}
