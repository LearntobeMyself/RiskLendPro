package org.example.risklendpro.loan.repay;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.loan.entity.RepaymentPlan;
import org.example.risklendpro.loan.entity.RepaymentRecord;
import org.example.risklendpro.loan.mapper.RepaymentPlanMapper;
import org.example.risklendpro.loan.mapper.RepaymentRecordMapper;
import org.example.risklendpro.loan.repay.RepaymentExecuteRequest;
import org.example.risklendpro.loan.repay.RepaymentResponse;
import org.example.risklendpro.loan.repay.RepaymentService;
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
public class RepaymentServiceImpl implements RepaymentService {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Override
    public List<Object> getRepaymentPlans(Long userId) {
        QueryWrapper<RepaymentPlan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");

        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(queryWrapper);

        // 转换为响应对象
        List<Object> result = new ArrayList<>();
        for (RepaymentPlan plan : plans) {
            Map<String, Object> planMap = new HashMap<>();
            planMap.put("planId", plan.getPlanId());
            planMap.put("loanId", plan.getLoanId());
            planMap.put("totalAmount", plan.getTotalAmount());
            planMap.put("paidAmount", plan.getPaidAmount());
            planMap.put("remainingAmount", plan.getRemainingAmount());
            planMap.put("totalPeriods", plan.getTotalPeriods());
            planMap.put("currentPeriod", plan.getCurrentPeriod());
            planMap.put("status", plan.getStatus());
            planMap.put("createTime", plan.getCreateTime());
            result.add(planMap);
        }

        return result;
    }

    @Override
    @Transactional
    public RepaymentResponse executeRepayment(Long userId, RepaymentExecuteRequest request) {
        // 1. 查询还款计划（FOR UPDATE 行锁，防止并发重复还款）
        RepaymentPlan plan = repaymentPlanMapper.selectOne(
                new QueryWrapper<RepaymentPlan>()
                        .eq("plan_id", request.getPlanId())
                        .last("FOR UPDATE"));
        if (plan == null) {
            throw new RuntimeException("还款计划不存在");
        }

        // 1.1 越权校验：仅本人可操作自己的还款计划
        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该还款计划");
        }

        // 2. 查询该期还款记录
        QueryWrapper<RepaymentRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("plan_id", request.getPlanId());
        recordQuery.eq("period", request.getPeriod());
        RepaymentRecord record = repaymentRecordMapper.selectOne(recordQuery);

        if (record == null) {
            throw new RuntimeException("还款记录不存在");
        }

        // 2.0 幂等保护：该期已结清则禁止重复操作
        if ("COMPLETED".equals(record.getStatus())) {
            throw new RuntimeException("该期已结清，请勿重复还款");
        }

        // 2.1 金额校验：仅允许足额还款，禁止部分/超额/零额
        if (request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAmount().compareTo(record.getAmount()) != 0) {
            throw new RuntimeException("还款金额必须等于当期应还金额，请一次性足额还款");
        }

        // 3. 执行还款
        record.setActualAmount(request.getAmount());
        record.setRepaymentDate(new Date());
        record.setStatus("COMPLETED");
        repaymentRecordMapper.updateById(record);

        // 4. 更新还款计划
        plan.setPaidAmount(plan.getPaidAmount().add(request.getAmount()));
        plan.setRemainingAmount(plan.getRemainingAmount().subtract(request.getAmount()));
        plan.setCurrentPeriod(plan.getCurrentPeriod() + 1);

        if (plan.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0
                && plan.getCurrentPeriod().compareTo(plan.getTotalPeriods()) >= 0) {
            plan.setStatus("COMPLETED");
        }

        repaymentPlanMapper.updateById(plan);

        // 5. 构建响应
        RepaymentResponse response = new RepaymentResponse();
        response.setRecordId(record.getRecordId());
        response.setPlanId(record.getPlanId());
        response.setPeriod(record.getPeriod());
        response.setActualAmount(record.getActualAmount());
        response.setRepaymentDate(record.getRepaymentDate());
        response.setStatus(record.getStatus());

        return response;
    }

    @Override
    public List<Object> getRepaymentRecords(Long userId, Long planId, String status) {
        // 越权校验：仅本人可查看自己的还款记录
        RepaymentPlan plan = repaymentPlanMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("还款计划不存在");
        }
        if (!plan.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看该还款记录");
        }

        QueryWrapper<RepaymentRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("plan_id", planId);

        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }

        queryWrapper.orderByAsc("period");

        List<RepaymentRecord> records = repaymentRecordMapper.selectList(queryWrapper);

        // 转换为响应对象
        List<Object> result = new ArrayList<>();
        for (RepaymentRecord record : records) {
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("recordId", record.getRecordId());
            recordMap.put("planId", record.getPlanId());
            recordMap.put("period", record.getPeriod());
            recordMap.put("principal", record.getPrincipal());
            recordMap.put("interest", record.getInterest());
            recordMap.put("amount", record.getAmount());
            recordMap.put("actualAmount", record.getActualAmount());
            recordMap.put("dueDate", record.getDueDate());
            recordMap.put("repaymentDate", record.getRepaymentDate());
            recordMap.put("status", record.getStatus());
            result.add(recordMap);
        }

        return result;
    }

    @Override
    public Map<String, Long> getRepaymentStatistics() {
        // 总数
        long totalCount = repaymentPlanMapper.selectCount(null);

        // 已完成数
        QueryWrapper<RepaymentPlan> completedQuery = new QueryWrapper<>();
        completedQuery.eq("status", "COMPLETED");
        long completedCount = repaymentPlanMapper.selectCount(completedQuery);

        Map<String, Long> statistics = new HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("completedCount", completedCount);

        return statistics;
    }

    @Override
    public Map<String, Long> getOverdueStatistics() {
        // 活跃状态的还款计划总数（排除已完成的）
        QueryWrapper<RepaymentPlan> activeQuery = new QueryWrapper<>();
        activeQuery.ne("status", "COMPLETED");
        long totalCount = repaymentPlanMapper.selectCount(activeQuery);

        // 逾期数
        QueryWrapper<RepaymentPlan> overdueQuery = new QueryWrapper<>();
        overdueQuery.eq("status", "OVERDUE");
        long overdueCount = repaymentPlanMapper.selectCount(overdueQuery);

        Map<String, Long> statistics = new HashMap<>();
        statistics.put("totalCount", totalCount);
        statistics.put("overdueCount", overdueCount);

        return statistics;
    }
}
