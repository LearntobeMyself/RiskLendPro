package org.example.risklendpro.loan.client;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.api.contract.LoanApi;
import org.example.risklendpro.api.dto.BCardRepaymentSnapshot;
import org.example.risklendpro.api.dto.CreditBehaviorUpsertCommand;
import org.example.risklendpro.api.dto.CreditLimitGrantCommand;
import org.example.risklendpro.api.dto.CreditLimitSnapshot;
import org.example.risklendpro.api.dto.LoanBehaviorSnapshot;
import org.example.risklendpro.api.dto.LoanUserSummaryItem;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.entity.LimitAdjustLog;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.mapper.LimitAdjustLogMapper;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * loan-service 对外契约实现（授信/额度域 + 贷款查询域），供 user/risk 通过 Feign 消费。
 */
@RestController
public class InternalLoanController implements LoanApi {

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private LimitAdjustLogMapper limitAdjustLogMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Override
    public CreditLimitSnapshot getCreditLimit(Long userId) {
        UserCreditLimit limit = findLimit(userId);
        if (limit == null) {
            return null;
        }
        return toSnapshot(limit);
    }

    @Override
    public Map<Long, CreditLimitSnapshot> listCreditLimits(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userCreditLimitMapper.selectList(
                        new QueryWrapper<UserCreditLimit>().in("user_id", userIds))
                .stream()
                .collect(Collectors.toMap(UserCreditLimit::getUserId, this::toSnapshot, (a, b) -> a));
    }

    @Override
    public CreditLimitSnapshot ensureCreditLimit(Long userId) {
        UserCreditLimit limit = findLimit(userId);
        if (limit == null) {
            limit = new UserCreditLimit();
            limit.setUserId(userId);
            limit.setTotalLimit(BigDecimal.ZERO);
            limit.setUsedLimit(BigDecimal.ZERO);
            limit.setRemainingLimit(BigDecimal.ZERO);
            limit.setOverdueAmount(BigDecimal.ZERO);
            limit.setHasOverdue(false);
            limit.setBCardEnabled(false);
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.insert(limit);
        }
        return toSnapshot(limit);
    }

    @Override
    public List<CreditLimitSnapshot> listBCardLimits() {
        return userCreditLimitMapper.selectList(
                new QueryWrapper<UserCreditLimit>().eq("b_card_enabled", true)
        ).stream().map(this::toSnapshot).toList();
    }

    @Override
    public CreditLimitSnapshot grantCreditLimit(CreditLimitGrantCommand command) {
        UserCreditLimit limit = findLimit(command.userId());
        BigDecimal approvedLimit = command.approvedLimit() != null ? command.approvedLimit() : BigDecimal.ZERO;
        if (limit == null) {
            limit = new UserCreditLimit();
            limit.setUserId(command.userId());
            limit.setTotalLimit(approvedLimit);
            limit.setUsedLimit(BigDecimal.ZERO);
            limit.setRemainingLimit(approvedLimit);
            limit.setOverdueAmount(BigDecimal.ZERO);
            limit.setHasOverdue(false);
            limit.setBCardEnabled(false);
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.insert(limit);
        } else {
            // 新增授信：在原有额度基础上累加，保留已有的已用额度与还款记录
            BigDecimal oldTotal = limit.getTotalLimit() != null ? limit.getTotalLimit() : BigDecimal.ZERO;
            BigDecimal used = limit.getUsedLimit() != null ? limit.getUsedLimit() : BigDecimal.ZERO;
            BigDecimal newTotal = oldTotal.add(approvedLimit);
            BigDecimal newRemaining = newTotal.subtract(used).max(BigDecimal.ZERO);
            limit.setTotalLimit(newTotal);
            limit.setRemainingLimit(newRemaining);
            limit.setLastUpdateTime(new Date());
            userCreditLimitMapper.updateById(limit);

            // 记录一次新增授信日志，便于审计追踪
            LimitAdjustLog log = new LimitAdjustLog();
            log.setUserId(command.userId());
            log.setOldLimit(oldTotal);
            log.setNewLimit(newTotal);
            log.setReason("新增授信审批通过，获批额度：" + approvedLimit + "，原总额度：" + oldTotal);
            log.setOperatorId(0L);
            log.setAdjustTime(new Date());
            limitAdjustLogMapper.insert(log);
        }
        return toSnapshot(limit);
    }

    @Override
    public CreditLimitSnapshot upsertBehaviorScore(CreditBehaviorUpsertCommand command) {
        UserCreditLimit limit = findLimit(command.userId());
        if (limit == null) {
            return null;
        }
        if (command.bCardEnabled() != null) {
            limit.setBCardEnabled(command.bCardEnabled());
        }
        if (command.behaviorScore() != null) {
            limit.setBScore(command.behaviorScore());
            limit.setBScoreUpdatedAt(new Date());
        }
        limit.setLastUpdateTime(new Date());
        userCreditLimitMapper.updateById(limit);
        return toSnapshot(limit);
    }

    @Override
    public LoanBehaviorSnapshot getLoanBehavior(Long userId) {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("user_id", userId));
        List<Long> loanIds = loans.stream().map(Loan::getLoanId).collect(Collectors.toList());

        long activeLoanCount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .count();

        BigDecimal outstandingAmount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RepaymentPlan> plans = loanIds.isEmpty() ? Collections.emptyList()
                : repaymentPlanMapper.selectList(new QueryWrapper<RepaymentPlan>().in("loan_id", loanIds));

        Date now = new Date();
        int maxOverdueDays = 0;
        int overduePeriodCount = 0;
        long overdueRecordCount = 0;
        for (RepaymentPlan plan : plans) {
            if ("COMPLETED".equals(plan.getStatus())) {
                continue;
            }
            List<RepaymentRecord> planRecords = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>()
                            .eq("plan_id", plan.getPlanId())
                            .orderByAsc("period"));
            boolean planOverdue = false;
            for (RepaymentRecord record : planRecords) {
                if (isRepaid(record) || record.getDueDate() == null) {
                    continue;
                }
                if (record.getDueDate().before(now)) {
                    overdueRecordCount++;
                    maxOverdueDays = Math.max(maxOverdueDays, daysPastDue(record.getDueDate(), now));
                    planOverdue = true;
                }
            }
            if (!planOverdue && "OVERDUE".equals(plan.getStatus())) {
                planOverdue = true;
                if (plan.getOverdueDays() != null) {
                    maxOverdueDays = Math.max(maxOverdueDays, plan.getOverdueDays());
                }
            }
            if (planOverdue) {
                overduePeriodCount++;
            }
        }

        List<RepaymentRecord> records;
        if (loanIds.isEmpty()) {
            records = Collections.emptyList();
        } else {
            records = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds));
        }
        double onTimeRate = 1.0;
        boolean historyAvailable = false;
        if (!records.isEmpty()) {
            int paid = 0;
            int onTime = 0;
            for (RepaymentRecord r : records) {
                if (isRepaid(r)) {
                    paid++;
                    historyAvailable = true;
                    if (r.getRepaymentDate() != null && r.getDueDate() != null
                            && !r.getRepaymentDate().after(r.getDueDate())) {
                        onTime++;
                    }
                } else if (r.getDueDate() != null && r.getDueDate().before(now)) {
                    paid++;
                    historyAvailable = true;
                }
            }
            onTimeRate = paid == 0 ? 1.0 : (double) onTime / (double) paid;
        }

        return new LoanBehaviorSnapshot(
                userId,
                (int) activeLoanCount,
                (int) overdueRecordCount,
                outstandingAmount,
                maxOverdueDays,
                overduePeriodCount,
                onTimeRate,
                historyAvailable
        );
    }

    @Override
    public LoanUserSummaryItem getUserLoanSummary(Long userId) {
        List<Loan> loans = loanMapper.selectList(new QueryWrapper<Loan>()
                .eq("user_id", userId)
                .in("status", LoanStatusEnum.DISBURSED.getCode(), LoanStatusEnum.REPAID.getCode(),
                        LoanStatusEnum.OVERDUE.getCode()));
        BigDecimal totalAmount = loans.stream()
                .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long activeCount = loans.stream()
                .filter(l -> LoanStatusEnum.DISBURSED.getCode().equals(l.getStatus())
                        || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                .count();
        return new LoanUserSummaryItem(userId, loans.size(), totalAmount, (int) activeCount);
    }

    @Override
    public Map<Long, LoanUserSummaryItem> listUserLoanSummaries(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Loan>> byUser = loanMapper.selectList(
                        new QueryWrapper<Loan>()
                                .in("user_id", userIds)
                                .in("status", LoanStatusEnum.DISBURSED.getCode(),
                                        LoanStatusEnum.REPAID.getCode(), LoanStatusEnum.OVERDUE.getCode()))
                .stream()
                .collect(Collectors.groupingBy(Loan::getUserId));
        return byUser.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    List<Loan> loans = e.getValue();
                    BigDecimal totalAmount = loans.stream()
                            .map(l -> l.getAmount() == null ? BigDecimal.ZERO : l.getAmount())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    long activeCount = loans.stream()
                            .filter(l -> LoanStatusEnum.DISBURSED.getCode().equals(l.getStatus())
                                    || LoanStatusEnum.OVERDUE.getCode().equals(l.getStatus()))
                            .count();
                    return new LoanUserSummaryItem(e.getKey(), loans.size(), totalAmount, (int) activeCount);
                },
                (a, b) -> a));
    }

    @Override
    public BCardRepaymentSnapshot getBCardRepaymentMonitor(Long userId) {
        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>().eq("user_id", userId));
        return buildBCardRepaymentSnapshot(userId, plans);
    }

    /**
     * 批量版本：一次性取回所有用户的还款计划再按用户分组，
     * 把贷后监控列表的 N 次跨服务调用压缩成 1 次。
     */
    @Override
    public Map<Long, BCardRepaymentSnapshot> listBCardRepaymentMonitors(List<Long> userIds) {
        Map<Long, BCardRepaymentSnapshot> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        Map<Long, List<RepaymentPlan>> plansByUser = new HashMap<>();
        try {
            List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                    new QueryWrapper<RepaymentPlan>().in("user_id", ids));
            if (plans != null) {
                for (RepaymentPlan p : plans) {
                    plansByUser.computeIfAbsent(p.getUserId(), k -> new ArrayList<>()).add(p);
                }
            }
        } catch (Exception ignored) {
            /* 批量查询异常时走下方逐用户兜底 */
        }
        for (Long userId : ids) {
            List<RepaymentPlan> plans = plansByUser.get(userId);
            if (plans == null) {
                plans = repaymentPlanMapper.selectList(
                        new QueryWrapper<RepaymentPlan>().eq("user_id", userId));
            }
            result.put(userId, buildBCardRepaymentSnapshot(userId, plans));
        }
        return result;
    }

    private BCardRepaymentSnapshot buildBCardRepaymentSnapshot(Long userId, List<RepaymentPlan> plans) {
        List<RepaymentPlan> safePlans = plans == null ? Collections.emptyList() : plans;
        // 一次取回本批计划的全部期次（已按 period 升序），避免每个计划各查 1~2 次造成的 N+1。
        List<Long> planIds = safePlans.stream()
                .filter(p -> !"COMPLETED".equals(p.getStatus()))
                .map(RepaymentPlan::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<RepaymentRecord>> recordsByPlan = new HashMap<>();
        if (!planIds.isEmpty()) {
            List<RepaymentRecord> records = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>().in("plan_id", planIds).orderByAsc("period"));
            if (records != null) {
                for (RepaymentRecord r : records) {
                    recordsByPlan.computeIfAbsent(r.getPlanId(), k -> new ArrayList<>()).add(r);
                }
            }
        }
        LocalDate today = LocalDate.now();
        int activePlanCount = 0;
        RepaymentPlan bestPlan = null;
        RepaymentRecord bestRecord = null;
        Integer daysToDue = null;
        int bestPriority = Integer.MAX_VALUE;
        for (RepaymentPlan plan : safePlans) {
            if ("COMPLETED".equals(plan.getStatus())) {
                continue;
            }
            activePlanCount++;
            RepaymentRecord record = resolveFocusRecord(plan.getPlanId(), plan.getCurrentPeriod(), recordsByPlan);
            if (record == null || record.getDueDate() == null) {
                continue;
            }
            int d = (int) ChronoUnit.DAYS.between(today, toLocalDate(record.getDueDate()));
            int priority = planUrgencyPriority(plan, record, d);
            if (bestPlan == null
                    || priority < bestPriority
                    || (priority == bestPriority && (daysToDue == null || d < daysToDue))) {
                bestPriority = priority;
                daysToDue = d;
                bestPlan = plan;
                bestRecord = record;
            }
        }
        return new BCardRepaymentSnapshot(
                userId,
                activePlanCount,
                bestPlan != null ? bestPlan.getPlanId() : null,
                bestPlan != null ? bestPlan.getStatus() : null,
                bestPlan != null ? bestPlan.getOverdueLevel() : null,
                bestPlan != null ? bestPlan.getOverdueDays() : null,
                bestRecord != null && bestRecord.getDueDate() != null ? bestRecord.getDueDate().getTime() : null,
                bestRecord != null ? bestRecord.getPeriod() : null,
                bestRecord != null ? bestRecord.getStatus() : null,
                daysToDue
        );
    }

    /** 当期已还清时推进到首个未还期次，避免误报逾期。records 由调用方按 planId 预取并升序。 */
    private RepaymentRecord resolveFocusRecord(Long planId, Integer currentPeriod,
                                               Map<Long, List<RepaymentRecord>> recordsByPlan) {
        if (planId == null || currentPeriod == null) {
            return null;
        }
        List<RepaymentRecord> records = recordsByPlan.getOrDefault(planId, Collections.emptyList());
        RepaymentRecord current = null;
        for (RepaymentRecord r : records) {
            if (currentPeriod.equals(r.getPeriod())) {
                current = r;
                break;
            }
        }
        if (current != null && !isRepaidRecord(current)) {
            return current;
        }
        for (RepaymentRecord record : records) {
            if (!isRepaidRecord(record)) {
                return record;
            }
        }
        return current;
    }

    private static boolean isRepaidRecord(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    /** 还款计划紧迫度：数值越小越优先展示。 */
    private static int planUrgencyPriority(RepaymentPlan plan, RepaymentRecord record, int daysToDue) {
        if (plan != null && "OVERDUE".equals(plan.getStatus())) {
            return 0;
        }
        if (record != null && "OVERDUE".equals(record.getStatus())) {
            return 0;
        }
        if (daysToDue < 0) {
            return 0;
        }
        if (daysToDue == 0) {
            return 1;
        }
        if (daysToDue <= 3) {
            return 2;
        }
        return 3;
    }

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private UserCreditLimit findLimit(Long userId) {
        return userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", userId));
    }

    private CreditLimitSnapshot toSnapshot(UserCreditLimit limit) {
        return new CreditLimitSnapshot(
                limit.getUserId(),
                limit.getTotalLimit(),
                limit.getUsedLimit(),
                limit.getRemainingLimit(),
                limit.getOverdueAmount(),
                Boolean.TRUE.equals(limit.getHasOverdue()),
                limit.getBScore(),
                limit.getBScoreUpdatedAt() == null ? null : limit.getBScoreUpdatedAt().getTime(),
                Boolean.TRUE.equals(limit.getBCardEnabled()),
                limit.getLastUpdateTime() == null ? null : limit.getLastUpdateTime().getTime()
        );
    }

    private static boolean isRepaid(RepaymentRecord record) {
        String status = record.getStatus();
        return "COMPLETED".equals(status) || "PAID".equals(status) || "SETTLED".equals(status);
    }

    private static int daysPastDue(Date dueDate, Date now) {
        LocalDate due = dueDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = now.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return (int) ChronoUnit.DAYS.between(due, today);
    }
}
