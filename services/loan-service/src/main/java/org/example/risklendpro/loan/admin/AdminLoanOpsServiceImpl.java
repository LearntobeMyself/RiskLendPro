package org.example.risklendpro.loan.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.RiskOverviewCounts;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.common.mail.EmailUtil;
import org.example.risklendpro.loan.borrow.LoanApproveRequest;
import org.example.risklendpro.loan.borrow.LoanApproveResponse;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.catalog.CatalogJson;
import org.example.risklendpro.loan.catalog.entity.LoanProduct;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.entity.VintageData;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.LoanProductMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.mapper.VintageDataMapper;
import org.example.risklendpro.loan.repay.RepaymentCalculator;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminLoanOpsServiceImpl implements AdminLoanOpsService {

    private static final Logger log = LoggerFactory.getLogger(AdminLoanOpsServiceImpl.class);

    @Autowired
    private LoanMapper loanMapper;
    @Autowired
    private LoanProductMapper loanProductMapper;
    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;
    @Autowired
    private VintageDataMapper vintageDataMapper;
    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;
    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private RiskServiceClient riskServiceClient;
    @Autowired
    private UserServiceClient userServiceClient;
    @Autowired
    private PlatformTransactionManager transactionManager;

    public Map<String, Object> getVintageData() {
        List<VintageData> vintageDataList = vintageDataMapper.selectList(null);
        if (vintageDataList != null && !vintageDataList.isEmpty()) {
            return buildVintageResponse(vintageDataList);
        }
        return computeVintageFromLoans();
    }

    private Map<String, Object> buildVintageResponse(List<VintageData> vintageDataList) {
        Map<String, Object> result = new HashMap<>();
        List<String> months = vintageDataList.stream()
                .map(VintageData::getMonth)
                .collect(Collectors.toList());
        result.put("months", months);

        List<Map<String, Object>> vintageData = new ArrayList<>();
        for (VintageData data : vintageDataList) {
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("month", data.getMonth());
            dataMap.put("disbursedAmount", data.getDisbursedAmount());
            dataMap.put("M1Rate", data.getM1Rate());
            dataMap.put("M2Rate", data.getM2Rate());
            dataMap.put("M3Rate", data.getM3Rate());
            vintageData.add(dataMap);
        }
        result.put("vintageData", vintageData);
        return result;
    }

    private Map<String, Object> computeVintageFromLoans() {
        List<Loan> loans = loanMapper.selectList(new QueryWrapper<Loan>()
                .in("status", LoanStatusEnum.DISBURSED.getCode(), LoanStatusEnum.REPAID.getCode(),
                        LoanStatusEnum.OVERDUE.getCode())
                .isNotNull("disbursement_time")
                .orderByAsc("disbursement_time"));

        Map<String, List<Loan>> byMonth = loans.stream()
                .collect(Collectors.groupingBy(l -> formatVintageMonth(l.getDisbursementTime())));

        List<String> months = new ArrayList<>(byMonth.keySet());
        Collections.sort(months);

        List<Map<String, Object>> vintageData = new ArrayList<>();
        for (String month : months) {
            List<Loan> cohortLoans = byMonth.get(month);
            BigDecimal disbursedAmount = cohortLoans.stream()
                    .map(l -> l.getAmount() != null ? l.getAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<Long> loanIds = cohortLoans.stream().map(Loan::getLoanId).toList();
            List<RepaymentPlan> plans = loanIds.isEmpty() ? List.of()
                    : repaymentPlanMapper.selectList(new QueryWrapper<RepaymentPlan>().in("loan_id", loanIds));

            int totalPlans = plans.size();
            long m1Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M1")).count();
            long m2Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M2")).count();
            long m3Count = plans.stream().filter(p -> containsOverdueLevel(p.getOverdueLevel(), "M3")).count();

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("month", month);
            dataMap.put("disbursedAmount", disbursedAmount);
            dataMap.put("M1Rate", totalPlans > 0 ? (double) m1Count / totalPlans : 0.0);
            dataMap.put("M2Rate", totalPlans > 0 ? (double) m2Count / totalPlans : 0.0);
            dataMap.put("M3Rate", totalPlans > 0 ? (double) m3Count / totalPlans : 0.0);
            vintageData.add(dataMap);
        }

        if (vintageData.isEmpty()) {
            log.info("Vintage compute: no disbursed loan cohorts found");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("vintageData", vintageData);
        return result;
    }

    private String formatVintageMonth(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private boolean containsOverdueLevel(String overdueLevel, String level) {
        return overdueLevel != null && overdueLevel.toUpperCase().contains(level);
    }

    @Override
    public Map<String, Object> getRollRateData() {
        Map<String, Object> result = new HashMap<>();
        result.put("currentStatus", "C");

        Map<String, Object> nextMonthStatus = calculateRollRate();
        result.put("nextMonthStatus", nextMonthStatus);

        return result;
    }

    @Override
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        RiskOverviewCounts riskOverview = riskServiceClient.getOverviewCounts();
        long totalApplications = riskOverview.totalApplications();
        long pendingReview = riskOverview.pendingReview();

        long totalDisbursedCount = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURSED.getCode())
        );

        long totalOverdueCount = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.OVERDUE.getCode())
        );

        long approvedToday = countLoansApprovedToday();
        long rejectedToday = countLoansRejectedToday();
        BigDecimal totalDisbursedAmount = calculateTotalDisbursedAmount();
        BigDecimal totalOverdueAmount = calculateTotalOverdueAmount();

        stats.put("totalApplications", totalApplications);
        stats.put("pendingReview", pendingReview);
        stats.put("approvedToday", approvedToday);
        stats.put("rejectedToday", rejectedToday);
        stats.put("totalDisbursed", totalDisbursedAmount);
        stats.put("totalOverdue", totalOverdueAmount);
        stats.put("overdueRate", totalDisbursedCount > 0 ? (double) totalOverdueCount / totalDisbursedCount : 0);

        return stats;
    }

    @Override
    public Page<Map<String, Object>> getLoanPendingList(Integer page, Integer size) {
        Page<Loan> pageInfo = new Page<>(page, size);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.PENDING_APPROVAL.getCode());
        queryWrapper.orderByDesc("apply_time");

        Page<Loan> resultPage = loanMapper.selectPage(pageInfo, queryWrapper);
        Page<Map<String, Object>> responsePage = new Page<>(page, size);
        responsePage.setTotal(resultPage.getTotal());

        List<Map<String, Object>> records = new ArrayList<>();
        for (Loan loan : resultPage.getRecords()) {
            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );
            BigDecimal totalLimit = creditLimit != null ? creditLimit.getTotalLimit() : BigDecimal.ZERO;
            BigDecimal remainingLimit = creditLimit != null ? creditLimit.getRemainingLimit() : BigDecimal.ZERO;
            BigDecimal exceedAmount = loan.getAmount().subtract(remainingLimit).max(BigDecimal.ZERO);
            // 抵押类申请与剩余信用额度无关，一律进入管理员抵押确认列表；
            // 信用类申请超过剩余额度在受理时已直接拒绝，不会进入人工审批。
            if (loan.getCollateralType() == null) {
                continue;
            }

            Map<String, Object> record = new HashMap<>();
            record.put("loanId", loan.getLoanId());
            record.put("userId", loan.getUserId());
            record.put("productId", loan.getProductId());
            record.put("productName", loan.getProductName());
            record.put("collateralType", loan.getCollateralType());
            record.put("collateralValue", loan.getCollateralValue());

            UserSummary user = userServiceClient.getUser(loan.getUserId());
            if (user != null) {
                record.put("userName", user.realName());
                record.put("phone", user.phoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                record.put("idCard", user.idCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
            } else {
                record.put("userName", "未知用户");
                record.put("phone", "未知");
                record.put("idCard", "未知");
            }

            record.put("currentLimit", totalLimit);
            record.put("remainingLimit", remainingLimit);
            record.put("exceedAmount", exceedAmount);
            record.put("amount", loan.getAmount());
            record.put("termMonths", loan.getTermMonths());
            record.put("repaymentMethod", loan.getRepaymentMethod());
            record.put("applyTime", loan.getApplyTime());
            record.put("status", loan.getStatus());
            records.add(record);
        }
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public LoanApproveResponse approveLoan(LoanApproveRequest request) {
        // 事务内只做加锁、校验、状态流转与还款计划生成；
        // 跨服务调用（Feign）与邮件发送放到事务提交之后，避免长时间占用数据库连接、也避免邮件失败回滚审批。
        ApproveOutcome outcome = new TransactionTemplate(transactionManager).execute(status -> doApprove(request));

        LoanApproveResponse response = new LoanApproveResponse();
        response.setLoanId(outcome.loanId());
        response.setUserId(outcome.userId());
        response.setStatus(outcome.status());
        response.setApproveTime(outcome.approveTime());
        response.setEmailSent(true);

        UserSummary user = userServiceClient.getUser(outcome.userId());

        if (LoanStatusEnum.REJECTED.getCode().equals(outcome.status())) {
            response.setRejectReason(outcome.rejectReason());
            if (user != null) {
                emailUtil.sendLoanRejectNotification(
                        user.email(),
                        user.realName(),
                        outcome.amount().toString(),
                        outcome.rejectReason()
                );
            }
            return response;
        }

        response.setActualDisbursedAmount(outcome.amount());
        if (user != null && user.idCard() != null) {
            riskServiceClient.activateBehaviorScore(outcome.userId(), user.idCard());
        }
        if (user != null) {
            emailUtil.sendLoanSuccessNotification(user.email(), user.realName(), outcome.amount().toString());
        }
        return response;
    }

    /** 审批的数据库部分：加锁、幂等校验、状态流转、还款计划生成。 */
    private ApproveOutcome doApprove(LoanApproveRequest request) {
        Loan loan = loanMapper.selectOne(
                new QueryWrapper<Loan>().eq("loan_id", request.getLoanId()).last("FOR UPDATE"));
        if (loan == null) {
            throw new RuntimeException("贷款申请不存在");
        }
        if (!LoanStatusEnum.PENDING_APPROVAL.getCode().equals(loan.getStatus())) {
            throw new RuntimeException("该贷款申请已处理，无法重复审批");
        }

        Date approveTime = new Date();

        if ("APPROVE".equals(request.getApproveResult())) {
            long existingPlans = repaymentPlanMapper.selectCount(
                    new QueryWrapper<RepaymentPlan>().eq("loan_id", loan.getLoanId()));
            if (existingPlans > 0) {
                throw new RuntimeException("该贷款已生成还款计划，无法重复放款");
            }

            // 抵押类：管理员确认抵押物、录入评估价后按 评估价×抵押率 放款，不占用信用额度。
            if (loan.getCollateralType() != null) {
                return approveMortgageLoan(loan, request, approveTime);
            }

            // 信用类申请超过剩余额度在受理时已直接拒绝，管理员不能改成通过。
            throw new RuntimeException("信用类申请超出剩余额度将被直接拒绝，不支持人工审批放款");
        }

        loan.setStatus(LoanStatusEnum.REJECTED.getCode());
        loan.setApproveTime(approveTime);
        loan.setRejectReason(request.getApproveRemark());
        loanMapper.updateById(loan);

        return new ApproveOutcome(loan.getLoanId(), loan.getUserId(), loan.getAmount(),
                LoanStatusEnum.REJECTED.getCode(), request.getApproveRemark(), approveTime);
    }

    /**
     * 房抵、车抵审批：管理员确认抵押物并录入评估价，金额须 ≤ min(评估价×抵押率, 产品上限)，
     * 通过后直接放款且不占用、不恢复信用额度。
     */
    private ApproveOutcome approveMortgageLoan(Loan loan, LoanApproveRequest request, Date approveTime) {
        if (request.getCollateralValue() == null || request.getCollateralValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("审批通过抵押类申请必须录入抵押物评估价");
        }

        LoanProduct product = loan.getProductId() == null ? null : loanProductMapper.selectById(loan.getProductId());
        BigDecimal rate = resolveCollateralRate(loan.getCollateralType(), product);
        BigDecimal cap = request.getCollateralValue()
                .multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        if (product != null && product.getMaxAmount() != null) {
            cap = cap.min(BigDecimal.valueOf(product.getMaxAmount()));
        }
        if (loan.getAmount().compareTo(cap) > 0) {
            throw new RuntimeException("借款金额超过抵押物可贷上限（评估价×抵押率" + rate + "% = " + cap + "）");
        }

        loan.setCollateralValue(request.getCollateralValue());
        loan.setCollateralRate(rate);
        if (request.getCollateralType() != null && !request.getCollateralType().isBlank()) {
            loan.setCollateralType(request.getCollateralType());
        }
        loan.setStatus(LoanStatusEnum.DISBURSED.getCode());
        loan.setApproveTime(approveTime);
        loan.setDisbursementTime(approveTime);
        loan.setAutoApproved(false);
        loanMapper.updateById(loan);

        generateRepaymentPlan(loan, loan.getRepaymentMethod());

        return new ApproveOutcome(loan.getLoanId(), loan.getUserId(), loan.getAmount(),
                LoanStatusEnum.DISBURSED.getCode(), null, approveTime);
    }

    /** 审批结果，供事务提交后的事务外处理（跨服务调用、邮件通知）使用。 */
    private record ApproveOutcome(Long loanId, Long userId, BigDecimal amount,
                                  String status, String rejectReason, Date approveTime) {
    }

    /** 抵押率：优先取产品的 maxLoanToValue，缺省住宅 70%、车辆 80%。 */
    private BigDecimal resolveCollateralRate(String collateralType, LoanProduct product) {
        if (product != null && product.getExtraConditions() != null) {
            Object ltv = CatalogJson.readMap(product.getExtraConditions()).get("maxLoanToValue");
            if (ltv instanceof Number n) {
                return BigDecimal.valueOf(n.doubleValue());
            }
            if (ltv != null) {
                try {
                    return new BigDecimal(ltv.toString());
                } catch (NumberFormatException ignored) {
                    // 落到默认值
                }
            }
        }
        return "VEHICLE".equals(collateralType) ? new BigDecimal("80") : new BigDecimal("70");
    }

    private Map<String, Object> calculateRollRate() {
        Map<String, Object> rollRate = new HashMap<>();

        long totalActive = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "ACTIVE")
        );

        long movedToM1 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M1")
        );

        long movedToM2 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M2")
        );

        long movedToM3 = repaymentPlanMapper.selectCount(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").like("overdue_level", "M3")
        );

        if (totalActive > 0) {
            rollRate.put("C", (double) (totalActive - movedToM1 - movedToM2 - movedToM3) / totalActive);
            rollRate.put("M1", (double) movedToM1 / totalActive);
            rollRate.put("M2", (double) movedToM2 / totalActive);
            rollRate.put("M3", (double) movedToM3 / totalActive);
        } else {
            rollRate.put("C", 1.0);
            rollRate.put("M1", 0.0);
            rollRate.put("M2", 0.0);
            rollRate.put("M3", 0.0);
        }

        return rollRate;
    }

    private long countLoansApprovedToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.APPROVED.getCode());
        queryWrapper.between("approve_time", startOfDay, endOfDay);

        return loanMapper.selectCount(queryWrapper);
    }

    private long countLoansRejectedToday() {
        LocalDate today = LocalDate.now();
        Date startOfDay = Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endOfDay = Date.from(today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", LoanStatusEnum.REJECTED.getCode());
        queryWrapper.between("approve_time", startOfDay, endOfDay);

        return loanMapper.selectCount(queryWrapper);
    }

    private BigDecimal calculateTotalDisbursedAmount() {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURSED.getCode())
        );

        return loans.stream()
                .map(Loan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalOverdueAmount() {
        List<Loan> loans = loanMapper.selectList(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.OVERDUE.getCode())
        );

        return loans.stream()
                .map(Loan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void generateRepaymentPlan(Loan loan, String repaymentMethod) {
        List<RepaymentCalculator.RepaymentDetail> details;
        switch (repaymentMethod) {
            case "等额本息":
                details = RepaymentCalculator.calculateEqualPrincipalAndInterest(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "等额本金":
                details = RepaymentCalculator.calculateEqualPrincipal(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            case "先息后本":
                details = RepaymentCalculator.calculateInterestFirst(
                        loan.getAmount(), loan.getInterestRate(), loan.getTermMonths());
                break;
            default:
                throw new RuntimeException("不支持的还款方式: " + repaymentMethod);
        }

        BigDecimal totalRepayable = BigDecimal.ZERO;
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            totalRepayable = totalRepayable.add(detail.getAmount());
        }

        RepaymentPlan plan = new RepaymentPlan();
        plan.setLoanId(loan.getLoanId());
        plan.setUserId(loan.getUserId());
        plan.setTotalAmount(totalRepayable);
        plan.setPaidAmount(BigDecimal.ZERO);
        plan.setRemainingAmount(totalRepayable);
        plan.setTotalPeriods(loan.getTermMonths());
        plan.setCurrentPeriod(1);
        plan.setStatus("ACTIVE");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());

        repaymentPlanMapper.insert(plan);

        Date now = new Date();
        for (RepaymentCalculator.RepaymentDetail detail : details) {
            RepaymentRecord record = new RepaymentRecord();
            record.setPlanId(plan.getPlanId());
            record.setLoanId(loan.getLoanId());
            record.setPeriod(detail.getPeriod());
            record.setPrincipal(detail.getPrincipal());
            record.setInterest(detail.getInterest());
            record.setAmount(detail.getAmount());
            record.setActualAmount(BigDecimal.ZERO);

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(now);
            calendar.add(Calendar.MONTH, detail.getPeriod());
            record.setDueDate(calendar.getTime());

            record.setStatus("PENDING");
            record.setCreateTime(now);

            repaymentRecordMapper.insert(record);
        }
    }
}
