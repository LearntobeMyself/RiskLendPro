package org.example.risklendpro.service.impl;

import org.example.risklendpro.entity.Loan;
import org.example.risklendpro.enums.LoanStatusEnum;
import org.example.risklendpro.mapper.LoanMapper;
import org.example.risklendpro.pojo.request.LoanRequest;
import org.example.risklendpro.pojo.response.LoanResponse;
import org.example.risklendpro.service.LoanService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanServiceImpl implements LoanService {

    @Autowired
    private LoanMapper loanMapper;

    @Override
    public LoanResponse requestLoan(Long userId, LoanRequest request) {
        // TODO: 检查用户是否有未处理逾期

        // TODO: 检查用户剩余额度
        BigDecimal remainingLimit = new BigDecimal("20000"); // 模拟剩余额度

        // 保存贷款记录
        Loan loan = new Loan();
        loan.setUserId(userId);
        loan.setAmount(request.getAmount());
        loan.setTermMonths(request.getTermMonths());
        loan.setInterestRate(new BigDecimal("0.05"));
        loan.setRepaymentMethod(request.getRepaymentMethod());
        loan.setApplyTime(new Date());
        loan.setCreateTime(new Date());
        loan.setUpdateTime(new Date());

        if (request.getAmount().compareTo(remainingLimit) <= 0) {
            // 额度内借款，自动审批通过
            loan.setStatus(LoanStatusEnum.DISBURRSED.getCode());
            loan.setDisbursementTime(new Date());
            loan.setAutoApproved(true);
            // TODO: 扣减额度
        } else {
            // 额度外借款，需要审批
            loan.setStatus(LoanStatusEnum.PENDING_APPROVAL.getCode());
            loan.setAutoApproved(false);
        }

        loanMapper.insert(loan);

        // 构建响应
        LoanResponse response = new LoanResponse();
        BeanUtils.copyProperties(loan, response);
        if (loan.getAutoApproved()) {
            response.setRemark("额度充足，自动审批通过");
        } else {
            response.setRemark("借款金额超出剩余额度(" + remainingLimit + ")，请等待管理员审批");
        }

        return response;
    }

    @Override
    public Page<LoanResponse> getUserLoanHistory(Long userId, Integer pageNum, Integer pageSize) {
        Page<Loan> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("create_time");
        
        Page<Loan> loanPage = loanMapper.selectPage(page, queryWrapper);
        
        // 转换为响应对象
        List<LoanResponse> records = loanPage.getRecords().stream()
                .map(loan -> {
                    LoanResponse response = new LoanResponse();
                    BeanUtils.copyProperties(loan, response);
                    return response;
                })
                .collect(Collectors.toList());
        
        Page<LoanResponse> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(records);
        resultPage.setTotal(loanPage.getTotal());
        resultPage.setSize(loanPage.getSize());
        resultPage.setCurrent(loanPage.getCurrent());
        resultPage.setPages(loanPage.getPages());
        
        return resultPage;
    }

    @Override
    public Page<LoanResponse> getUserLoanApplications(Long userId, String status, Integer pageNum, Integer pageSize) {
        Page<Loan> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Loan> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        
        queryWrapper.orderByDesc("create_time");
        
        Page<Loan> loanPage = loanMapper.selectPage(page, queryWrapper);
        
        // 转换为响应对象
        List<LoanResponse> records = loanPage.getRecords().stream()
                .map(loan -> {
                    LoanResponse response = new LoanResponse();
                    BeanUtils.copyProperties(loan, response);
                    return response;
                })
                .collect(Collectors.toList());
        
        Page<LoanResponse> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setRecords(records);
        resultPage.setTotal(loanPage.getTotal());
        resultPage.setSize(loanPage.getSize());
        resultPage.setCurrent(loanPage.getCurrent());
        resultPage.setPages(loanPage.getPages());
        
        return resultPage;
    }
}
