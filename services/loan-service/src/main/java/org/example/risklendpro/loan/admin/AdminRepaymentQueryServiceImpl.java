package org.example.risklendpro.loan.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.api.dto.UserSummary;
import org.example.risklendpro.loan.entity.Loan;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.LoanMapper;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.client.UserServiceClient;
import org.example.risklendpro.loan.repay.RepaymentReminderRequest;
import org.example.risklendpro.loan.repay.RepaymentReportRequest;
import org.example.risklendpro.loan.admin.AdminRepaymentQueryService;
import org.example.risklendpro.common.admin.AdminDateHelper;
import org.example.risklendpro.common.admin.AdminEntityMapper;
import org.example.risklendpro.common.admin.AdminExportHelper;
import org.example.risklendpro.common.admin.AdminPageHelper;
import org.example.risklendpro.common.mail.EmailUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminRepaymentQueryServiceImpl implements AdminRepaymentQueryService {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;
    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;
    @Autowired
    private LoanMapper loanMapper;
    @Autowired
    private UserServiceClient userServiceClient;
    @Autowired
    private EmailUtil emailUtil;
    @Autowired
    private AdminExportHelper adminExportHelper;

    @Override
    public Map<String, Object> getSummary() {
        LocalDate now = LocalDate.now();
        Date monthStart = Date.from(now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date monthEnd = Date.from(now.plusMonths(1).withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<RepaymentRecord> monthRecords = repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>().ge("due_date", monthStart).lt("due_date", monthEnd));
        BigDecimal receivable = monthRecords.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal received = monthRecords.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()))
                .map(r -> r.getActualAmount() != null ? r.getActualAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pending = receivable.subtract(received).max(BigDecimal.ZERO);

        List<RepaymentPlan> overduePlans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE"));
        BigDecimal overdueAmount = overduePlans.stream()
                .map(p -> p.getRemainingAmount() != null ? p.getRemainingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> data = new HashMap<>();
        data.put("receivableThisMonth", receivable);
        data.put("receivedThisMonth", received);
        data.put("pendingAmount", pending);
        data.put("overdueAmount", overdueAmount);
        return data;
    }

    @Override
    public Map<String, Object> listPlans(Integer page, Integer size, Long loanId, String userName, String status) {
        Page<RepaymentPlan> pageInfo = new Page<>(page, size);
        QueryWrapper<RepaymentPlan> qw = new QueryWrapper<>();
        if (loanId != null) {
            qw.eq("loan_id", loanId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        if (userName != null && !userName.isBlank()) {
            List<Long> userIds = userServiceClient.searchUserIdsByName(userName);
            if (userIds.isEmpty()) {
                qw.eq("user_id", -1);
            } else {
                qw.in("user_id", userIds);
            }
        }
        qw.orderByDesc("create_time");
        Page<RepaymentPlan> result = repaymentPlanMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = result.getRecords().stream().map(this::toPlanMap).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getPlanDetail(Long planId) {
        RepaymentPlan plan = repaymentPlanMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("还款计划不存在");
        }
        Map<String, Object> data = toPlanMap(plan);
        List<RepaymentRecord> records = repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>().eq("plan_id", planId).orderByAsc("period"));
        data.put("repaymentRecords", records.stream().map(r -> toRecordMap(r, plan)).toList());
        data.put("overdueRecords", records.stream().filter(r -> r.getStatus() != null && r.getStatus().contains("OVERDUE")).map(r -> toRecordMap(r, plan)).toList());
        data.put("completedRecords", records.stream().filter(r -> "COMPLETED".equals(r.getStatus())).map(r -> toRecordMap(r, plan)).toList());
        data.put("activeRecords", records.stream().filter(r -> "PENDING".equals(r.getStatus()) || "ACTIVE".equals(r.getStatus())).map(r -> toRecordMap(r, plan)).toList());
        return data;
    }

    @Override
    public Map<String, Object> getOverdueStats() {
        long totalCount = repaymentPlanMapper.selectCount(null);
        long overdueCount = repaymentPlanMapper.selectCount(new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE"));
        return Map.of("totalCount", totalCount, "overdueCount", overdueCount);
    }

    @Override
    public Map<String, Object> listPlanRecords(Long planId, Integer page, Integer size, String status) {
        Page<RepaymentRecord> pageInfo = new Page<>(page, size);
        QueryWrapper<RepaymentRecord> qw = new QueryWrapper<RepaymentRecord>().eq("plan_id", planId);
        if (status != null && !status.isBlank()) {
            qw.eq("status", normalizeRecordStatus(status));
        }
        qw.orderByAsc("period");
        Page<RepaymentRecord> result = repaymentRecordMapper.selectPage(pageInfo, qw);
        RepaymentPlan plan = repaymentPlanMapper.selectById(planId);
        List<Map<String, Object>> list = result.getRecords().stream().map(r -> toRecordMap(r, plan)).toList();
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> getRecordsStats(Long planId) {
        QueryWrapper<RepaymentRecord> qw = new QueryWrapper<>();
        if (planId != null) {
            qw.eq("plan_id", planId);
        }
        long totalCount = repaymentRecordMapper.selectCount(qw);
        QueryWrapper<RepaymentRecord> completedQw = new QueryWrapper<>();
        if (planId != null) {
            completedQw.eq("plan_id", planId);
        }
        completedQw.eq("status", "COMPLETED");
        long completedCount = repaymentRecordMapper.selectCount(completedQw);
        return Map.of("totalCount", totalCount, "completedCount", completedCount);
    }

    @Override
    public Map<String, Object> listActualRecords(Integer page, Integer size, Long loanId, String userName,
                                                 String startDate, String endDate) {
        Page<RepaymentRecord> pageInfo = new Page<>(page, size);
        QueryWrapper<RepaymentRecord> qw = new QueryWrapper<>();
        qw.eq("status", "COMPLETED");
        if (loanId != null) {
            qw.eq("loan_id", loanId);
        }
        Date start = AdminDateHelper.parseDateStart(startDate);
        Date end = AdminDateHelper.parseDateEnd(endDate);
        if (start != null) {
            qw.ge("repayment_date", start);
        }
        if (end != null) {
            qw.lt("repayment_date", end);
        }
        if (userName != null && !userName.isBlank()) {
            List<Long> userIds = userServiceClient.searchUserIdsByName(userName);
            if (userIds.isEmpty()) {
                return AdminPageHelper.toListPage(List.of(), 0);
            }
            List<Long> loanIds = loanMapper.selectList(new QueryWrapper<Loan>()
                    .in("user_id", userIds))
                    .stream().map(Loan::getLoanId).toList();
            if (loanIds.isEmpty()) {
                return AdminPageHelper.toListPage(List.of(), 0);
            }
            qw.in("loan_id", loanIds);
        }
        qw.orderByDesc("repayment_date");
        Page<RepaymentRecord> result = repaymentRecordMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RepaymentRecord record : result.getRecords()) {
            Loan loan = loanMapper.selectById(record.getLoanId());
            UserSummary user = loan != null ? userServiceClient.getUser(loan.getUserId()) : null;
            Map<String, Object> item = new HashMap<>();
            item.put("id", record.getRecordId());
            item.put("loanId", record.getLoanId());
            item.put("userName", user != null ? user.realName() : "未知");
            item.put("repayDate", AdminDateHelper.formatDate(record.getRepaymentDate()));
            item.put("repayAmount", record.getActualAmount());
            item.put("repayType", "银行卡");
            item.put("status", "SUCCESS");
            item.put("operator", "系统自动");
            list.add(item);
        }
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> listOverdueRecords(Integer page, Integer size) {
        Page<RepaymentPlan> pageInfo = new Page<>(page, size);
        QueryWrapper<RepaymentPlan> qw = new QueryWrapper<RepaymentPlan>().eq("status", "OVERDUE").orderByDesc("update_time");
        Page<RepaymentPlan> result = repaymentPlanMapper.selectPage(pageInfo, qw);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RepaymentPlan plan : result.getRecords()) {
            UserSummary user = userServiceClient.getUser(plan.getUserId());
            Map<String, Object> item = new HashMap<>();
            item.put("id", plan.getPlanId());
            item.put("loanId", plan.getLoanId());
            item.put("userName", user != null ? user.realName() : "未知");
            item.put("overdueAmount", plan.getRemainingAmount());
            item.put("overdueDays", plan.getOverdueDays());
            item.put("overdueDate", AdminDateHelper.formatDate(plan.getUpdateTime()));
            item.put("contactTimes", 0);
            item.put("lastContact", null);
            item.put("status", "COLLECTING");
            list.add(item);
        }
        return AdminPageHelper.toListPage(list, result.getTotal());
    }

    @Override
    public Map<String, Object> sendReminder(RepaymentReminderRequest request) {
        UserSummary user = userServiceClient.getUser(request.getUserId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String message = request.getMessage() != null ? request.getMessage() : "您有一笔还款即将到期，请及时处理";
        if (user.email() != null) {
            emailUtil.sendSimpleEmail(user.email(), "【RiskLendPro】还款提醒", message);
        }
        return Map.of(
                "reminderId", "REM" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "sendTime", AdminDateHelper.formatDateTime(new Date())
        );
    }

    @Override
    public Map<String, Object> generateReport(RepaymentReportRequest request) {
        QueryWrapper<RepaymentRecord> qw = new QueryWrapper<>();
        Date start = AdminDateHelper.parseDateStart(request.getStartDate());
        Date end = AdminDateHelper.parseDateEnd(request.getEndDate());
        if (start != null) {
            qw.ge("repayment_date", start);
        }
        if (end != null) {
            qw.lt("repayment_date", end);
        }
        List<RepaymentRecord> records = repaymentRecordMapper.selectList(qw);
        List<String> headers = List.of("recordId", "loanId", "period", "amount", "status", "repaymentDate");
        List<List<String>> rows = records.stream().map(r -> List.of(
                String.valueOf(r.getRecordId()),
                String.valueOf(r.getLoanId()),
                String.valueOf(r.getPeriod()),
                String.valueOf(r.getAmount()),
                String.valueOf(r.getStatus()),
                AdminDateHelper.formatDateTime(r.getRepaymentDate())
        )).toList();
        String path = adminExportHelper.writeCsv("repayment-report", headers, rows);
        return adminExportHelper.downloadMeta(path);
    }

    private Map<String, Object> toPlanMap(RepaymentPlan plan) {
        UserSummary user = userServiceClient.getUser(plan.getUserId());
        Loan loan = loanMapper.selectById(plan.getLoanId());
        List<RepaymentRecord> planRecords = repaymentRecordMapper.selectList(
                new QueryWrapper<RepaymentRecord>().eq("plan_id", plan.getPlanId()));
        long paidPeriods = planRecords.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        double progress = plan.getTotalPeriods() != null && plan.getTotalPeriods() > 0
                ? (double) paidPeriods / plan.getTotalPeriods() * 100 : 0;
        Map<String, Object> map = new HashMap<>();
        map.put("planId", plan.getPlanId());
        map.put("loanId", plan.getLoanId());
        map.put("userId", plan.getUserId());
        map.put("userName", user != null ? user.realName() : "未知");
        map.put("totalAmount", plan.getTotalAmount());
        map.put("principalAmount", AdminEntityMapper.sumPrincipal(planRecords));
        map.put("interestAmount", AdminEntityMapper.sumInterest(planRecords));
        map.put("repaymentMethod", loan != null ? loan.getRepaymentMethod() : "");
        map.put("totalPeriods", plan.getTotalPeriods());
        map.put("currentPeriod", plan.getCurrentPeriod());
        map.put("status", plan.getStatus());
        map.put("statusDesc", statusDesc(plan.getStatus()));
        map.put("startDate", AdminDateHelper.formatDate(plan.getCreateTime()));
        map.put("endDate", AdminDateHelper.formatDate(plan.getUpdateTime()));
        map.put("remainingAmount", plan.getRemainingAmount());
        map.put("paidPeriods", paidPeriods);
        map.put("paidAmount", plan.getPaidAmount());
        map.put("progressPercentage", BigDecimal.valueOf(progress).setScale(1, RoundingMode.HALF_UP));
        map.put("createTime", AdminDateHelper.formatDateTime(plan.getCreateTime()));
        map.put("updateTime", AdminDateHelper.formatDateTime(plan.getUpdateTime()));
        return map;
    }

    private Map<String, Object> toRecordMap(RepaymentRecord record, RepaymentPlan plan) {
        Map<String, Object> map = new HashMap<>();
        map.put("recordId", record.getRecordId());
        map.put("planId", record.getPlanId());
        map.put("period", record.getPeriod());
        map.put("amount", record.getAmount());
        map.put("principal", record.getPrincipal());
        map.put("interest", record.getInterest());
        map.put("actualAmount", record.getActualAmount());
        map.put("dueDate", AdminDateHelper.formatDate(record.getDueDate()));
        map.put("repaymentDate", AdminDateHelper.formatDateTime(record.getRepaymentDate()));
        map.put("status", record.getStatus());
        map.put("overdueDays", plan != null && plan.getOverdueDays() != null ? plan.getOverdueDays() : 0);
        map.put("createTime", AdminDateHelper.formatDateTime(record.getCreateTime()));
        return map;
    }

    private String normalizeRecordStatus(String status) {
        return "PAID".equals(status) ? "COMPLETED" : status;
    }

    private String statusDesc(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "ACTIVE" -> "还款中";
            case "COMPLETED" -> "已结清";
            case "OVERDUE" -> "逾期";
            case "PENDING" -> "待还款";
            default -> status;
        };
    }
}
