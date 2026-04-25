package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.Loan;
import org.example.risklendpro.entity.MockData;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.mapper.LoanMapper;
import org.example.risklendpro.mapper.MockDataMapper;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.MockDataUpdateRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import org.example.risklendpro.service.AdminService;
import org.example.risklendpro.utils.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private EmailUtil emailUtil;

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
        RiskAssessment assessment = riskAssessmentMapper.selectOne(
                new QueryWrapper<RiskAssessment>().eq("apply_id", applyId)
        );
        if (assessment == null) {
            throw new RuntimeException("评估申请不存在");
        }

        Map<String, Object> report = new HashMap<>();

        Map<String, Object> userDetails = new HashMap<>();
        userDetails.put("name", assessment.getName().substring(0, 1) + "*");
        userDetails.put("idCard", assessment.getIdCard().replaceAll("(\\d{3})\\d{9}(\\d{4})", "$1*********$2"));
        userDetails.put("education", assessment.getEducation());
        userDetails.put("marriage", assessment.getMarriage());
        userDetails.put("jobType", assessment.getJobType());
        userDetails.put("monthlyIncome", assessment.getMonthlyIncome());
        userDetails.put("hasHouse", assessment.getHasHouse());
        userDetails.put("hasCar", assessment.getHasCar());

        report.put("userDetails", userDetails);
        report.put("totalScore", assessment.getTotalScore());
        report.put("systemDecision", assessment.getSysDecision());

        Map<String, Object> scoringBreakdown = new HashMap<>();
        Map<String, Object> profileScore = new HashMap<>();
        profileScore.put("score", 45);
        List<Map<String, Object>> profileDetails = new ArrayList<>();
        Map<String, Object> profileItem1 = new HashMap<>();
        profileItem1.put("item", "学历评估");
        profileItem1.put("value", assessment.getEducation());
        profileItem1.put("subScore", 15);
        profileItem1.put("comment", "学历符合准入要求");
        profileDetails.add(profileItem1);
        profileScore.put("details", profileDetails);
        scoringBreakdown.put("profileScore", profileScore);

        Map<String, Object> capacityScore = new HashMap<>();
        capacityScore.put("score", 30);
        List<Map<String, Object>> capacityDetails = new ArrayList<>();
        Map<String, Object> capacityItem1 = new HashMap<>();
        capacityItem1.put("item", "收入水平评分");
        capacityItem1.put("value", assessment.getMonthlyIncome());
        capacityItem1.put("subScore", 20);
        capacityItem1.put("comment", "申报收入极高");
        capacityDetails.add(capacityItem1);
        capacityScore.put("details", capacityDetails);
        scoringBreakdown.put("capacityScore", capacityScore);

        report.put("scoringBreakdown", scoringBreakdown);

        List<Map<String, Object>> fusionComparison = new ArrayList<>();
        Map<String, Object> fusionItem1 = new HashMap<>();
        fusionItem1.put("dimension", "收入真实性");
        fusionItem1.put("userFill", assessment.getMonthlyIncome());
        fusionItem1.put("mockCheck", "模拟流水校验：月收入约8000");
        fusionItem1.put("status", "WARNING");
        fusionItem1.put("reason", "申报收入显著高于社保/流水推算值");
        fusionComparison.add(fusionItem1);
        report.put("fusionComparison", fusionComparison);

        return report;
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
        List<String> months = List.of("2023-07", "2023-08", "2023-09", "2023-10");
        result.put("months", months);

        List<Map<String, Object>> vintageData = new ArrayList<>();
        Map<String, Object> data1 = new HashMap<>();
        data1.put("month", "2023-07");
        data1.put("disbursedAmount", 500000);
        data1.put("M1Rate", 0.02);
        data1.put("M2Rate", 0.01);
        data1.put("M3Rate", 0.005);
        vintageData.add(data1);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("month", "2023-08");
        data2.put("disbursedAmount", 600000);
        data2.put("M1Rate", 0.015);
        data2.put("M2Rate", 0.008);
        data2.put("M3Rate", 0.003);
        vintageData.add(data2);

        result.put("vintageData", vintageData);
        return result;
    }

    @Override
    public Map<String, Object> getRollRateData() {
        Map<String, Object> result = new HashMap<>();
        result.put("currentStatus", "C");

        Map<String, Object> nextMonthStatus = new HashMap<>();
        nextMonthStatus.put("C", 0.85);
        nextMonthStatus.put("M1", 0.10);
        nextMonthStatus.put("M2", 0.03);
        nextMonthStatus.put("M3", 0.02);

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

        long totalDisbursed = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.DISBURRSED.getCode())
        );

        long totalOverdue = loanMapper.selectCount(
                new QueryWrapper<Loan>().eq("status", LoanStatusEnum.OVERDUE.getCode())
        );

        stats.put("totalApplications", totalApplications);
        stats.put("pendingReview", pendingReview);
        stats.put("approvedToday", 12);
        stats.put("rejectedToday", 3);
        stats.put("totalDisbursed", totalDisbursed * 10000.00);
        stats.put("totalOverdue", totalOverdue * 5000.00);
        stats.put("overdueRate", totalDisbursed > 0 ? (double) totalOverdue / totalDisbursed : 0);

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
            record.put("userName", "张三");
            record.put("phone", "138****8000");
            record.put("idCard", "510***********1234");
            record.put("amount", loan.getAmount());
            record.put("termMonths", loan.getTermMonths());
            record.put("repaymentMethod", loan.getRepaymentMethod());
            record.put("currentLimit", 15000.00);
            record.put("exceedAmount", loan.getAmount().subtract(new BigDecimal("15000.00")));
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

        LoanApproveResponse response = new LoanApproveResponse();
        response.setLoanId(loan.getLoanId());
        response.setUserId(loan.getUserId());

        if ("APPROVE".equals(request.getApproveResult())) {
            loan.setStatus(LoanStatusEnum.APPROVED.getCode());
            loan.setApproveTime(new Date());

            UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                    new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId())
            );

            if (creditLimit != null) {
                response.setOriginalLimit(creditLimit.getTotalLimit());
                response.setAdditionalLimit(request.getAdditionalLimit());
                response.setTotalLimit(creditLimit.getTotalLimit().add(request.getAdditionalLimit()));
                response.setActualDisbursedAmount(loan.getAmount());
            }

            response.setStatus(LoanStatusEnum.APPROVED.getCode());
            response.setEmailSent(true);
        } else {
            loan.setStatus(LoanStatusEnum.REJECTED.getCode());
            loan.setApproveTime(new Date());
            loan.setRejectReason(request.getApproveRemark());

            response.setStatus(LoanStatusEnum.REJECTED.getCode());
            response.setRejectReason(request.getApproveRemark());
            response.setEmailSent(true);
        }

        loanMapper.updateById(loan);
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
}
