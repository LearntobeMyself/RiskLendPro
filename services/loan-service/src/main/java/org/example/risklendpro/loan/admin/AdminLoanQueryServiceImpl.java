package org.example.risklendpro.loan.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.AdminProfile;
import org.example.risklendpro.api.dto.RiskAssessmentSummary;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.UserCreditLimit;
import org.example.risklendpro.loan.borrow.LoanStatusEnum;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.UserCreditLimitMapper;
import org.example.risklendpro.loan.client.RiskServiceClient;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.borrow.BatchLoanApproveRequest;
import org.example.risklendpro.loan.borrow.LoanApproveRequest;
import org.example.risklendpro.loan.admin.AdminLoanQueryService;
import org.example.risklendpro.loan.admin.AdminLoanOpsService;
import org.example.risklendpro.common.admin.AdminDateHelper;
import org.example.risklendpro.common.admin.AdminEntityMapper;
import org.example.risklendpro.common.admin.AdminExportHelper;
import org.example.risklendpro.common.admin.AdminPageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminLoanQueryServiceImpl implements AdminLoanQueryService {

    @Autowired
    private LoanMapper loanMapper;
    @Autowired
    private UserCreditLimitMapper userCreditLimitMapper;
    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;
    @Autowired
    private UserServiceClient userServiceClient;
    @Autowired
    private RiskServiceClient riskServiceClient;
    @Autowired
    private AdminLoanOpsService adminLoanOpsService;
    @Autowired
    private AdminExportHelper adminExportHelper;

    @Override
    public Map<String, Object> getApplicationDetail(Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null) {
            throw new RuntimeException("贷款申请不存在");
        }
        UserSummary user = userServiceClient.getUser(loan.getUserId());
        UserCreditLimit creditLimit = userCreditLimitMapper.selectOne(
                new QueryWrapper<UserCreditLimit>().eq("user_id", loan.getUserId()));
        RiskAssessmentSummary assessment = riskServiceClient.getLatestFinalAssessment(loan.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("loanId", loan.getLoanId());
        data.put("userId", loan.getUserId());
        if (user != null) {
            data.put("userName", user.realName());
            data.put("phone", user.phoneNumber());
            data.put("idCard", user.idCard());
        }
        data.put("amount", loan.getAmount());
        data.put("termMonths", loan.getTermMonths());
        data.put("purpose", "消费贷款");
        data.put("interestRate", AdminEntityMapper.toInterestRatePercent(loan.getInterestRate()));
        data.put("repaymentMethod", loan.getRepaymentMethod());
        data.put("status", loan.getStatus());
        data.put("applyTime", AdminDateHelper.formatDateTime(loan.getApplyTime()));
        if (assessment != null) {
            data.put("creditScore", assessment.totalScore());
            data.put("riskReportId", assessment.assessmentId());
        }
        if (creditLimit != null) {
            data.put("currentLimit", creditLimit.getTotalLimit());
            data.put("exceedAmount", loan.getAmount().subtract(creditLimit.getTotalLimit()).max(BigDecimal.ZERO));
        } else {
            data.put("currentLimit", BigDecimal.ZERO);
            data.put("exceedAmount", loan.getAmount());
        }
        data.put("materials", List.of());
        return data;
    }

    @Override
    public Map<String, Object> listApprovalRecords(Integer page, Integer size, Long userId, Long loanId,
                                                   String status, String startDate, String endDate) {
        Page<Loan> pageInfo = new Page<>(page, size);
        QueryWrapper<Loan> qw = new QueryWrapper<>();
        qw.isNotNull("approve_time");
        qw.in("status", LoanStatusEnum.DISBURRSED.getCode(), LoanStatusEnum.REJECTED.getCode(),
                LoanStatusEnum.APPROVED.getCode());
        if (userId != null) {
            qw.eq("user_id", userId);
        }
        if (loanId != null) {
            qw.eq("loan_id", loanId);
        }
        if (status != null && !status.isBlank()) {
            if ("APPROVED".equals(status)) {
                qw.eq("status", LoanStatusEnum.DISBURRSED.getCode());
            } else if ("REJECTED".equals(status)) {
                qw.eq("status", LoanStatusEnum.REJECTED.getCode());
            }
        }
        Date start = AdminDateHelper.parseDateStart(startDate);
        Date end = AdminDateHelper.parseDateEnd(endDate);
        if (start != null) {
            qw.ge("approve_time", start);
        }
        if (end != null) {
            qw.lt("approve_time", end);
        }
        qw.orderByDesc("approve_time");

        Page<Loan> result = loanMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Loan loan : result.getRecords()) {
            UserSummary user = userServiceClient.getUser(loan.getUserId());
            AdminProfile reviewer = loan.getOperatorId() != null ? userServiceClient.getAdmin(loan.getOperatorId()) : null;
            String recordStatus = LoanStatusEnum.REJECTED.getCode().equals(loan.getStatus()) ? "REJECTED" : "APPROVED";
            Map<String, Object> item = new HashMap<>();
            item.put("id", loan.getLoanId() + "-" + AdminDateHelper.formatDateTime(loan.getApproveTime()).replaceAll("[^0-9]", ""));
            item.put("loanId", loan.getLoanId());
            item.put("userId", loan.getUserId());
            item.put("reviewerId", loan.getOperatorId() != null ? loan.getOperatorId() : 1L);
            item.put("reviewerName", reviewer != null ? reviewer.username() : "admin");
            item.put("applicantName", user != null ? user.realName() : "未知");
            item.put("status", recordStatus);
            item.put("comment", loan.getRejectReason() != null ? loan.getRejectReason() : "审批通过");
            item.put("createTime", AdminDateHelper.formatDateTime(loan.getApproveTime()));
            item.put("updateTime", AdminDateHelper.formatDateTime(loan.getApproveTime()));
            list.add(item);
        }
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> listRecords(Integer page, Integer size, String status, String userName,
                                           String startDate, String endDate) {
        Page<Loan> pageInfo = new Page<>(page, size);
        QueryWrapper<Loan> qw = buildRecordQuery(status, userName, startDate, endDate);
        Page<Loan> result = loanMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Loan loan : result.getRecords()) {
            list.add(buildRecordItem(loan));
        }
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getRecordDetail(Long loanId) {
        Loan loan = loanMapper.selectById(loanId);
        if (loan == null) {
            throw new RuntimeException("借款记录不存在");
        }
        Map<String, Object> item = buildRecordItem(loan);
        UserSummary user = userServiceClient.getUser(loan.getUserId());
        if (user != null) {
            item.put("idCard", user.idCard());
        }
        item.put("repaymentMethod", loan.getRepaymentMethod());
        RepaymentPlan plan = repaymentPlanMapper.selectOne(
                new QueryWrapper<RepaymentPlan>().eq("loan_id", loanId).last("LIMIT 1"));
        if (plan != null) {
            item.put("remainingAmount", plan.getRemainingAmount());
            item.put("paidAmount", plan.getPaidAmount());
        } else {
            item.put("remainingAmount", loan.getAmount());
            item.put("paidAmount", BigDecimal.ZERO);
        }
        return item;
    }

    @Override
    public Map<String, Object> batchApprove(BatchLoanApproveRequest request, Long adminId) {
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (Long loanId : request.getLoanIds()) {
            try {
                LoanApproveRequest approveRequest = new LoanApproveRequest();
                approveRequest.setLoanId(loanId);
                approveRequest.setApproveResult(request.getApproveResult());
                approveRequest.setAdditionalLimit(request.getAdditionalLimit() != null
                        ? request.getAdditionalLimit() : BigDecimal.ZERO);
                approveRequest.setApproveRemark(request.getApproveRemark());
                adminLoanOpsService.approveLoan(approveRequest);
                Loan loan = loanMapper.selectById(loanId);
                if (loan != null) {
                    loan.setOperatorId(adminId);
                    loanMapper.updateById(loan);
                }
                successCount++;
            } catch (Exception e) {
                failCount++;
                Map<String, Object> fail = new HashMap<>();
                fail.put("loanId", loanId);
                fail.put("reason", e.getMessage());
                failedItems.add(fail);
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("failCount", failCount);
        data.put("failedItems", failedItems);
        return data;
    }

    @Override
    public Map<String, Object> exportRecords(String status, String userName, String startDate, String endDate) {
        QueryWrapper<Loan> qw = buildRecordQuery(status, userName, startDate, endDate);
        List<Loan> loans = loanMapper.selectList(qw);
        List<String> headers = List.of("loanId", "userId", "userName", "loanAmount", "status", "loanTime");
        List<List<String>> rows = new ArrayList<>();
        for (Loan loan : loans) {
            Map<String, Object> item = buildRecordItem(loan);
            rows.add(List.of(
                    String.valueOf(item.get("loanId")),
                    String.valueOf(item.get("userId")),
                    String.valueOf(item.get("userName")),
                    String.valueOf(item.get("loanAmount")),
                    String.valueOf(item.get("status")),
                    String.valueOf(item.get("loanTime"))
            ));
        }
        String path = adminExportHelper.writeCsv("loan-records", headers, rows);
        return adminExportHelper.downloadMeta(path);
    }

    private QueryWrapper<Loan> buildRecordQuery(String status, String userName, String startDate, String endDate) {
        QueryWrapper<Loan> qw = new QueryWrapper<>();
        qw.in("status", LoanStatusEnum.DISBURRSED.getCode(), LoanStatusEnum.REPAID.getCode(),
                LoanStatusEnum.OVERDUE.getCode());
        if (status != null && !status.isBlank()) {
            String dbStatus = mapFrontendStatusToDb(status);
            if (dbStatus != null) {
                qw.eq("status", dbStatus);
            }
        }
        Date start = AdminDateHelper.parseDateStart(startDate);
        Date end = AdminDateHelper.parseDateEnd(endDate);
        if (start != null) {
            qw.ge("disbursement_time", start);
        }
        if (end != null) {
            qw.lt("disbursement_time", end);
        }
        if (userName != null && !userName.isBlank()) {
            List<Long> userIds = userServiceClient.searchUserIdsByName(userName);
            if (userIds.isEmpty()) {
                qw.eq("user_id", -1);
            } else {
                qw.in("user_id", userIds);
            }
        }
        qw.orderByDesc("disbursement_time");
        return qw;
    }

    private Map<String, Object> buildRecordItem(Loan loan) {
        UserSummary user = userServiceClient.getUser(loan.getUserId());
        RepaymentPlan plan = repaymentPlanMapper.selectOne(
                new QueryWrapper<RepaymentPlan>().eq("loan_id", loan.getLoanId()).last("LIMIT 1"));
        Map<String, Object> item = new HashMap<>();
        item.put("loanId", loan.getLoanId());
        item.put("userId", loan.getUserId());
        item.put("userName", user != null ? user.realName() : "未知");
        item.put("phone", user != null ? user.phoneNumber() : "");
        item.put("loanAmount", loan.getAmount());
        item.put("loanTerm", loan.getTermMonths());
        item.put("interestRate", AdminEntityMapper.toInterestRatePercent(loan.getInterestRate()));
        item.put("loanTime", AdminDateHelper.formatDateTime(
                loan.getDisbursementTime() != null ? loan.getDisbursementTime() : loan.getApplyTime()));
        item.put("dueTime", calcDueTime(loan));
        item.put("paidAmount", plan != null ? plan.getPaidAmount() : BigDecimal.ZERO);
        item.put("status", mapDbStatusToFrontend(loan.getStatus()));
        item.put("overdueDays", plan != null && plan.getOverdueDays() != null ? plan.getOverdueDays() : 0);
        item.put("avatar", "");
        return item;
    }

    private String calcDueTime(Loan loan) {
        Date base = loan.getDisbursementTime() != null ? loan.getDisbursementTime() : loan.getApplyTime();
        if (base == null || loan.getTermMonths() == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.MONTH, loan.getTermMonths());
        return AdminDateHelper.formatDateTime(cal.getTime());
    }

    private String mapDbStatusToFrontend(String dbStatus) {
        if (LoanStatusEnum.DISBURRSED.getCode().equals(dbStatus)) {
            return "REPAYING";
        }
        if (LoanStatusEnum.REPAID.getCode().equals(dbStatus)) {
            return "SETTLED";
        }
        if (LoanStatusEnum.OVERDUE.getCode().equals(dbStatus)) {
            return "OVERDUE";
        }
        return dbStatus;
    }

    private String mapFrontendStatusToDb(String frontendStatus) {
        return switch (frontendStatus) {
            case "REPAYING" -> LoanStatusEnum.DISBURRSED.getCode();
            case "SETTLED" -> LoanStatusEnum.REPAID.getCode();
            case "OVERDUE" -> LoanStatusEnum.OVERDUE.getCode();
            default -> null;
        };
    }
}
