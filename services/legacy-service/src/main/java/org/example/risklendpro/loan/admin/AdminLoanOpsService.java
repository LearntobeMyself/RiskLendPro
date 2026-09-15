package org.example.risklendpro.loan.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.risklendpro.loan.borrow.LoanApproveRequest;
import org.example.risklendpro.loan.borrow.LoanApproveResponse;

import java.util.Map;

public interface AdminLoanOpsService {

    Map<String, Object> getVintageData();

    Map<String, Object> getRollRateData();

    Map<String, Object> getDashboardStats();

    Page<Map<String, Object>> getLoanPendingList(Integer page, Integer size);

    LoanApproveResponse approveLoan(LoanApproveRequest request);
}
