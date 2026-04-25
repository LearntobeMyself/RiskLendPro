package org.example.risklendpro.service;

import org.example.risklendpro.pojo.request.LoanRequest;
import org.example.risklendpro.pojo.response.LoanResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface LoanService {
    /**
     * 发起借款请求
     */
    LoanResponse requestLoan(Long userId, LoanRequest request);

    /**
     * 获取用户借款记录
     */
    Page<LoanResponse> getUserLoanHistory(Long userId, Integer pageNum, Integer pageSize);

    /**
     * 获取用户贷款申请记录
     */
    Page<LoanResponse> getUserLoanApplications(Long userId, String status, Integer pageNum, Integer pageSize);
}
