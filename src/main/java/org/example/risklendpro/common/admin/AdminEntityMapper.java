package org.example.risklendpro.common.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.risk.entity.RiskAssessment;
import org.example.risklendpro.user.entity.User;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.risk.mapper.RiskAssessmentMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AdminEntityMapper {

    private AdminEntityMapper() {
    }

    public static RiskAssessment findLatestFinalAssessment(RiskAssessmentMapper mapper, Long userId) {
        return mapper.selectOne(
                new QueryWrapper<RiskAssessment>()
                        .eq("user_id", userId)
                        .eq("is_final", true)
                        .orderByDesc("submit_time")
                        .last("LIMIT 1"));
    }

    public static Integer latestCreditScore(RiskAssessment assessment) {
        return assessment != null && assessment.getTotalScore() != null ? assessment.getTotalScore() : 0;
    }

    public static String resolveUserStatus(User user) {
        if (user == null) {
            return "ACTIVE";
        }
        if (user.getAccountStatus() != null && !user.getAccountStatus().isBlank()) {
            return user.getAccountStatus();
        }
        if (user.getAssessmentStatus() != null && !user.getAssessmentStatus().isBlank()) {
            return user.getAssessmentStatus();
        }
        return "ACTIVE";
    }

    public static String deriveCreditLimitStatus(User user, UserCreditLimit limit) {
        String accountStatus = resolveUserStatus(user);
        if ("DISABLED".equals(accountStatus) || "FROZEN".equals(accountStatus)) {
            return "FROZEN";
        }
        if (limit != null && limit.getUsedLimit() != null && limit.getTotalLimit() != null
                && limit.getUsedLimit().compareTo(limit.getTotalLimit()) > 0) {
            return "OVERLIMIT";
        }
        return "NORMAL";
    }

    public static boolean matchesCreditLimitStatus(String filterStatus, User user, UserCreditLimit limit) {
        if (filterStatus == null || filterStatus.isBlank()) {
            return true;
        }
        return filterStatus.equals(deriveCreditLimitStatus(user, limit));
    }

    public static BigDecimal toUsageRatePercent(UserCreditLimit limit) {
        if (limit == null || limit.getTotalLimit() == null
                || limit.getTotalLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal used = limit.getUsedLimit() != null ? limit.getUsedLimit() : BigDecimal.ZERO;
        return used.multiply(BigDecimal.valueOf(100))
                .divide(limit.getTotalLimit(), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal toInterestRatePercent(BigDecimal interestRate) {
        if (interestRate == null) {
            return BigDecimal.ZERO;
        }
        return interestRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    public static Map<String, Object> toUserListItem(User user, UserCreditLimit limit,
                                                     RiskAssessment assessment, List<Loan> loans) {
        BigDecimal totalLoan = loans.stream()
                .map(l -> l.getAmount() != null ? l.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("userId", user.getId());
        item.put("name", user.getRealName());
        item.put("realName", user.getRealName());
        item.put("phone", maskPhone(user.getPhoneNumber()));
        item.put("phoneNumber", maskPhone(user.getPhoneNumber()));
        item.put("idCard", maskIdCard(user.getIdCard()));
        item.put("creditScore", latestCreditScore(assessment));
        item.put("creditLimit", limit != null ? limit.getTotalLimit() : BigDecimal.ZERO);
        item.put("loanCount", loans.size());
        item.put("totalLoan", totalLoan);
        String accountStatus = resolveUserStatus(user);
        item.put("status", accountStatus);
        item.put("accountStatus", accountStatus);
        item.put("registerTime", AdminDateHelper.formatDateTime(user.getCreateTime()));
        item.put("createTime", AdminDateHelper.formatDateTime(user.getCreateTime()));
        item.put("avatar", "");
        return item;
    }

    public static Map<String, Object> toCreditLimitItem(User user, UserCreditLimit limit, RiskAssessment assessment) {
        Map<String, Object> item = new HashMap<>();
        item.put("userId", limit.getUserId());
        item.put("userName", user != null ? user.getRealName() : "未知");
        item.put("phone", user != null ? user.getPhoneNumber() : "");
        item.put("idCard", user != null ? maskIdCard(user.getIdCard()) : "");
        item.put("creditScore", latestCreditScore(assessment));
        item.put("totalLimit", limit.getTotalLimit());
        item.put("usedLimit", limit.getUsedLimit());
        item.put("remainingLimit", limit.getRemainingLimit());
        item.put("availableLimit", limit.getRemainingLimit());
        item.put("usageRate", toUsageRatePercent(limit));
        item.put("status", deriveCreditLimitStatus(user, limit));
        item.put("bScore", limit.getBScore());
        item.put("bCardEnabled", limit.getBCardEnabled());
        item.put("hasOverdue", limit.getHasOverdue());
        item.put("overdueAmount", limit.getOverdueAmount());
        item.put("lastUpdateTime", AdminDateHelper.formatDateTime(limit.getLastUpdateTime()));
        item.put("lastAdjustTime", AdminDateHelper.formatDateTime(limit.getLastUpdateTime()));
        item.put("avatar", "");
        return item;
    }

    public static BigDecimal sumPrincipal(List<RepaymentRecord> records) {
        return sumBigDecimal(records, RepaymentRecord::getPrincipal);
    }

    public static BigDecimal sumInterest(List<RepaymentRecord> records) {
        return sumBigDecimal(records, RepaymentRecord::getInterest);
    }

    private static BigDecimal sumBigDecimal(List<RepaymentRecord> records,
                                            java.util.function.Function<RepaymentRecord, BigDecimal> getter) {
        return records.stream()
                .map(r -> {
                    BigDecimal v = getter.apply(r);
                    return v != null ? v : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 15) {
            return idCard;
        }
        return idCard.replaceAll("(\\d{3})\\d+(\\d{4})", "$1***********$2");
    }

    public static String resolveOperatorLabel(Long operatorId, String adminUsername) {
        if (operatorId == null || operatorId == 0L) {
            return "System";
        }
        if (adminUsername != null && !adminUsername.isBlank()) {
            return adminUsername;
        }
        return "Admin#" + operatorId;
    }
}
