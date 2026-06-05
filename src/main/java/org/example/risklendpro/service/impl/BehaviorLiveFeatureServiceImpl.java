package org.example.risklendpro.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.example.risklendpro.entity.Loan;
import org.example.risklendpro.entity.RepaymentPlan;
import org.example.risklendpro.entity.RepaymentRecord;
import org.example.risklendpro.mapper.LoanMapper;
import org.example.risklendpro.mapper.RepaymentPlanMapper;
import org.example.risklendpro.mapper.RepaymentRecordMapper;
import org.example.risklendpro.service.BehaviorLiveFeatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BehaviorLiveFeatureServiceImpl implements BehaviorLiveFeatureService {

    @Autowired
    private RepaymentPlanMapper repaymentPlanMapper;

    @Autowired
    private RepaymentRecordMapper repaymentRecordMapper;

    @Autowired
    private LoanMapper loanMapper;

    @Override
    public LiveFeatures aggregate(Long userId) {
        LiveFeatures live = new LiveFeatures();
        if (userId == null) {
            live.setOnTimeRate(1.0);
            return live;
        }

        List<RepaymentPlan> plans = repaymentPlanMapper.selectList(
                new QueryWrapper<RepaymentPlan>().eq("user_id", userId));
        int maxOverdue = 0;
        int overdueCount = 0;
        for (RepaymentPlan plan : plans) {
            if ("OVERDUE".equals(plan.getStatus())) {
                overdueCount++;
            }
            if (plan.getOverdueDays() != null && plan.getOverdueDays() > maxOverdue) {
                maxOverdue = plan.getOverdueDays();
            }
        }
        live.setMaxOverdueDays(maxOverdue);
        live.setOverduePeriodCount(overdueCount);

        List<Loan> loans = loanMapper.selectList(new QueryWrapper<Loan>().eq("user_id", userId));
        List<Long> loanIds = loans.stream().map(Loan::getLoanId).collect(Collectors.toList());
        List<RepaymentRecord> records;
        if (loanIds.isEmpty()) {
            records = Collections.emptyList();
        } else {
            records = repaymentRecordMapper.selectList(
                    new QueryWrapper<RepaymentRecord>().in("loan_id", loanIds));
        }
        if (records.isEmpty()) {
            live.setOnTimeRate(1.0);
            return live;
        }
        int paid = 0;
        int onTime = 0;
        Date now = new Date();
        for (RepaymentRecord r : records) {
            if ("PAID".equals(r.getStatus()) || "SETTLED".equals(r.getStatus())) {
                paid++;
                if (r.getRepaymentDate() != null && r.getDueDate() != null
                        && !r.getRepaymentDate().after(r.getDueDate())) {
                    onTime++;
                }
            } else if ("PENDING".equals(r.getStatus()) && r.getDueDate() != null && r.getDueDate().before(now)) {
                paid++;
            }
        }
        live.setOnTimeRate(paid == 0 ? 1.0 : (double) onTime / (double) paid);
        return live;
    }
}
