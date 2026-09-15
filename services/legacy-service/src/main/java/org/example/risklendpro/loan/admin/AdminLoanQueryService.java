package org.example.risklendpro.loan.admin;

import org.example.risklendpro.loan.borrow.BatchLoanApproveRequest;

import java.util.Map;

public interface AdminLoanQueryService {

    Map<String, Object> getApplicationDetail(Long loanId);

    Map<String, Object> listApprovalRecords(Integer page, Integer size, Long userId, Long loanId,
                                            String status, String startDate, String endDate);

    Map<String, Object> listRecords(Integer page, Integer size, String status, String userName,
                                    String startDate, String endDate);

    Map<String, Object> getRecordDetail(Long loanId);

    Map<String, Object> batchApprove(BatchLoanApproveRequest request, Long adminId);

    Map<String, Object> exportRecords(String status, String userName, String startDate, String endDate);
}
