package org.example.risklendpro.loan.admin;

import org.example.risklendpro.loan.repay.RepaymentReminderRequest;
import org.example.risklendpro.loan.repay.RepaymentReportRequest;

import java.util.Map;

public interface AdminRepaymentQueryService {

    Map<String, Object> getSummary();

    Map<String, Object> listPlans(Integer page, Integer size, Long loanId, String userName, String status);

    Map<String, Object> getPlanDetail(Long planId);

    Map<String, Object> getOverdueStats();

    Map<String, Object> listPlanRecords(Long planId, Integer page, Integer size, String status);

    Map<String, Object> getRecordsStats(Long planId);

    Map<String, Object> listActualRecords(Integer page, Integer size, Long loanId, String userName,
                                          String startDate, String endDate);

    Map<String, Object> listOverdueRecords(Integer page, Integer size);

    Map<String, Object> sendReminder(RepaymentReminderRequest request);

    Map<String, Object> generateReport(RepaymentReportRequest request);
}
