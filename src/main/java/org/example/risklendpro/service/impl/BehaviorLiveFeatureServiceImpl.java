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



import java.time.LocalDate;

import java.time.ZoneId;

import java.time.temporal.ChronoUnit;

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

        Date now = new Date();

        int maxOverdue = 0;

        int overdueCount = 0;

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

                    maxOverdue = Math.max(maxOverdue, daysPastDue(record.getDueDate(), now));

                    planOverdue = true;

                }

            }

            if (!planOverdue && "OVERDUE".equals(plan.getStatus())) {

                planOverdue = true;

                if (plan.getOverdueDays() != null) {

                    maxOverdue = Math.max(maxOverdue, plan.getOverdueDays());

                }

            }

            if (planOverdue) {

                overdueCount++;

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

        for (RepaymentRecord r : records) {

            if (isRepaid(r)) {

                paid++;

                if (r.getRepaymentDate() != null && r.getDueDate() != null

                        && !r.getRepaymentDate().after(r.getDueDate())) {

                    onTime++;

                }

            } else if (r.getDueDate() != null && r.getDueDate().before(now)) {

                paid++;

            }

        }

        live.setOnTimeRate(paid == 0 ? 1.0 : (double) onTime / (double) paid);

        return live;

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


