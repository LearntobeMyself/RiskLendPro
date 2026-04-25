package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.entity.Loan;
import org.example.risklendpro.entity.MockData;
import org.example.risklendpro.entity.RiskAssessment;
import org.example.risklendpro.entity.UserCreditLimit;
import org.example.risklendpro.entity.VintageData;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.mapper.LoanMapper;
import org.example.risklendpro.mapper.MockDataMapper;
import org.example.risklendpro.mapper.RiskAssessmentMapper;
import org.example.risklendpro.mapper.UserCreditLimitMapper;
import org.example.risklendpro.mapper.VintageDataMapper;
import org.example.risklendpro.pojo.request.LoanApproveRequest;
import org.example.risklendpro.pojo.request.MockDataUpdateRequest;
import org.example.risklendpro.pojo.request.RiskApproveRequest;
import org.example.risklendpro.pojo.response.LoanApproveResponse;
import org.example.risklendpro.service.AdminService;
import org.example.risklendpro.utils.EmailUtil;
import org.example.risklendpro.utils.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
