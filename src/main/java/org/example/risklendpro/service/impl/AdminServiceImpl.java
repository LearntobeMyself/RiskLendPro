package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.*;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.mapper.*;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.MockDataUpdateRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import org.example.risklendpro.service.AdminService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RedisCacheUtil;
import org.example.risklendpro.utils.RepaymentCalculator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private RiskAssessmentMapper riskAssessmentMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Autowired
    private MockDataMapper mockDataMapper;

    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;

    @Autowired
    private VintageDataMapper vintageDataMapper;

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailUtil emailUtil;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Override
    public Page<Map<String, Object>> getRiskList(Integer page, Integer size, String status) {
        Page<RiskAssessment> pageInfo = new Page<>(page, size);
        QueryWrapper<RiskAssessment> queryWrapper = new QueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        } else {
            queryWrapper.eq("status", "MANUAL_REVIEW");
        }
        queryWrapper.orderByDesc("submit_time");

        Page<RiskAssessment> resultPage = riskAssessmentMapper.selectPage(pageInfo, queryWrapper);
        Page<Map<String, Object>> responsePage = new Page<>(page, size);
        responsePage.setTotal(resultPage.getTotal());

        List<Map<String, Object>> records = new ArrayList<>();
        for (RiskAssessment assessment : resultPage.getRecords()) {
            Map<String, Object> record = new HashMap<>();
            record.put("applyId", assessment.getApplyId());
            record.put("userName", assessment.getName());
            record.put("phone", assessment.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
            record.put("idCard", assessment.getIdCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
            record.put("totalScore", assessment.getTotalScore());
            record.put("sysDecision", assessment.getSysDecision());
            record.put("status", assessment.getStatus());
            record.put("applyTime", assessment.getSubmitTime());
            records.add(record);
        }
        responsePage.setRecords(records);
        return responsePage;
    }

    @Override
    public Map<String, Object> getRiskReport(String applyId) {
        String cacheKey = RedisCacheUtil.getRiskReportKey(applyId);
        if (redisCacheUtil.exists(cacheKey)) {
            Map<String, Object> cachedReport = redisCacheUtil.get(cacheKey, Map.class);
            if (cachedReport != null) {
                return cachedReport;
            }
        }
        return new HashMap<>();
    }

    @Override
    @Transactional
    public void approveRisk(RiskApproveRequest request) {
        RiskAssessment assessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", request.getApplyId())
        );
        if (assessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        if ("PASS".equals(request.getAuditResult())) {
            assessment.setStatus("FINAL_PASS");
            assessment.setCreditLimit(request.getCreditLimit());
        } else {
            assessment.setStatus("FINAL_REJECT");
        }
        assessment.setIsFinal(true);
        assessment.setAuditRemark(request.getAuditRemark());
        assessment.setApprovalTime(new Date());
        riskAssessmentMapper.updateById(assessment);

        emailUtil.sendRiskAssessmentNotification(
                assessment.getEmail(),
                assessment.getName(),
                "PASS".equals(request.getAuditResult()) ? "评估通过" : "评估拒绝",
                request.getCreditLimit() != null ? request.getCreditLimit().toString() : "0"
        );
    }

    @Override
    public Map<String, Object> getVintageData() {
        Map<String, Object> result = new HashMap<>();

        List<VintageData> vintageDataList = vintageDataMapper.selectList(null);

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

        long totalApplications = riskAssessmentMapper.selectCount(null);
        long pendingReview = riskAssessmentMapper.selectCount(
                new QueryWrapper<RiskAssessment>().eq("status", "MANUAL_REVIEW")
        );

        long totalDisbursedCount = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURRSED.getCode())
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
            Map<String, Object> record = new HashMap<>();
            record.put("loanId", loan.getLoanId());
            record.put("userId", loan.getUserId());

            User user = userMapper.selectById(loan.getUserId());
            if (user != null) {
                record.put("userName", user.getRealName());
                record.put("phone", user.getPhoneNumber().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                record.put("idCard", user.getIdCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
            } else {
                record.put("userName", "未知用户");
                record.put("phone", "未知");
                record.put("idCard", "未知");
            }

            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );
            if (creditLimit != null) {
                record.put("currentLimit", creditLimit.getTotalLimit());
                record.put("exceedAmount", loan.getAmount().subtract(creditLimit.getTotalLimit()));
            } else {
                record.put("currentLimit", BigDecimal.ZERO);
                record.put("exceedAmount", loan.getAmount());
            }

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
    @Transactional
    public LoanApproveResponse approveLoan(LoanApproveRequest request) {
        Loan loan = loanMapper.selectById(request.getLoanId());
        if (loan == null) {
            throw new RuntimeException("贷款申请不存在");
        }

        User user = userMapper.selectById(loan.getUserId());

        LoanApproveResponse response = new LoanApproveResponse();
        response.setLoanId(loan.getLoanId());
        response.setUserId(loan.getUserId());

        if ("APPROVE".equals(request.getApproveResult())) {
            loan.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            loan.setApproveTime(new Date());
            loan.setDisbursementTime(new Date());
            loan.setAdditionalLimit(request.getAdditionalLimit());

            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );

            if (creditLimit != null) {
                BigDecimal newTotalLimit = creditLimit.getTotalLimit().add(request.getAdditionalLimit());
                creditLimit.setTotalLimit(newTotalLimit);
                creditLimit.setUsedLimit(creditLimit.getUsedLimit().add(loan.getAmount()));
                creditLimit.setRemainingLimit(newTotalLimit.subtract(creditLimit.getUsedLimit()));
                creditLimit.setLastUpdateTime(new Date());
                userCreditLimitMapper.updateById(creditLimit);

                response.setOriginalLimit(creditLimit.getTotalLimit().subtract(request.getAdditionalLimit()));
                response.setAdditionalLimit(request.getAdditionalLimit());
                response.setTotalLimit(newTotalLimit);
                response.setActualDisbursedAmount(loan.getAmount());
            }

            loanMapper.updateById(loan);

            generateRepaymentPlan(loan, loan.getRepaymentMethod());

            response.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            response.setEmailSent(true);

            if (user != null) {
                emailUtil.sendLoanSuccessNotification(
                        user.getEmail(),
                        user.getRealName(),
                        loan.getAmount().toString()
                );
            }
        } else {
            loan.setStatus(LoanStatusEnum.REJECTED.getCode());
            loan.setApproveTime(new Date());
            loan.setRejectReason(request.getApproveRemark());

            loanMapper.updateById(loan);

            response.setStatus(LoanStatusEnum.REJECTED.getCode());
            response.setRejectReason(request.getApproveRemark());
            response.setEmailSent(true);

            if (user != null) {
                emailUtil.sendLoanRejectNotification(
                        user.getEmail(),
                        user.getRealName(),
                        loan.getAmount().toString(),
                        request.getApproveRemark()
                );
            }
        }

        response.setApproveTime(new Date());

        return response;
    }

    @Override
    public void updateMockData(MockDataUpdateRequest request) {
        MockData mockData = mockDataMapper.selectOne(
                new QueryWrapper<MockData>().eq("id_card", request.getIdCard())
        );

        if (mockData == null) {
            mockData = new MockData();
            mockData.setIdCard(request.getIdCard());
            mockData.setIsBlacklist(request.getIsBlacklist());
            mockData.setOverdueCount(request.getOverdueCount());
            mockData.setLoanCount(request.getLoanCount());
            mockData.setRecentQueryCount(request.getRecentQueryCount());
            mockDataMapper.insert(mockData);
        } else {
            if (request.getIsBlacklist() != null) {
                mockData.setIsBlacklist(request.getIsBlacklist());
            }
            if (request.getOverdueCount() != null) {
                mockData.setOverdueCount(request.getOverdueCount());
            }
            if (request.getLoanCount() != null) {
                mockData.setLoanCount(request.getLoanCount());
            }
            if (request.getRecentQueryCount() != null) {
                mockData.setRecentQueryCount(request.getRecentQueryCount());
            }
            mockDataMapper.updateById(mockData);
        }
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
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURRSED.getCode())
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
        RepaymentPlan plan = new RepaymentPlan();
        plan.setLoanId(loan.getLoanId());
        plan.setUserId(loan.getUserId());
        plan.setTotalAmount(loan.getAmount());
        plan.setPaidAmount(BigDecimal.ZERO);
        plan.setRemainingAmount(loan.getAmount());
        plan.setTotalPeriods(loan.getTermMonths());
        plan.setCurrentPeriod(1);
        plan.setStatus("ACTIVE");
        plan.setCreateTime(new Date());
        plan.setUpdateTime(new Date());

        repaymentPlanMapper.insert(plan);

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
